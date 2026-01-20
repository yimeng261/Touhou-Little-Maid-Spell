package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.accessor.ChunkGeneratorAccessor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin到ChunkGenerator，阻止其他结构在归隐之地维度生成
 * 
 * 策略：
 * 1. 检测当前维度是否是归隐之地（通过维度名称：touhou_little_maid_spell:the_retreat_*）
 * 2. 如果是，只允许HiddenRetreatStructure生成，拦截其他所有结构
 * 3. 这样就不需要在HiddenRetreatStructure中检查重叠
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin implements ChunkGeneratorAccessor {
    
    @Shadow
    public abstract BiomeSource getBiomeSource();
    
    /**
     * 隐世之境结构ID
     */
    @Unique
    private static final ResourceLocation HIDDEN_RETREAT_ID = new ResourceLocation(MaidSpellMod.MOD_ID, "hidden_retreat");
    
    /**
     * 存储当前ChunkGenerator所属的维度
     */
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
    
    /**
     * 拦截结构生成方法
     * 在归隐之地维度中，只允许HiddenRetreatStructure生成
     */
    @Inject(
        method = "tryGenerateStructure",
        at = @At("HEAD"),
        cancellable = true
    )
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
        // 检查当前维度是否是归隐之地
        if (!maidspell$isRetreatDimension()) {
            return; // 不是归隐之地，允许正常生成
        }
        
        // 获取当前尝试生成的结构ID
        var structureKey = structureEntry.structure().unwrapKey();
        if (structureKey.isEmpty()) {
            return;
        }
        
        ResourceLocation structureId = structureKey.get().location();
        
        // 如果是HiddenRetreatStructure，允许生成
        if (HIDDEN_RETREAT_ID.equals(structureId)) {
            MaidSpellMod.LOGGER.debug("Allowing HiddenRetreat structure generation at chunk {}, {}", 
                chunkPos.x, chunkPos.z);
            return;
        }
        
        // 其他所有结构在归隐之地中都不允许生成
        MaidSpellMod.LOGGER.debug("Blocking structure {} generation in retreat dimension at chunk {}, {}", 
            structureId, chunkPos.x, chunkPos.z);
        cir.setReturnValue(false);
    }
    
    /**
     * 检查当前维度是否是归隐之地
     * 归隐之地特征：维度名称格式为 touhou_little_maid_spell:the_retreat_*
     */
    @Unique
    private boolean maidspell$isRetreatDimension() {
        try {
            // 如果维度信息未设置，返回false
            if (maidspell$dimensionKey == null) {
                return false;
            }
            
            ResourceLocation dimensionLocation = maidspell$dimensionKey.location();
            
            // 检查命名空间和路径前缀
            return dimensionLocation.getNamespace().equals(MaidSpellMod.MOD_ID)
                    && dimensionLocation.getPath().startsWith("the_retreat");
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Error checking if dimension is retreat", e);
            return false;
        }
    }
}


