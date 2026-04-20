package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class TouhouLittleMaidModelPackInstaller {
    private static final String TLM_MOD_ID = "touhou_little_maid";
    private static final String CUSTOM_PACK_DIR = "tlm_custom_pack";
    private static final String PACK_NAME = "maidspell_geckolib_models-1.0.0";
    private static final String RESOURCE_ROOT = "/tlm_custom_pack/" + PACK_NAME + "/";
    private static boolean installedThisRun = false;
    private static final List<String> PACK_FILES = List.of(
            "assets/geckolib/maid_model.json",
            "assets/geckolib/models/entity/winefox_saint.json",
            "assets/geckolib/animation/winefox_saint.main.animation.json",
            "assets/geckolib/animation/winefox_saint.condition.animation.json",
            "assets/geckolib/animation/winefox_saint.iss.animation.json",
            "assets/geckolib/textures/entity/winefox_saint.png",
            "assets/geckolib/textures/entity/winefox_saint_1.png",
            "assets/geckolib/lang/zh_cn.json",
            "assets/geckolib/lang/en_us.json"
    );

    private TouhouLittleMaidModelPackInstaller() {
    }

    public static boolean installIfNeeded() {
        if (!ModList.get().isLoaded(TLM_MOD_ID)) {
            return false;
        }

        Path packRoot = FMLPaths.GAMEDIR.get().resolve(CUSTOM_PACK_DIR).resolve(PACK_NAME);
        try {
            Files.createDirectories(packRoot);
            for (String relativePath : PACK_FILES) {
                copyBundledResource(relativePath, packRoot.resolve(relativePath));
            }
            installedThisRun = true;
            MaidSpellMod.LOGGER.info("Installed Touhou Little Maid compatibility model pack at {}", packRoot);
            return true;
        } catch (IOException e) {
            MaidSpellMod.LOGGER.error("Failed to install Touhou Little Maid compatibility model pack", e);
            return false;
        }
    }

    public static boolean wasInstalledThisRun() {
        return installedThisRun;
    }

    public static void reloadServerPacksIfNeeded() {
        if (!installedThisRun || !ModList.get().isLoaded(TLM_MOD_ID)) {
            return;
        }
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
