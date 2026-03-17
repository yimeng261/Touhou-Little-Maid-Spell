package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.structure.NoTerrainFillPiece;
import com.github.yimeng261.maidspell.worldgen.structure.NoTerrainPoolElement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截拼图系统创建 PoolElementStructurePiece 的调用。
 * 当元素是 {@link NoTerrainPoolElement} 时，创建 {@link NoTerrainFillPiece} 替代，
 * 使该拼图块不参与 Beardifier 地形填充。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public class JigsawPlacerMixin {

    @Redirect(
            method = "tryPlacingChildren",
            at = @At(value = "NEW", target = "net/minecraft/world/level/levelgen/structure/PoolElementStructurePiece")
    )
    private PoolElementStructurePiece redirectNewPiece(
            StructureTemplateManager manager,
            StructurePoolElement element,
            BlockPos pos,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox box,
            LiquidSettings liquidSettings
    ) {
        if (element instanceof NoTerrainPoolElement) {
            return new NoTerrainFillPiece(manager, element, pos, groundLevelDelta, rotation, box, liquidSettings);
        }
        return new PoolElementStructurePiece(manager, element, pos, groundLevelDelta, rotation, box, liquidSettings);
    }
}
