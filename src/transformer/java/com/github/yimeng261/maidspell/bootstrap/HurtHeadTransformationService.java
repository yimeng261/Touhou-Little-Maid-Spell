package com.github.yimeng261.maidspell.bootstrap;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * ModLauncher service packaged as a separate helper jar.
 *
 * <p>It instruments every discovered LivingEntity subclass that declares
 * hurt(DamageSource, float), so true-damage HurtHead handlers can run before
 * subclass cooldown or damage-cap logic.
 */
public final class HurtHeadTransformationService implements ITransformationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("MaidSpell HurtHeadService");
    private static final String SERVICE_NAME = "maidspell_hurt_head";
    private static final String LIVING_ENTITY = "net/minecraft/world/entity/LivingEntity";
    private static final String DAMAGE_SOURCE = "net/minecraft/world/damagesource/DamageSource";
    private static final String HURT_DESC = "(L" + DAMAGE_SOURCE + ";F)Z";
    private static final String HOOK_OWNER = "com/github/yimeng261/maidspell/coremod/HurtHeadCoremodHooks";
    private static final String HOOK_ENTER = "maidspell$enterHurtHook";
    private static final String HOOK_EXIT = "maidspell$exitHurtHook";
    private static final Set<String> HURT_METHOD_NAMES = Set.of("hurt", "m_6469_");
    private static final String OBJECT = "java/lang/Object";

    private final Map<String, String> superClasses = new HashMap<>();
    private final Set<String> hurtCandidates = new HashSet<>();
    private final Set<String> livingClasses = new HashSet<>(Set.of(LIVING_ENTITY));
    private final Set<String> nonLivingClasses = new HashSet<>(Set.of(OBJECT));
    private final Set<String> missingClasses = new HashSet<>();
    private final Set<String> scannedLocations = new HashSet<>();
    private final Set<String> targetClasses = new LinkedHashSet<>();
    private boolean discoveredTargets;

    @Override
    public String name() {
        return SERVICE_NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {
        scanClasspathFallback();
        scanMinecraftClassLibrary();
        scanModsDirectory();
    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        scanClasspathFallback();
        scanMinecraftClassLibrary();
        scanModsDirectory();
        return List.of();
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        scanModuleLayers(layerManager);
        discoverTargets();
        return List.of();
    }

    @Override
    public void onLoad(IEnvironment environment, Set<String> otherServices) throws IncompatibleEnvironmentException {
        environment.findModuleLayerManager().ifPresent(this::scanModuleLayers);
    }

    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalResourcesLocator() {
        Set<String> resources = Set.of("maidspell.mixins.json", "maidspell.refmap.json");
        return new AbstractMap.SimpleImmutableEntry<>(resources, () -> this::locateOwnResource);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<ITransformer> transformers() {
        if (!this.discoveredTargets) {
            scanClasspathFallback();
            scanMinecraftClassLibrary();
            scanModsDirectory();
            discoverTargets();
        }
        if (this.targetClasses.isEmpty()) {
            LOGGER.warn("[MaidSpell] dynamic hurt-head scan found no LivingEntity hurt overrides");
            return List.of();
        }
        LOGGER.info("[MaidSpell] dynamic hurt-head scan targeting {} hurt overrides", this.targetClasses.size());
        return Collections.singletonList(new HurtMethodTransformer(this.targetClasses));
    }

    private void scanModuleLayers(IModuleLayerManager layerManager) {
        scanLayer(layerManager, IModuleLayerManager.Layer.GAME);
        scanLayer(layerManager, IModuleLayerManager.Layer.PLUGIN);
        scanLayer(layerManager, IModuleLayerManager.Layer.SERVICE);
    }

    private Optional<URL> locateOwnResource(String resourceName) {
        try {
            URL source = HurtHeadTransformationService.class.getProtectionDomain().getCodeSource().getLocation();
            if (source.getPath().endsWith(".jar")) {
                return Optional.of(new URL("jar:" + source.toExternalForm() + "!/" + resourceName));
            }
            return Optional.of(Path.of(source.toURI()).resolve(resourceName).toUri().toURL());
        } catch (MalformedURLException | URISyntaxException exception) {
            LOGGER.error("[MaidSpell] failed to locate early resource {}", resourceName, exception);
            return Optional.empty();
        }
    }

    private void scanLayer(IModuleLayerManager layerManager, IModuleLayerManager.Layer layerName) {
        Optional<ModuleLayer> layer = layerManager.getLayer(layerName);
        if (layer.isEmpty()) {
            return;
        }
        for (ResolvedModule module : layer.get().configuration().modules()) {
            module.reference().location().ifPresent(uri -> scanUri(uri, "module " + module.name()));
        }
    }

    private void scanClasspathFallback() {
        scanPathProperty("java.class.path", false);
        scanPathProperty("legacyClassPath", false);
    }

    private void scanMinecraftClassLibrary() {
        String libraryDirectory = System.getProperty("libraryDirectory", "");
        if (libraryDirectory.isBlank()) {
            return;
        }
        String minecraftVersion = FMLLoader.versionInfo().mcVersion();
        Path clientLibraries = Path.of(libraryDirectory, "net", "minecraft", "client");
        if (!Files.isDirectory(clientLibraries)) {
            return;
        }
        try (var paths = Files.walk(clientLibraries, 2)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getParent().getFileName().toString().startsWith(minecraftVersion + "-"))
                    .filter(path -> path.getFileName().toString().endsWith("-srg.jar"))
                    .filter(HurtHeadTransformationService::containsLivingEntityClass)
                    .forEach(path -> scanPath(path, "Minecraft class library", false));
        } catch (IOException exception) {
            LOGGER.debug("[MaidSpell] failed to scan Minecraft class library {}: {}",
                    clientLibraries, exception.getMessage());
        }
    }

    private void scanPathProperty(String propertyName, boolean fromModsDirectory) {
        String classPath = System.getProperty(propertyName, "");
        if (classPath.isBlank()) {
            return;
        }
        for (String entry : classPath.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                scanPath(Path.of(entry), propertyName, fromModsDirectory);
            }
        }
    }

    private void scanModsDirectory() {
        Path modsDir = FMLPaths.MODSDIR.get();
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        try (var stream = Files.list(modsDir)) {
            stream.forEach(path -> scanPath(path, "mods directory", true));
        } catch (IOException exception) {
            LOGGER.debug("[MaidSpell] failed to scan mods directory {}: {}", modsDir, exception.getMessage());
        }
    }

    private void scanUri(URI uri, String source) {
        if (!"file".equalsIgnoreCase(uri.getScheme())) {
            return;
        }
        scanPath(Path.of(uri), source, true);
    }

    private void scanPath(Path path, String source, boolean fromModsDirectory) {
        Path normalized;
        try {
            normalized = path.toRealPath();
        } catch (IOException ignored) {
            normalized = path.toAbsolutePath().normalize();
        }
        String key = normalized.toString();
        if (!this.scannedLocations.add(key) || !Files.exists(normalized)) {
            return;
        }
        try {
            if (Files.isDirectory(normalized)) {
                if (fromModsDirectory || shouldScanClasspathDirectory(normalized)) {
                    scanDirectory(normalized);
                }
            } else if (key.toLowerCase().endsWith(".jar")
                    && (fromModsDirectory || shouldScanClasspathJar(normalized))) {
                scanJar(normalized);
            }
        } catch (IOException exception) {
            LOGGER.debug("[MaidSpell] failed to scan {} from {}: {}", normalized, source, exception.getMessage());
        }
    }

    private static boolean shouldScanClasspathDirectory(Path path) {
        return Files.exists(path.resolve("META-INF").resolve("mods.toml"))
                || path.toString().contains("classes");
    }

    private static boolean shouldScanClasspathJar(Path path) {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            return jarFile.getEntry("META-INF/mods.toml") != null
                    || jarFile.getEntry("META-INF/neoforge.mods.toml") != null
                    || jarFile.getEntry("mcmod.info") != null
                    || jarFile.getEntry("fabric.mod.json") != null
                    || jarFile.getEntry(LIVING_ENTITY + ".class") != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean containsLivingEntityClass(Path path) {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            return jarFile.getEntry(LIVING_ENTITY + ".class") != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private void scanDirectory(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".class")) {
                    try (InputStream input = Files.newInputStream(file)) {
                        readClass(input, true);
                    } catch (IOException exception) {
                        LOGGER.debug("[MaidSpell] failed to scan class {}: {}", file, exception.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void scanJar(Path jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()
                        || !name.endsWith(".class")
                        || name.equals("module-info.class")
                        || name.startsWith("META-INF/versions/")) {
                    continue;
                }
                try (InputStream input = jarFile.getInputStream(entry)) {
                    readClass(input, true);
                } catch (IOException exception) {
                    LOGGER.debug("[MaidSpell] failed to scan class {} in {}: {}", name, jarPath, exception.getMessage());
                }
            }
        }
    }

    private void readClass(InputStream input, boolean collectHurtCandidate) throws IOException {
        ClassReader reader = new ClassReader(input);
        ClassHeader header = new ClassHeader();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                header.name = name;
                header.superName = superName;
                header.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (HURT_DESC.equals(descriptor) && HURT_METHOD_NAMES.contains(name)) {
                    header.declaresHurt = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (header.name == null) {
            return;
        }
        this.superClasses.putIfAbsent(header.name, header.superName);
        if (collectHurtCandidate && !header.isInterface && header.declaresHurt) {
            this.hurtCandidates.add(header.name);
        }
    }

    private void discoverTargets() {
        this.discoveredTargets = true;
        this.targetClasses.clear();
        this.hurtCandidates.stream()
                .filter(this::inheritsLivingEntity)
                .map(name -> name.replace('/', '.'))
                .sorted(Comparator.naturalOrder())
                .forEach(this.targetClasses::add);
    }

    private boolean inheritsLivingEntity(String className) {
        if (this.livingClasses.contains(className)) {
            return true;
        }
        if (this.nonLivingClasses.contains(className)) {
            return false;
        }

        List<String> unresolvedPath = new ArrayList<>();
        Set<String> pathClasses = new HashSet<>();
        String current = className;
        boolean living = false;
        while (current != null) {
            if (this.livingClasses.contains(current)) {
                living = true;
                break;
            }
            if (this.nonLivingClasses.contains(current) || !pathClasses.add(current)) {
                break;
            }
            unresolvedPath.add(current);
            current = resolveSuperClass(current);
        }

        Set<String> resultCache = living ? this.livingClasses : this.nonLivingClasses;
        resultCache.addAll(unresolvedPath);
        return living;
    }

    private String resolveSuperClass(String className) {
        if (this.superClasses.containsKey(className)) {
            return this.superClasses.get(className);
        }
        if (!this.missingClasses.add(className)) {
            return null;
        }

        String resourceName = className + ".class";
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = openClassResource(contextLoader, resourceName)) {
            if (input != null) {
                readClass(input, false);
                this.missingClasses.remove(className);
                return this.superClasses.get(className);
            }
        } catch (IOException exception) {
            LOGGER.debug("[MaidSpell] failed to resolve parent for {}: {}", className, exception.getMessage());
        }
        return null;
    }

    private static InputStream openClassResource(ClassLoader contextLoader, String resourceName) {
        InputStream input = contextLoader == null ? null : contextLoader.getResourceAsStream(resourceName);
        if (input == null) {
            input = HurtHeadTransformationService.class.getClassLoader().getResourceAsStream(resourceName);
        }
        return input;
    }

    private static boolean hasHook(MethodNode methodNode, String methodName) {
        for (var instruction : methodNode.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.INVOKESTATIC
                    && instruction instanceof MethodInsnNode methodInsn
                    && HOOK_OWNER.equals(methodInsn.owner)
                    && methodName.equals(methodInsn.name)) {
                return true;
            }
        }
        return false;
    }

    private static void injectHead(MethodNode methodNode) {
        LabelNode continueLabel = new LabelNode();
        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injection.add(new VarInsnNode(Opcodes.FLOAD, 2));
        injection.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                HOOK_ENTER,
                "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Ljava/lang/Boolean;",
                false));
        injection.add(new InsnNode(Opcodes.DUP));
        injection.add(new JumpInsnNode(Opcodes.IFNULL, continueLabel));
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                HOOK_EXIT,
                "(Lnet/minecraft/world/entity/LivingEntity;)V",
                false));
        injection.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Boolean",
                "booleanValue",
                "()Z",
                false));
        injection.add(new InsnNode(Opcodes.IRETURN));
        injection.add(continueLabel);
        injection.add(new InsnNode(Opcodes.POP));
        methodNode.instructions.insert(injection);
    }

    private static void injectExit(MethodNode methodNode) {
        for (var instruction : methodNode.instructions.toArray()) {
            int opcode = instruction.getOpcode();
            if (opcode == Opcodes.IRETURN || opcode == Opcodes.ATHROW) {
                InsnList exitInjection = new InsnList();
                exitInjection.add(new VarInsnNode(Opcodes.ALOAD, 0));
                exitInjection.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOK_OWNER,
                        HOOK_EXIT,
                        "(Lnet/minecraft/world/entity/LivingEntity;)V",
                        false));
                methodNode.instructions.insertBefore(instruction, exitInjection);
            }
        }
    }

    private static final class HurtMethodTransformer implements ITransformer<MethodNode> {
        private final Set<Target> targets;

        private HurtMethodTransformer(Set<String> classNames) {
            Set<Target> targetSet = new LinkedHashSet<>();
            for (String className : classNames) {
                for (String methodName : HURT_METHOD_NAMES) {
                    targetSet.add(Target.targetMethod(className, methodName, HURT_DESC));
                }
            }
            this.targets = Set.copyOf(targetSet);
        }

        @Override
        public MethodNode transform(MethodNode methodNode, ITransformerVotingContext context) {
            if (hasHook(methodNode, HOOK_ENTER)) {
                return methodNode;
            }
            injectHead(methodNode);
            injectExit(methodNode);
            methodNode.maxStack += 4;
            return methodNode;
        }

        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Override
        public Set<Target> targets() {
            return this.targets;
        }

        @Override
        public String[] labels() {
            return new String[]{SERVICE_NAME};
        }
    }

    private static final class ClassHeader {
        private String name;
        private String superName;
        private boolean isInterface;
        private boolean declaresHurt;
    }
}
