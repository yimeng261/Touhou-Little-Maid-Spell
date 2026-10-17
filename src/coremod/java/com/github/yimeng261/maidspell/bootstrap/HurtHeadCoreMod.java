package com.github.yimeng261.maidspell.bootstrap;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * 女仆法术的 hurt 头部插桩 Java coremod。
 *
 * <p>NeoForge 在构建游戏模块层之前向 PLUGIN 层的 {@link ICoreMod} 索取转换器，此时模组清单已确定。
 * 这里扫描清单里每个模组文件（含 Minecraft 本体与嵌套模组）的类头，找出所有直接声明了非抽象
 * {@code hurt(DamageSource, float)} 的 LivingEntity 子类，为它们的 hurt 方法头部插入
 * {@code HurtHeadCoremodHooks} 的进入调用，并在每个返回前和异常离开方法时插入退出调用。
 *
 * <p>本类只以字符串引用 Minecraft 与模组主体的类，不会提前加载游戏类；钩子类在模组主体里，
 * 插桩后的方法第一次执行时才由游戏层解析。
 */
public final class HurtHeadCoreMod implements ICoreMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("MaidSpell/HurtHeadCoreMod");
    private static final String LABEL = "maidspell_hurt_head";
    private static final String OBJECT = "java/lang/Object";
    private static final String LIVING_ENTITY = "net/minecraft/world/entity/LivingEntity";
    private static final String HURT_NAME = "hurt";
    private static final String HURT_DESC = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static final String HOOK_OWNER = "com/github/yimeng261/maidspell/coremod/HurtHeadCoremodHooks";
    private static final String HOOK_ENTER = "maidspell$enterHurtHook";
    private static final String HOOK_ENTER_DESC =
            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Ljava/lang/Boolean;";
    private static final String HOOK_EXIT = "maidspell$exitHurtHook";
    private static final String HOOK_EXIT_DESC = "(Lnet/minecraft/world/entity/LivingEntity;)V";

    @Override
    public Iterable<? extends ITransformer<?>> getTransformers() {
        long start = System.nanoTime();
        HierarchyScan scan = new HierarchyScan();
        int scannedFiles = 0;
        for (ModFileInfo modFileInfo : LoadingModList.get().getModFiles()) {
            scan.scanRoot(modFileInfo.getFile().getSecureJar().getRootPath());
            scannedFiles++;
        }
        Set<String> targets = scan.findTargets();
        LOGGER.info("[MaidSpell/Coremod] 扫描 {} 个模组文件、{} 个类，耗时 {} ms，共 {} 个 LivingEntity 子类声明了 hurt，将插桩",
                scannedFiles, scan.superClasses.size(), (System.nanoTime() - start) / 1_000_000L, targets.size());
        if (!targets.contains(LIVING_ENTITY.replace('/', '.'))) {
            LOGGER.warn("[MaidSpell/Coremod] 扫描结果里没有 LivingEntity 本体，hurt 头部钩子可能不完整");
        }
        if (LOGGER.isDebugEnabled()) {
            targets.forEach(target -> LOGGER.debug("[MaidSpell/Coremod] hurt 插桩目标: {}", target));
        }
        if (targets.isEmpty()) {
            return List.of();
        }
        return List.of(new HurtMethodTransformer(targets));
    }

    /**
     * 只读类头和方法签名，记录父类关系与声明了 hurt 的类。
     */
    private static final class HierarchyScan {
        private final Map<String, String> superClasses = new HashMap<>();
        private final Set<String> hurtDeclarers = new HashSet<>();
        private final Map<String, Boolean> livingCache = new HashMap<>();

        private void scanRoot(Path root) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> {
                            String name = path.toString();
                            return name.endsWith(".class")
                                    && !name.endsWith("module-info.class")
                                    && !name.contains("META-INF/versions/");
                        })
                        .forEach(this::readClass);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("[MaidSpell/Coremod] 扫描 {} 失败: {}", root, exception.toString());
            }
        }

        private void readClass(Path classFile) {
            try (InputStream input = Files.newInputStream(classFile)) {
                ClassReader reader = new ClassReader(input);
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    private String className;

                    @Override
                    public void visit(int version, int access, String name, String signature,
                                      String superName, String[] interfaces) {
                        if ((access & Opcodes.ACC_INTERFACE) == 0) {
                            this.className = name;
                            superClasses.putIfAbsent(name, superName);
                        }
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (this.className != null
                                && HURT_NAME.equals(name)
                                && HURT_DESC.equals(descriptor)
                                && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                            hurtDeclarers.add(this.className);
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (IOException | RuntimeException exception) {
                LOGGER.debug("[MaidSpell/Coremod] 读取 {} 失败: {}", classFile, exception.toString());
            }
        }

        private Set<String> findTargets() {
            Set<String> targets = new TreeSet<>();
            for (String className : this.hurtDeclarers) {
                if (isLiving(className)) {
                    targets.add(className.replace('/', '.'));
                }
            }
            return targets;
        }

        /**
         * 沿父类链判断是否为 LivingEntity 或其子类；父类不在扫描范围内（JDK 类或缺失的可选依赖）时按非生物处理。
         */
        private boolean isLiving(String className) {
            List<String> path = new ArrayList<>();
            String current = className;
            Boolean result = null;
            while (result == null) {
                if (current == null || OBJECT.equals(current)) {
                    result = Boolean.FALSE;
                } else if (LIVING_ENTITY.equals(current)) {
                    result = Boolean.TRUE;
                } else {
                    Boolean cached = this.livingCache.get(current);
                    if (cached != null) {
                        result = cached;
                    } else if (path.contains(current)) {
                        result = Boolean.FALSE;
                    } else {
                        path.add(current);
                        current = this.superClasses.get(current);
                    }
                }
            }
            for (String visited : path) {
                this.livingCache.put(visited, result);
            }
            return result;
        }
    }

    private static final class HurtMethodTransformer implements ITransformer<MethodNode> {
        private final Set<Target<MethodNode>> targets = new HashSet<>();

        private HurtMethodTransformer(Set<String> classNames) {
            for (String className : classNames) {
                this.targets.add(Target.targetMethod(className, HURT_NAME, HURT_DESC));
            }
        }

        @Override
        public MethodNode transform(MethodNode methodNode, ITransformerVotingContext context) {
            if ((methodNode.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                    || hasEnterHook(methodNode)) {
                return methodNode;
            }
            try {
                injectExit(methodNode);
                injectExceptionExit(methodNode);
                injectHead(methodNode);
                methodNode.maxStack += 4;
                LOGGER.debug("[MaidSpell/Coremod] 已插桩 hurt: {}（{}）", context.getClassName(), context.getReason());
            } catch (RuntimeException exception) {
                LOGGER.error("[MaidSpell/Coremod] 插桩 {} 的 hurt 失败", context.getClassName(), exception);
            }
            return methodNode;
        }

        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Override
        public Set<Target<MethodNode>> targets() {
            return this.targets;
        }

        @Override
        public TargetType<MethodNode> getTargetType() {
            return TargetType.METHOD;
        }

        @Override
        public String[] labels() {
            return new String[]{LABEL};
        }
    }

    private static boolean hasEnterHook(MethodNode methodNode) {
        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction instanceof MethodInsnNode methodInsn
                    && methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                    && HOOK_OWNER.equals(methodInsn.owner)
                    && HOOK_ENTER.equals(methodInsn.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 方法头部：进入钩子返回非 null 时先退出钩子再直接返回该值，返回 null 时继续执行原方法体。
     */
    private static void injectHead(MethodNode methodNode) {
        LabelNode continueLabel = new LabelNode();
        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injection.add(new VarInsnNode(Opcodes.FLOAD, 2));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_ENTER, HOOK_ENTER_DESC, false));
        injection.add(new InsnNode(Opcodes.DUP));
        injection.add(new JumpInsnNode(Opcodes.IFNULL, continueLabel));
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_EXIT, HOOK_EXIT_DESC, false));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
        injection.add(new InsnNode(Opcodes.IRETURN));
        injection.add(continueLabel);
        injection.add(new InsnNode(Opcodes.POP));
        methodNode.instructions.insert(injection);
    }

    /**
     * 原方法体的每个 IRETURN 前调用退出钩子，维持重入计数平衡。
     */
    private static void injectExit(MethodNode methodNode) {
        for (AbstractInsnNode instruction : methodNode.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                InsnList exitInjection = new InsnList();
                exitInjection.add(new VarInsnNode(Opcodes.ALOAD, 0));
                exitInjection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_EXIT, HOOK_EXIT_DESC, false));
                methodNode.instructions.insertBefore(instruction, exitInjection);
            }
        }
    }

    /**
     * 用覆盖整个原方法体的 catch-all 包住它：任何异常离开方法前调用退出钩子再原样抛出。
     * 处理器排在异常表末尾，方法内部自己的 catch 优先匹配，被内部吞掉的异常不会触发退出。
     */
    private static void injectExceptionExit(MethodNode methodNode) {
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        methodNode.instructions.insert(start);
        methodNode.instructions.add(end);
        InsnList handlerInjection = new InsnList();
        handlerInjection.add(handler);
        handlerInjection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        handlerInjection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_EXIT, HOOK_EXIT_DESC, false));
        handlerInjection.add(new InsnNode(Opcodes.ATHROW));
        methodNode.instructions.add(handlerInjection);
        methodNode.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
    }
}
