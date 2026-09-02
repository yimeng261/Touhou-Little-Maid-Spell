package com.github.yimeng261.maidspell.validation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code TouhouLittleMaidModelPackInstaller.PACK_FILES} 与磁盘上的模型包目录必须一一对应。
 *
 * <p>那份清单是写死的，不是遍历 jar 得来的（{@code getResourceAsStream} 在开发环境和 jar 里
 * 行为一致，不用碰 Forge 的 union 文件系统）。代价就是加一个文件而忘了加进清单，
 * 玩家装出来的模型包会缺一块，而且是安安静静地缺——这个测试就是补那一刀。
 *
 * <p>两个方向都查：清单里有而磁盘上没有（解压时会报找不到资源），
 * 磁盘上有而清单里没有（悄悄漏装）。
 */
class TouhouLittleMaidModelPackInstallerTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty(
        "maidspell.projectDir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
    private static final Path INSTALLER_SOURCE = PROJECT_ROOT.resolve(
        "src/main/java/com/github/yimeng261/maidspell/compat/touhou_little_maid/"
            + "TouhouLittleMaidModelPackInstaller.java");
    private static final Path PACK_ROOT = PROJECT_ROOT.resolve(
        "src/main/resources/assets/touhou_little_maid_spell/tlm_custom_pack");

    private static final Pattern PACK_NAME = Pattern.compile(
        "static\\s+final\\s+String\\s+PACK_NAME\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern PACK_FILES_BLOCK = Pattern.compile(
        "PACK_FILES\\s*=\\s*List\\.of\\((.*?)\\);", Pattern.DOTALL);
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    @Test
    void packFileListMatchesTheDirectoryOnDisk() throws IOException {
        String source = Files.readString(INSTALLER_SOURCE, StandardCharsets.UTF_8);
        Path packDir = PACK_ROOT.resolve(packName(source));
        assertTrue(Files.isDirectory(packDir),
            () -> "模型包目录不存在：" + PROJECT_ROOT.relativize(packDir));

        TreeSet<String> declared = new TreeSet<>(declaredFiles(source));
        assertTrue(declared.size() >= 5,
            () -> "只从 PACK_FILES 解析出 " + declared.size() + " 项，正则大概过时了");

        TreeSet<String> onDisk = new TreeSet<>();
        try (Stream<Path> files = Files.walk(packDir)) {
            files.filter(Files::isRegularFile)
                .map(path -> packDir.relativize(path).toString().replace('\\', '/'))
                .forEach(onDisk::add);
        }

        List<String> failures = new ArrayList<>();
        for (String declaredFile : declared) {
            if (!onDisk.contains(declaredFile)) {
                failures.add("PACK_FILES 里有、磁盘上没有：" + declaredFile);
            }
        }
        for (String actualFile : onDisk) {
            if (!declared.contains(actualFile)) {
                failures.add("磁盘上有、PACK_FILES 里没有（玩家装不到）：" + actualFile);
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    /**
     * {@code CUSTOM_PACK_DIR} 得和资源目录的实际名字一致 ——
     * 它是 {@code RESOURCE_ROOT} 的一段，对不上就是整包解压失败。
     */
    @Test
    void customPackDirMatchesTheResourceFolder() throws IOException {
        String source = Files.readString(INSTALLER_SOURCE, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(
            "CUSTOM_PACK_DIR\\s*=\\s*\"([^\"]+)\"").matcher(source);
        assertTrue(matcher.find(), "在安装器源码里找不到 CUSTOM_PACK_DIR");
        assertEquals(PACK_ROOT.getFileName().toString(), matcher.group(1),
            "CUSTOM_PACK_DIR 与 src/main/resources 下的目录名对不上");
    }

    private static String packName(String source) {
        Matcher matcher = PACK_NAME.matcher(source);
        assertTrue(matcher.find(), "在安装器源码里找不到 PACK_NAME");
        return matcher.group(1);
    }

    private static List<String> declaredFiles(String source) {
        Matcher block = PACK_FILES_BLOCK.matcher(source);
        assertTrue(block.find(), "在安装器源码里找不到 PACK_FILES 的 List.of(...)");
        List<String> files = new ArrayList<>();
        Matcher quoted = QUOTED.matcher(block.group(1));
        while (quoted.find()) {
            files.add(quoted.group(1));
        }
        return files;
    }
}
