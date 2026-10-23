package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.TouhouLittleMaidLegacyModelPackCleaner;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.TouhouLittleMaidModelPackInstaller;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Logs;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 车万女仆模型包：不再内置旧的 geckolib 兼容包，启动时删掉旧版装到游戏目录的残留，
 * 星之魔女酒狐模型包按配置解压并被车万女仆读到。
 */
public final class ModelPackScenes {
    private static final Path PACK_DIR = FMLPaths.GAMEDIR.get().resolve("tlm_custom_pack");
    private static final Path LEGACY = PACK_DIR.resolve("maidspell_geckolib_models-1.0.0");
    private static final Path STAR_WITCH = PACK_DIR.resolve("star_witch_winefox-1.0.0");
    private static final String REMOVED_LOG = "Removed obsolete Touhou Little Maid compatibility model pack";

    private ModelPackScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("model_pack.noBundledGeckolibPack", 5, ModelPackScenes::noBundledPack));
        scenes.add(Checks.scene("model_pack.legacyPackRemovedOthersKept", 5, ModelPackScenes::legacyRemoved));
        scenes.add(Checks.scene("model_pack.cleanerWithoutLegacyDir", 5, ModelPackScenes::cleanerWithoutLegacy));
        scenes.add(Checks.scene("model_pack.starWitchPackInstalled", 5, ModelPackScenes::starWitchInstalled));
        scenes.add(Checks.scene("model_pack.autoInstallOffSkipsUnpack", 5, ModelPackScenes::autoInstallOff));
        return scenes;
    }

    private static void noBundledPack(SceneContext ctx) {
        ctx.check(Files.exists(LEGACY)).as(LEGACY + " 存在").isFalse();
        ctx.check(ServerCustomPackLoader.SERVER_MAID_MODELS.containsInfo("geckolib:winefox_saint_black"))
                .as("服务端模型表含已移除的 geckolib:winefox_saint_black").isFalse();
    }

    /** 放一个带子目录的旧包和一个同级的其他包，清理后只删旧包，日志记一行。 */
    private static void legacyRemoved(SceneContext ctx) {
        Path sibling = PACK_DIR.resolve("tlms_test_sibling_pack");
        ctx.cleanup(() -> delete(sibling));
        write(LEGACY.resolve("assets/geckolib/models/entity/old.json"), "{}");
        write(LEGACY.resolve("pack.mcmeta"), "{}");
        write(sibling.resolve("pack.mcmeta"), "{}");
        long logged = removedLogCount();
        TouhouLittleMaidLegacyModelPackCleaner.cleanGameDirectory();
        ctx.check(Files.exists(LEGACY)).as("清理后旧包仍在").isFalse();
        ctx.check(Files.exists(sibling.resolve("pack.mcmeta"))).as("同级的其他包仍在").isTrue();
        ctx.check(Files.exists(STAR_WITCH)).as("星之魔女酒狐模型包仍在").isTrue();
        ctx.check(removedLogCount()).as("日志「" + REMOVED_LOG + "」条数").isEqualTo(logged + 1);
    }

    private static void cleanerWithoutLegacy(SceneContext ctx) {
        delete(LEGACY);
        boolean removed = Reflect.call(null, TouhouLittleMaidLegacyModelPackCleaner.class, "deleteLegacyPack",
                new Class<?>[]{Path.class}, FMLPaths.GAMEDIR.get());
        ctx.check(removed).as("目录不存在时报告已删除").isFalse();
    }

    /** 首次启动后解压出的星之魔女酒狐包文件齐全，车万女仆服务端读到了这个模型和 1.5.3 自带的两个酒狐模型。 */
    private static void starWitchInstalled(SceneContext ctx) {
        List<String> files = Reflect.field(null, TouhouLittleMaidModelPackInstaller.class, "PACK_FILES");
        List<String> missing = files.stream().filter(file -> !Files.isRegularFile(STAR_WITCH.resolve(file))).toList();
        ctx.check(missing).as(STAR_WITCH + " 缺少的文件").isEmpty();
        for (String model : List.of("touhou_little_maid_spell:stellar_witch", "geckolib:winefox_elf", "geckolib:winefox_saint")) {
            ctx.check(ServerCustomPackLoader.SERVER_MAID_MODELS.containsInfo(model)).as("服务端模型表含 " + model).isTrue();
        }
    }

    /** 关掉 autoInstallTlmModelPack 时不解压（先把已解压的包挪开，场景结束挪回）。 */
    private static void autoInstallOff(SceneContext ctx) {
        Path aside = PACK_DIR.resolve("tlms_test_star_witch_aside");
        boolean previous = Config.autoInstallTlmModelPack;
        move(STAR_WITCH, aside);
        ctx.cleanup(() -> {
            Config.autoInstallTlmModelPack = previous;
            delete(STAR_WITCH);
            move(aside, STAR_WITCH);
        });
        Config.autoInstallTlmModelPack = false;
        ctx.check(TouhouLittleMaidModelPackInstaller.installIfNeeded()).as("配置关闭时报告已解压").isFalse();
        ctx.check(Files.exists(STAR_WITCH)).as("配置关闭时解压出了 " + STAR_WITCH.getFileName()).isFalse();
    }

    private static long removedLogCount() {
        List<String> lines = Logs.lines();
        return lines == null ? -1 : lines.stream().filter(line -> line.contains(REMOVED_LOG)).count();
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void move(Path from, Path to) {
        if (!Files.exists(from)) {
            return;
        }
        try {
            Files.move(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
