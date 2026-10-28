package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.HiddenRetreatMaidRegistry;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginData;
import com.github.yimeng261.maidspell.worldgen.accessor.StructureTemplateAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin implements StructureTemplateAccessor {
    @Unique
    private boolean maidSpell$ownTemplate;

    @Override
    public void maidspell$setOwnTemplate(boolean own) {
        this.maidSpell$ownTemplate = own;
    }

    @Shadow
    private static Optional<Entity> createEntityIgnoreException(ServerLevelAccessor level, CompoundTag tag) {
        throw new AssertionError();
    }

    /**
     * 原版放置模板实体只按 {@code EntityType.create} 读顶层实体，Passengers 里的乘客（如坐在座位上的女仆）会丢失。
     * 本模组的模板在这里逐个创建乘客，位置取载具已变换的坐标，同样按放置设置旋转、镜像并初始化，再让它们骑上载具；
     * 乘客与顶层实体走同一个创建方法，女仆的来源标记与隐世女仆等处理照常生效。
     */
    @WrapOperation(
            method = "addEntitiesToWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;createEntityIgnoreException(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;")
    )
    private Optional<Entity> maidSpell$createWithPassengers(ServerLevelAccessor level, CompoundTag tag,
                                                            Operation<Optional<Entity>> original,
                                                            @Local(argsOnly = true) StructurePlaceSettings settings) {
        Optional<Entity> entity = original.call(level, tag);
        if (maidSpell$ownTemplate) {
            entity.ifPresent(vehicle -> maidSpell$addPassengers(level, settings, vehicle, tag));
        }
        return entity;
    }

    @Unique
    private static void maidSpell$addPassengers(ServerLevelAccessor level, StructurePlaceSettings settings, Entity vehicle,
                                                CompoundTag vehicleTag) {
        if (!vehicle.getPassengers().isEmpty()) {
            return;
        }
        ListTag passengers = vehicleTag.getList(Entity.PASSENGERS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < passengers.size(); i++) {
            CompoundTag passengerTag = passengers.getCompound(i).copy();
            passengerTag.put("Pos", vehicleTag.getList("Pos", Tag.TAG_DOUBLE).copy());
            passengerTag.remove("UUID");
            createEntityIgnoreException(level, passengerTag).ifPresent(passenger -> {
                float yRot = passenger.rotate(settings.getRotation());
                yRot += passenger.mirror(settings.getMirror()) - passenger.getYRot();
                passenger.moveTo(vehicle.getX(), vehicle.getY(), vehicle.getZ(), yRot, passenger.getXRot());
                if (settings.shouldFinalizeEntities() && passenger instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(vehicle.blockPosition()), MobSpawnType.STRUCTURE, null);
                }
                maidSpell$addPassengers(level, settings, passenger, passengerTag);
                passenger.startRiding(vehicle, true);
            });
        }
    }
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
        MaidOriginData.captureTemplatePresetEffects(tag);
        if ("touhou_little_maid:maid".equals(tag.getString("id"))
                && !tag.contains("Owner") && MaidOriginData.isHiddenRetreatMaid(tag)) {
            try {
                ci.setReturnValue(HiddenRetreatMaidRegistry.createTemplateMaid(accessor, tag, blockPos));
            } catch (Exception exception) {
                MaidSpellMod.LOGGER.warn("创建隐世之境模板女仆失败，位置: {}", blockPos, exception);
                ci.setReturnValue(Optional.empty());
            }
            return;
        }
        if ((MaidSpellMod.MOD_ID + ":stellar_witch").equals(tag.getString("id")) && posTag.size() == 3) {
            // Pos 已是变换后的世界坐标，而模板里记录的归位坐标不是，这里用实体实际位置覆盖。
            tag.put("WinefoxHomePos", NbtUtils.writeBlockPos(blockPos));
        }
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
