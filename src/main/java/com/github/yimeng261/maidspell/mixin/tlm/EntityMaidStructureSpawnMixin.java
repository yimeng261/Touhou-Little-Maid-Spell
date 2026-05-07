package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(value = EntityMaid.class, remap = false)
public class EntityMaidStructureSpawnMixin {
    private static final List<String> PROTECTED_STRUCTURES =
            List.of("hidden_retreat", "relic_sanctum", "fallen_sanctum", "elven_realm");

    @Shadow
    private boolean structureSpawn;

    @Inject(method = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;",
            at = @At("HEAD"),
            cancellable = true,
            remap = true)
    private void maidspell$skipRandomModelForProtectedStructures(ServerLevelAccessor worldIn,
                                                                 DifficultyInstance difficultyIn,
                                                                 MobSpawnType reason,
                                                                 @Nullable SpawnGroupData spawnDataIn,
                                                                 @Nullable CompoundTag dataTag,
                                                                 CallbackInfoReturnable<SpawnGroupData> cir) {
        try {
            if (reason != MobSpawnType.STRUCTURE) {
                return;
            }

            EntityMaid maid = (EntityMaid) (Object) this;
            BlockPos maidPos = maid.blockPosition();
            if (maidspell$shouldSkipRandomModel(worldIn, maidPos)) {
                this.structureSpawn = false;
                Global.LOGGER.debug("Prevented finalizeSpawn processing for maid in protected structure at {}", maidPos);
                cir.setReturnValue(spawnDataIn);
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to prevent finalizeSpawn processing for protected-structure maid", e);
        }
    }

    @Unique
    private boolean maidspell$shouldSkipRandomModel(ServerLevelAccessor worldIn, BlockPos pos) {
        StructureManager structureManager = worldIn.getLevel().structureManager();
        Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
                worldIn.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return PROTECTED_STRUCTURES.stream().anyMatch(id ->
                maidspell$isInsideStructure(structureManager, structureRegistry, pos, id));
    }

    @Unique
    private static boolean maidspell$isInsideStructure(StructureManager structureManager,
                                                       Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry,
                                                       BlockPos pos,
                                                       String structureId) {
        @SuppressWarnings("removal")
        var structure = structureRegistry.getOptional(new ResourceLocation("touhou_little_maid_spell", structureId));
        return structure.isPresent() && structureManager.getStructureWithPieceAt(pos, structure.get()).isValid();
    }
}
