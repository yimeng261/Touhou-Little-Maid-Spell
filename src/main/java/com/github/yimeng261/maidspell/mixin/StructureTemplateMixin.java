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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
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
                    .getOptional(new ResourceLocation(MaidSpellMod.MOD_ID, "enchantress_footsteps_outpost"));

            if (enchantressFootstepsOutpostStructureSet.isPresent()) {
                // 仅在真实服务端世界中查询结构，避免 Create Ponder 的客户端假世界崩溃
                var structureStart = structureManager.getStructureWithPieceAt(pos, enchantressFootstepsOutpostStructureSet.get());
                return structureStart.isValid();
            }
        } catch (IllegalStateException e) {
            return false;
        } catch (UnsupportedOperationException e) {
            return false;
        }
        return false;
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
        } catch (IllegalStateException e) {
            return false;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }
}
