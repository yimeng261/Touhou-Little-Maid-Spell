package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.worldgen.accessor.ChunkGeneratorAccessor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
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
 * Mixin到ChunkGenerator，阻止其他结构在归隐之地维度生成
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin implements ChunkGeneratorAccessor {

    @Unique
    private static final ResourceLocation HIDDEN_RETREAT_ID =
        new ResourceLocation(MaidSpellMod.MOD_ID, "hidden_retreat");

    @Unique
    @Nullable
    private ResourceKey<Level> maidspell$dimensionKey = null;

    @Override
    public void maidspell$setDimensionKey(ResourceKey<Level> dimensionKey) {
        this.maidspell$dimensionKey = dimensionKey;
    }

    @Override
    @Nullable
    public ResourceKey<Level> maidspell$getDimensionKey() {
        return this.maidspell$dimensionKey;
    }

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
        if (!maidspell$isRetreatDimension()) {
            return;
        }

        var structureKey = structureEntry.structure().unwrapKey();
        if (structureKey.isEmpty()) {
            return;
        }

        ResourceLocation structureId = structureKey.get().location();

        if (HIDDEN_RETREAT_ID.equals(structureId)) {
            // 私人模式：阶段1 — 原子性预约 pending 槽位，阻止后续候选区块重复调用 generate()。
            // generate() 成功则 completeGeneration()，失败则 cancelGeneration() 释放槽位以便重试。
            // 共享模式：generate() 内部通过 tryAcquireSearchPermit 信号量保证单次生成，Mixin 放行即可。
            if (Config.enablePrivateDimensions && maidspell$dimensionKey != null) {
                if (!RetreatManager.tryBeginGeneration(maidspell$dimensionKey)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            return;
        }

        // 其他结构在归隐之地中不允许生成
        cir.setReturnValue(false);
    }

    @Unique
    private boolean maidspell$isRetreatDimension() {
        if (maidspell$dimensionKey == null) {
            return false;
        }
        ResourceLocation loc = maidspell$dimensionKey.location();
        return loc.getNamespace().equals(MaidSpellMod.MOD_ID)
            && loc.getPath().startsWith("the_retreat");
    }
}
