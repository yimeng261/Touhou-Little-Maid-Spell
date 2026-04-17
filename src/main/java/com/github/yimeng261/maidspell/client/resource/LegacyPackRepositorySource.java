package com.github.yimeng261.maidspell.client.resource;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.locating.IModFile;

import java.nio.file.Path;
import java.util.function.Consumer;

public class LegacyPackRepositorySource implements RepositorySource {
    private static final String LEGACY_PACK_DIR_NAME = "legacy_pack";
    private static final String PACK_NAME = "touhou_little_maid_spell_legacy_textures";

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        IModFile modFile = ModList.get().getModFileById(MaidSpellMod.MOD_ID).getFile();
        Path legacyPackPath = modFile.findResource(LEGACY_PACK_DIR_NAME);
        Pack pack = Pack.readMetaAndCreate(
                PACK_NAME,
                Component.translatable("pack.touhou_little_maid_spell.legacy_textures.title"),
                false,
                id -> new PathPackResources(id, legacyPackPath, false),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                PackSource.BUILT_IN
        );
        if (pack != null) {
            consumer.accept(pack);
        }
    }
}
