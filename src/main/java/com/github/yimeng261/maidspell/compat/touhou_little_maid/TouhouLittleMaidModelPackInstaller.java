package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 把 jar 内置的车万女仆模型包解压到 {@code gameDir/tlm_custom_pack}。
 *
 * <p>TLM 1.5.x 只从 {@code tlm_custom_pack} 读模型包 —— 不读 mod jar、不读原版资源包栈、
 * 也不读数据包（那是 1.12.2 时代的机制）。它自己的默认女仆包走的就是这条自解压路线
 * （{@code CommonDefaultPack} + {@code GetJarResources.copyFolder}），本类是同一手法。
 *
 * <p>解压到磁盘而不是运行时注入，是因为{@link ServerCustomPackLoader 服务端}和客户端
 * 各有一份加载器、读的是同一个目录：躺在目录里，两边就都有，玩家才能在模型选择界面里
 * 真正选到它（服务端那份还负责显示名和野生女仆的随机模型池）。
 *
 * <p>文件清单写死而不是遍历 jar：{@code getResourceAsStream} 在开发环境和 jar 里行为一致，
 * 不用碰 Forge 的 union 文件系统。清单与磁盘的一致性由
 * {@code TouhouLittleMaidModelPackInstallerTest} 兜底。
 */
public final class TouhouLittleMaidModelPackInstaller {
    private static final String TLM_MOD_ID = "touhou_little_maid";
    private static final String CUSTOM_PACK_DIR = "tlm_custom_pack";
    static final String PACK_NAME = "star_witch_winefox-1.0.0";
    static final String RESOURCE_ROOT =
            "/assets/" + MaidSpellMod.MOD_ID + "/" + CUSTOM_PACK_DIR + "/" + PACK_NAME + "/";

    /** 相对包根目录的文件清单，与 {@link #RESOURCE_ROOT} 下的实际内容一一对应。 */
    static final List<String> PACK_FILES = List.of(
            "pack.mcmeta",
            "assets/touhou_little_maid_spell/maid_model.json",
            "assets/touhou_little_maid_spell/models/entity/sea_witch_winefox.json",
            "assets/touhou_little_maid_spell/textures/entity/sea_witch_winefox.png",
            "assets/touhou_little_maid_spell/textures/maid_icon.png",
            "assets/touhou_little_maid_spell/lang/zh_cn.lang",
            "assets/touhou_little_maid_spell/lang/en_us.lang",
            "assets/touhou_little_maid_spell/animation/touhou_little_maid_spell.sea_witch_winefox.main.animation.json",
            "assets/touhou_little_maid_spell/animation/touhou_little_maid_spell.sea_witch_winefox.arm.animation.json",
            "assets/touhou_little_maid_spell/animation/touhou_little_maid_spell.sea_witch_winefox.tlm.animation.json",
            "assets/touhou_little_maid_spell/animation/touhou_little_maid_spell.sea_witch_winefox.iss.animation.json");

    private TouhouLittleMaidModelPackInstaller() {
    }

    public static boolean installIfNeeded() {
        if (!ModList.get().isLoaded(TLM_MOD_ID)) {
            return false;
        }
        if (!Config.autoInstallTlmModelPack) {
            MaidSpellMod.LOGGER.info("Skipped Touhou Little Maid model pack installation (autoInstallTlmModelPack=false)");
            return false;
        }

        Path packRoot = FMLPaths.GAMEDIR.get().resolve(CUSTOM_PACK_DIR).resolve(PACK_NAME);
        try {
            Files.createDirectories(packRoot);
            for (String relativePath : PACK_FILES) {
                copyBundledResource(relativePath, packRoot.resolve(relativePath));
            }
            MaidSpellMod.LOGGER.info("Installed Touhou Little Maid model pack at {}", packRoot);
            return true;
        } catch (IOException e) {
            MaidSpellMod.LOGGER.error("Failed to install Touhou Little Maid model pack", e);
            return false;
        }
    }

    /**
     * 装完之后强制刷一次服务端的包列表。只在 {@link #installIfNeeded()} 返回 true 之后调用。
     *
     * <p>TLM 的服务端读包挂在它自己的 {@code FMLCommonSetupEvent} 上，和我们这边谁先谁后
     * 没有保证；无条件重载一次，两种顺序都对。客户端不用管，它的首次
     * {@code CustomPackLoader.reloadPacks()} 发生在初次资源重载，晚于 setup。
     */
    public static void reloadServerPacks() {
        ServerCustomPackLoader.reloadPacks();
    }

    private static void copyBundledResource(String relativePath, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream stream = TouhouLittleMaidModelPackInstaller.class.getResourceAsStream(RESOURCE_ROOT + relativePath)) {
            if (stream == null) {
                throw new IOException("Missing bundled resource: " + RESOURCE_ROOT + relativePath);
            }
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
