package com.github.yimeng261.maidspell.client.resource;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

public class LegacyPackRepositorySource implements RepositorySource {
    private static final String LEGACY_PACK_DIR_NAME = "legacy_pack";
    private static final String PACK_NAME = "touhou_little_maid_spell_legacy_textures";

    private final Pack legacyPack;

    public LegacyPackRepositorySource() {
        PathPackResources.PathResourcesSupplier supplier = getLegacyPack();
        Component title = Component.translatable("pack.touhou_little_maid_spell.legacy_textures.title");
        Component desc = Component.translatable("pack.touhou_little_maid_spell.legacy_textures.desc");
        PackLocationInfo info = new PackLocationInfo(PACK_NAME, title, PackSource.BUILT_IN, Optional.empty());
        Pack.Metadata metadata = new Pack.Metadata(desc, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), Collections.emptyList(), false);
        PackSelectionConfig config = new PackSelectionConfig(false, Pack.Position.TOP, false);
        this.legacyPack = new Pack(info, supplier, metadata, config);
    }

    private PathPackResources.PathResourcesSupplier getLegacyPack() {
        IModFile file = ModList.get().getModFileById(MaidSpellMod.MOD_ID).getFile();
        Path legacyPackPath = file.getSecureJar().getRootPath().resolve(LEGACY_PACK_DIR_NAME);
        return new PathPackResources.PathResourcesSupplier(legacyPackPath);
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        consumer.accept(this.legacyPack);
    }
}
