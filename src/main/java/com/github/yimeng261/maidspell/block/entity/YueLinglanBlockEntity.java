package com.github.yimeng261.maidspell.block.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.block.custom.YueLinglanBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public class YueLinglanBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 40;
    private static final int PARTICLE_INTERVAL = 8;
    private static final int STRUCTURE_SEARCH_INTERVAL = 20 * 10;
    private static final ResourceLocation ELVEN_REALM_ID = new ResourceLocation(MaidSpellMod.MOD_ID, "elven_realm");
    private static final TagKey<Structure> ELVEN_REALM_TAG =
        TagKey.create(Registries.STRUCTURE, ELVEN_REALM_ID);
    private static final int STRUCTURE_SEARCH_RADIUS = 2560;
    private static final double MIN_PARTICLE_DISTANCE_TO_STRUCTURE = 16.0 * 2.0;
    private static final double MIN_PARTICLE_DISTANCE_TO_STRUCTURE_SQR =
        MIN_PARTICLE_DISTANCE_TO_STRUCTURE * MIN_PARTICLE_DISTANCE_TO_STRUCTURE;
    @Nullable
    private BlockPos cachedStructurePos;
    private long nextStructureSearchGameTime;

    public YueLinglanBlockEntity(BlockPos pos, BlockState state) {
        super(MaidSpellBlockEntities.YUE_LINGLAN.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, YueLinglanBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        long gameTime = serverLevel.getGameTime();
        blockEntity.tickStructureTrail(serverLevel, pos, gameTime);

        int offset = Math.floorMod(pos.asLong(), TICK_INTERVAL);
        if ((gameTime + offset) % TICK_INTERVAL != 0) {
            return;
        }

        YueLinglanBlock.applyAura(serverLevel, pos);
    }

    private void tickStructureTrail(ServerLevel level, BlockPos pos, long gameTime) {
        if (gameTime >= nextStructureSearchGameTime) {
            cachedStructurePos = findNearestTrackedStructure(level, pos);
            nextStructureSearchGameTime = gameTime + STRUCTURE_SEARCH_INTERVAL;
        }

        if (cachedStructurePos == null) {
            return;
        }

        int particleOffset = Math.floorMod(pos.asLong(), PARTICLE_INTERVAL);
        if ((gameTime + particleOffset) % PARTICLE_INTERVAL != 0) {
            return;
        }

        if (isInsideTrackedStructure(level, pos) || isWithinSuppressionRange(level, pos)) {
            return;
        }

        double dx = cachedStructurePos.getX() + 0.5 - (pos.getX() + 0.5);
        double dz = cachedStructurePos.getZ() + 0.5 - (pos.getZ() + 0.5);
        double horizontalDistanceSqr = dx * dx + dz * dz;
        if (horizontalDistanceSqr <= MIN_PARTICLE_DISTANCE_TO_STRUCTURE_SQR) {
            return;
        }
        double horizontalLength = Math.sqrt(horizontalDistanceSqr);
        if (horizontalLength < 1.0e-4) {
            return;
        }

        double dirX = dx / horizontalLength;
        double dirZ = dz / horizontalLength;
        double perpX = -dirZ;
        double perpZ = dirX;
        double phase = (gameTime % 40L) / 40.0 * (Math.PI * 2.0);

        for (int i = 0; i < 9; i++) {
            double progress = 0.06 + i * 0.11;
            double nextProgress = progress + 0.24;
            double curve = Math.sin(phase + progress * 2.9) * 0.18;
            double nextCurve = Math.sin(phase + nextProgress * 2.9) * 0.18;
            double fan = (level.random.nextDouble() - 0.5) * 0.05;
            double x = pos.getX() + 0.5 + dirX * progress + perpX * (curve + fan);
            double y = pos.getY() + 0.70 + i * 0.018 + Math.sin(phase + i * 0.6) * 0.03;
            double z = pos.getZ() + 0.5 + dirZ * progress + perpZ * (curve + fan);

            double nextX = pos.getX() + 0.5 + dirX * nextProgress + perpX * nextCurve;
            double nextY = pos.getY() + 0.74 + i * 0.02 + Math.sin(phase + i * 0.6 + 0.45) * 0.03;
            double nextZ = pos.getZ() + 0.5 + dirZ * nextProgress + perpZ * nextCurve;

            double vx = (nextX - x) * 0.18;
            double vy = (nextY - y) * 0.18 + 0.004;
            double vz = (nextZ - z) * 0.18;
            level.sendParticles(
                new DustParticleOptions(new Vector3f(0.92f, 1.0f, 0.97f), i > 7 ? 1.28f : 1.08f),
                x, y, z,
                0,
                vx, vy, vz,
                1.0
            );
        }

        double tipX = pos.getX() + 0.5 + dirX * 1.75 + perpX * Math.sin(phase + 1.4) * 0.08;
        double tipY = pos.getY() + 1.05 + Math.sin(phase + 0.8) * 0.04;
        double tipZ = pos.getZ() + 0.5 + dirZ * 1.75 + perpZ * Math.sin(phase + 1.4) * 0.08;
        level.sendParticles(
            new DustParticleOptions(new Vector3f(0.98f, 1.0f, 1.0f), 1.5f),
            tipX, tipY, tipZ,
            0,
            dirX * 0.04, 0.018, dirZ * 0.04,
            1.0
        );
    }

    @Nullable
    private BlockPos findNearestTrackedStructure(ServerLevel level, BlockPos origin) {
        return level.findNearestMapStructure(ELVEN_REALM_TAG, origin, STRUCTURE_SEARCH_RADIUS, false);
    }

    private boolean isInsideTrackedStructure(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, ELVEN_REALM_TAG).isValid();
    }

    private boolean isWithinSuppressionRange(ServerLevel level, BlockPos pos) {
        StructureStart structureStart = getTrackedStructureStart(level);
        if (!structureStart.isValid()) {
            return false;
        }

        BoundingBox box = structureStart.getBoundingBox();
        int padding = (int) MIN_PARTICLE_DISTANCE_TO_STRUCTURE;
        return pos.getX() >= box.minX() - padding
            && pos.getX() <= box.maxX() + padding
            && pos.getZ() >= box.minZ() - padding
            && pos.getZ() <= box.maxZ() + padding;
    }

    private StructureStart getTrackedStructureStart(ServerLevel level) {
        if (cachedStructurePos == null) {
            return StructureStart.INVALID_START;
        }

        @SuppressWarnings("removal")
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOptional(ELVEN_REALM_ID);
        if (structure.isEmpty()) {
            return StructureStart.INVALID_START;
        }

        StructureStart structureStart = level.structureManager().getStructureAt(cachedStructurePos, structure.get());
        if (structureStart.isValid()) {
            return structureStart;
        }

        return level.structureManager().getStructureWithPieceAt(cachedStructurePos, structure.get());
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityTicker<T> createTicker(BlockEntityType<T> type) {
        return type == MaidSpellBlockEntities.YUE_LINGLAN.get()
            ? (level, pos, state, blockEntity) -> serverTick(level, pos, state, (YueLinglanBlockEntity) blockEntity)
            : null;
    }
}
