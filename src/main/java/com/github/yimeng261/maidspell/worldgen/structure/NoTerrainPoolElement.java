package com.github.yimeng261.maidspell.worldgen.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * 与 {@link SinglePoolElement} 功能完全相同，但作为标记类型，
 * 使 Mixin 能在拼图生成时识别该元素并创建 {@link NoTerrainFillPiece}。
 */
public class NoTerrainPoolElement extends SinglePoolElement {

    public static final MapCodec<NoTerrainPoolElement> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    templateCodec(),
                    processorsCodec(),
                    projectionCodec(),
                    overrideLiquidSettingsCodec()
            ).apply(instance, NoTerrainPoolElement::new)
    );

    protected NoTerrainPoolElement(
            Either<ResourceLocation, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            Optional<LiquidSettings> overrideLiquidSettings
    ) {
        super(template, processors, projection, overrideLiquidSettings);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return MaidSpellPoolElementTypes.NO_TERRAIN.get();
    }
}
