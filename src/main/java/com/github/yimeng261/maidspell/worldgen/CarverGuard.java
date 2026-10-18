package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;

/**
 * 洞穴雕刻器不挖 {@link #TAG} 里结构的占地：底框水平外扩 {@link #MARGIN} 格、从底框最低处往下 {@link #DEPTH} 格往上的范围。
 * 雕刻器在地形和地形适配之后才挖洞，选址看的噪声地形里没有这些洞，不拦的话洞穴会从地基底下穿过，房子悬在洞口上。
 */
public final class CarverGuard {
    public static final TagKey<Structure> TAG = TagKey.create(Registries.STRUCTURE,
            new ResourceLocation(MaidSpellMod.MOD_ID, "no_carvers"));
    private static final int MARGIN = 2;
    private static final int DEPTH = 4;

    private static final ThreadLocal<List<BoundingBox>> PROTECTED = ThreadLocal.withInitial(List::of);

    private CarverGuard() {
    }

    /** 一个区块开始挖洞之前，找出引用了这个区块的受保护结构。 */
    public static void begin(StructureManager structureManager, RegistryAccess registryAccess, ChunkPos chunkPos) {
        Registry<Structure> registry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        PROTECTED.set(structureManager.startsForStructure(chunkPos, structure -> registry.wrapAsHolder(structure).is(TAG))
                .stream().filter(StructureStart::isValid).map(StructureStart::getBoundingBox).toList());
    }

    public static void end() {
        PROTECTED.remove();
    }

    /** 世界坐标 (x, y, z) 是否在受保护结构的范围里。 */
    public static boolean blocked(int x, int y, int z) {
        for (BoundingBox box : PROTECTED.get()) {
            if (y >= box.minY() - DEPTH
                    && x >= box.minX() - MARGIN && x <= box.maxX() + MARGIN
                    && z >= box.minZ() - MARGIN && z <= box.maxZ() + MARGIN) {
                return true;
            }
        }
        return false;
    }
}
