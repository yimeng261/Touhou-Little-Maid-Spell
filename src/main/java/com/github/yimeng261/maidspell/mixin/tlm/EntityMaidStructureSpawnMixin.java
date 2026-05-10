package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
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

import java.util.Set;

@Mixin(value = EntityMaid.class, remap = false)
public class EntityMaidStructureSpawnMixin {
    @Unique
    private static final Set<ResourceLocation> maidspell$deniedStructures = Set.of(
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "hidden_retreat"),
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "fairy_maid_cafe"),
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "relic_sanctum"),
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "fallen_sanctum"),
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "elven_realm")
    );

    @Shadow
    private boolean structureSpawn;

    @Inject(method = "finalizeSpawn",
            at = @At("HEAD"),
            cancellable = true,
            remap = true)
    private void maidspell$skipRandomModelForProtectedStructures(ServerLevelAccessor worldIn,
                                                                 DifficultyInstance difficultyIn,
                                                                 MobSpawnType reason,
                                                                 SpawnGroupData spawnDataIn,
                                                                 CallbackInfoReturnable<SpawnGroupData> cir) {
        if (worldIn.isClientSide()) {
            return;
        }
        try {
            if (reason != MobSpawnType.STRUCTURE) {
                return;
            }

            EntityMaid maid = (EntityMaid) (Object) this;
            BlockPos maidPos = maid.blockPosition();
            if (maidspell$isInProtectedStructure(worldIn, maidPos)) {
                this.structureSpawn = false;
                Global.LOGGER.debug("Prevented finalizeSpawn processing for maid in protected structure at {}", maidPos);
                cir.setReturnValue(spawnDataIn);
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to prevent finalizeSpawn processing for protected-structure maid", e);
        }
    }

    @Unique
    private boolean maidspell$isInProtectedStructure(ServerLevelAccessor worldIn, BlockPos pos) {
        StructureManager structureManager = worldIn.getLevel().structureManager();
        Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
                worldIn.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation structureKey : maidspell$deniedStructures) {
            var structure = structureRegistry.getOptional(structureKey);
            if (structure.isPresent() && structureManager.getStructureWithPieceAt(pos, structure.get()).isValid()) {
                return true;
            }
        }
        return false;
    }
}
