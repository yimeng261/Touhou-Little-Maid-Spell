package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.mixin.accessor.StructureManagerAccessor;
import com.github.yimeng261.maidspell.worldgen.accessor.RandomStateAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin 到 ChunkGenerator，阻止非白名单结构在归隐之地维度生成。
 * hidden_retreat 的"每维度一个"限制在 HiddenRetreatStructure.generate() 中处理。
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void onTryGenerateStructure(
            StructureSet.StructureSelectionEntry structureEntry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!maidspell$isRetreatDimension(RandomStateAccessor.dimensionOf(randomState))) {
            return;
        }

        var structureKey = structureEntry.structure().unwrapKey();
        if (structureKey.isEmpty()) {
            return;
        }

        ResourceLocation structureId = structureKey.get().location();

        // 开关：放行全部结构
        if (Config.allowAllStructures) {
            return;
        }

        // 白名单结构放行
        if (Config.allowedStructures.contains(structureId)) {
            return;
        }

        // 其他结构不允许生成
        cir.setReturnValue(false);
    }

    /**
     * 在归隐之地维度中，对 MONSTER 类别返回空生成列表，从源头禁止敌对生物生成
     */
    @Inject(method = "getMobsAt", at = @At("HEAD"), cancellable = true)
    private void onGetMobsAt(
            Holder<Biome> biome,
            StructureManager structureManager,
            MobCategory category,
            BlockPos pos,
            CallbackInfoReturnable<WeightedRandomList<MobSpawnSettings.SpawnerData>> cir
    ) {
        if (Config.disableHostileMobSpawning
                && category == MobCategory.MONSTER
                && maidspell$isRetreatDimension(maidspell$dimensionOf(structureManager))) {
            cir.setReturnValue(WeightedRandomList.create());
        }
    }

    /** 刷怪走的是维度自己的 StructureManager，从它持有的世界认维度；认不出时为 null。 */
    @Unique
    @Nullable
    private static ResourceKey<Level> maidspell$dimensionOf(StructureManager structureManager) {
        LevelAccessor level = ((StructureManagerAccessor) structureManager).maidspell$getLevel();
        return level instanceof WorldGenLevel worldGenLevel ? worldGenLevel.getLevel().dimension() : null;
    }

    @Unique
    private static boolean maidspell$isRetreatDimension(@Nullable ResourceKey<Level> dimensionKey) {
        if (dimensionKey == null) {
            return false;
        }
        ResourceLocation loc = dimensionKey.location();
        return loc.getNamespace().equals(MaidSpellMod.MOD_ID)
                && loc.getPath().startsWith("the_retreat");
    }
}
