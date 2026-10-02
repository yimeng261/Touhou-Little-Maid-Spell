package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    /**
     * 模板里画的 blockPos 是它挂靠的方块，pos 却是实体中心；偶数尺寸那一维的中心落在方块边界上，
     * 放置时 {@code BlockPos.containing(pos)} 会取到相邻方块，画随之上移或横移一格甚至掉落。
     * 这里在所有 processor 之后把画的位置改成已变换的挂靠方块中心，四种旋转下取整都回到 blockPos。
     */
    @Inject(method = "processEntityInfos", at = @At("RETURN"))
    private static void maidSpell$anchorPaintingsToBlockPos(@Nullable StructureTemplate template, LevelAccessor level,
                                                            BlockPos offset, StructurePlaceSettings settings,
                                                            List<StructureTemplate.StructureEntityInfo> entityInfos,
                                                            CallbackInfoReturnable<List<StructureTemplate.StructureEntityInfo>> cir) {
        cir.getReturnValue().replaceAll(info -> "minecraft:painting".equals(info.nbt.getString("id"))
                ? new StructureTemplate.StructureEntityInfo(Vec3.atCenterOf(info.blockPos), info.blockPos, info.nbt)
                : info);
    }

    @Inject(
            method = "createEntityIgnoreException(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void createEntityIgnoreException(ServerLevelAccessor accessor, CompoundTag tag, CallbackInfoReturnable<Optional<Entity>> ci) {
        if (!maidSpell$isSafeServerAccessor(accessor)) {
            return;
        }

        ListTag posTag = tag.getList("Pos", 6);
        BlockPos blockPos = BlockPos.containing(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
        if (maidSpell$isInEnchantressFootstepsOutpostStructure(accessor, blockPos)) {
            try {
                ci.setReturnValue(EntityType.create(tag, accessor.getLevel()));
            } catch (Exception ignored) {
                ci.setReturnValue(Optional.empty());
            }
        }
    }

    @Unique
    private static boolean maidSpell$isInEnchantressFootstepsOutpostStructure(ServerLevelAccessor worldIn, BlockPos pos) {
        if (!maidSpell$isSafeServerAccessor(worldIn)) {
            return false;
        }

        try {
            var structureManager = worldIn.getLevel().structureManager();
            var enchantressFootstepsOutpostStructureSet = worldIn.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .getOptional(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "enchantress_footsteps_outpost"));
            return enchantressFootstepsOutpostStructureSet.isPresent() &&
                    structureManager.getStructureWithPieceAt(pos, enchantressFootstepsOutpostStructureSet.get()).isValid();
        } catch (IllegalStateException | UnsupportedOperationException e) {
            return false;
        }
    }

    @Unique
    private static boolean maidSpell$isSafeServerAccessor(ServerLevelAccessor accessor) {
        if (accessor == null) {
            return false;
        }
        if (accessor instanceof ServerLevel) {
            return true;
        }
        try {
            return accessor.getLevel() != null;
        } catch (IllegalStateException | UnsupportedOperationException e) {
            return false;
        }
    }
}
