package com.github.yimeng261.maidspell.bootstrap;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class MaidSpellNestedModLocator extends AbstractJarFileModLocator {
    private static final String MOD_ID = "touhou_little_maid_spell";
    private static final String NESTED_MOD = "META-INF/maidspell/maidspell-mod.jar";
    private static Path extractedMod;

    @Override
    public Stream<Path> scanCandidates() {
        if (isExplodedDevelopmentModPresent()) {
            return Stream.empty();
        }
        return Stream.of(extractNestedMod());
    }

    @Override
    public String name() {
        return "maidspell_nested_mod";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }

    @Override
    public boolean isValid(IModFile modFile) {
        return true;
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {
        super.scanFile(modFile, pathConsumer);
    }

    private static synchronized Path extractNestedMod() {
        if (extractedMod != null && Files.isRegularFile(extractedMod)) {
            return extractedMod;
        }

        try {
            Path bootstrapJar = resolveBootstrapJar();
            Path extractionDirectory = Path.of(System.getProperty("java.io.tmpdir"), "maidspell-bootstrap");
            Files.createDirectories(extractionDirectory);
            Path destination = extractionDirectory.resolve("maidspell-mod-" + Files.size(bootstrapJar) + ".jar");

            try (JarFile jarFile = new JarFile(bootstrapJar.toFile())) {
                JarEntry entry = jarFile.getJarEntry(NESTED_MOD);
                if (entry == null) {
                    throw new IllegalStateException("Missing nested MaidSpell mod: " + NESTED_MOD);
                }
                try (InputStream input = jarFile.getInputStream(entry)) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            destination.toFile().deleteOnExit();
            extractedMod = destination;
            return destination;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to extract the nested MaidSpell mod", exception);
        }
    }

    private static Path resolveBootstrapJar() throws IOException {
        Path modsDirectory = FMLPaths.MODSDIR.get();
        try (Stream<Path> candidates = Files.list(modsDirectory)) {
            return candidates
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(MaidSpellNestedModLocator::containsNestedMod)
                    .findFirst()
                    .orElseThrow(() -> new IOException("Unable to find the MaidSpell bootstrap jar in " + modsDirectory));
        }
    }

    private static boolean containsNestedMod(Path candidate) {
        try (JarFile jarFile = new JarFile(candidate.toFile())) {
            return jarFile.getJarEntry(NESTED_MOD) != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isExplodedDevelopmentModPresent() {
        String modClasses = System.getenv("MOD_CLASSES");
        return modClasses != null && modClasses.contains(MOD_ID + "%%");
    }
}
