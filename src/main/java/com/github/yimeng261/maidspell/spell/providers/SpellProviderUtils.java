package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public final class SpellProviderUtils {

    private SpellProviderUtils() {
    }

    public static String describeItem(ItemStack stack) {
        if (stack == null) {
            return "<null>";
        }
        if (stack.isEmpty()) {
            return "<empty>";
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? stack.getItem().getDescriptionId() : key.toString();
    }

    public static String describeEntity(LivingEntity entity) {
        if (entity == null) {
            return "<null>";
        }
        return entity.getType().builtInRegistryHolder().key().location() + "@" + entity.getUUID();
    }

    public static void orientToTarget(EntityMaid maid, LivingEntity target) {
        if (target == null) return;

        double dx = target.getX() - maid.getX();
        double dy = target.getEyeY() - maid.getEyeY();
        double dz = target.getZ() - maid.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI));

        maid.setYRot(yaw);
        maid.setXRot(pitch);
        maid.setYHeadRot(yaw);
        maid.yBodyRot = yaw;
        maid.yRotO = yaw;
        maid.xRotO = pitch;
        maid.yHeadRotO = yaw;
        maid.yBodyRotO = yaw;
    }

    public static Vec3 orientAndGetDirection(EntityMaid maid, LivingEntity target) {
        if (target == null) return Vec3.ZERO;

        Vec3 eyePos = maid.getEyePosition();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 direction = targetPos.subtract(eyePos);
        double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

        float yaw = (float) (Math.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(direction.y, horizontalDist) * 180.0 / Math.PI));

        maid.setYRot(yaw);
        maid.setXRot(pitch);
        maid.setYHeadRot(yaw);
        maid.yBodyRot = yaw;

        return direction.normalize();
    }
}
