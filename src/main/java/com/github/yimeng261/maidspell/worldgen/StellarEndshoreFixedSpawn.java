package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.StellarEndshoreStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/** Places one additional, seed-stable Starway Endshore at one of the four configured anchors. */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class StellarEndshoreFixedSpawn {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_stellar_endshore_fixed";
    private static final BlockPos[] ANCHORS = {
            new BlockPos(800, 150, 0), new BlockPos(-800, 200, 0),
            new BlockPos(0, 200, 800), new BlockPos(0, 200, -800)
    };

    private StellarEndshoreFixedSpawn() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        event.getServer().execute(() -> placeIfNeeded(event.getServer()));
    }

    private static void placeIfNeeded(MinecraftServer server) {
        if (!server.getWorldData().worldGenOptions().generateStructures()) return;
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        FixedSpawnData data = end.getDataStorage().computeIfAbsent(
                FixedSpawnData::load, FixedSpawnData::new, DATA_NAME);
        if (data.attempted) return;

        ResourceKey<Structure> structureKey = ResourceKey.create(
                Registries.STRUCTURE, new ResourceLocation(MaidSpellMod.MOD_ID, "stellar_endshore"));
        var registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure registered = registry.get(structureKey);
        if (!(registered instanceof StellarEndshoreStructure structure)) {
            return;
        }

        BlockPos anchor = selectAnchor(end.getSeed());
        ChunkPos candidate = new ChunkPos(anchor);
        try {
            // Avoid duplicating a normally generated structure that happens to overlap this anchor.
            if (end.structureManager().getStructureWithPieceAt(anchor, structure).isValid()) {
                data.attempted = true;
                data.position = anchor.asLong();
                data.setDirty();
                return;
            }

            var generator = end.getChunkSource().getGenerator();
            StellarEndshoreStructure.setFixedAnchor(anchor);
            StructureStart start;
            try {
                start = structure.generate(server.registryAccess(), generator,
                        generator.getBiomeSource(), end.getChunkSource().randomState(),
                        end.getStructureManager(), end.getSeed(), candidate, 0, end,
                        holder -> true);
            } finally {
                StellarEndshoreStructure.clearFixedAnchor();
            }
            if (start == null || !start.isValid()) {
                data.attempted = true;
                data.setDirty();
                return;
            }

            BoundingBox box = start.getBoundingBox();
            ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                    SectionPos.blockToSectionCoord(box.minZ()));
            ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()),
                    SectionPos.blockToSectionCoord(box.maxZ()));
            for (ChunkPos pos : ChunkPos.rangeClosed(min, max).toList()) {
                end.getChunk(pos.x, pos.z);
                start.placeInChunk(end, end.structureManager(), generator, end.getRandom(),
                        new BoundingBox(pos.getMinBlockX(), end.getMinBuildHeight(), pos.getMinBlockZ(),
                                pos.getMaxBlockX(), end.getMaxBuildHeight(), pos.getMaxBlockZ()), pos);
            }

            ChunkAccess anchorChunk = end.getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true);
            end.structureManager().setStartForStructure(SectionPos.bottomOf(anchorChunk), structure, start, anchorChunk);
            for (ChunkPos pos : ChunkPos.rangeClosed(min, max).toList()) {
                ChunkAccess chunk = end.getChunk(pos.x, pos.z);
                end.structureManager().addReferenceForStructure(SectionPos.bottomOf(chunk), structure,
                        start.getChunkPos().toLong(), chunk);
            }
            data.attempted = true;
            data.position = anchor.asLong();
            data.setDirty();
            MaidSpellMod.LOGGER.info("星途终岸固定刷新：已在末地 {} 生成（覆盖区块 {}~{}）", anchor, min, max);
        } catch (RuntimeException e) {
            MaidSpellMod.LOGGER.error("星途终岸固定刷新失败，保留状态以便下次开服重试", e);
        }
    }

    static BlockPos selectAnchor(long seed) {
        return ANCHORS[new Random(seed ^ 0x5EED0B5EEDL).nextInt(ANCHORS.length)];
    }

    private static final class FixedSpawnData extends SavedData {
        private boolean attempted;
        private long position;

        static FixedSpawnData load(CompoundTag tag) {
            FixedSpawnData data = new FixedSpawnData();
            data.attempted = tag.getBoolean("Attempted");
            data.position = tag.getLong("Position");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("Attempted", attempted);
            tag.putLong("Position", position);
            return tag;
        }
    }
}
