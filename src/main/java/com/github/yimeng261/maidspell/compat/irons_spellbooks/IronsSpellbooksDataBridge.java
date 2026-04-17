package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;
import net.minecraft.world.entity.LivingEntity;

/**
 * ISS 数据桥接类，隔离对 MaidIronsSpellData 的直接引用。
 * <p>
 * 本类只在 Iron's Spellbooks 已加载时才会被访问（由调用方通过 ModList 守卫），
 * 因此不会在 ISS 缺失时触发 NoClassDefFoundError。
 */
public final class IronsSpellbooksDataBridge {

    private IronsSpellbooksDataBridge() {
    }

    public static LivingEntity getOriginTarget(EntityMaid maid) {
        MaidIronsSpellData data = MaidIronsSpellData.getOrCreate(maid);
        return data.getOriginTarget();
    }

    public static boolean isSpecialOwnerCastCase(EntityMaid maid) {
        MaidIronsSpellData data = MaidIronsSpellData.getOrCreate(maid);
        LivingEntity currentTarget = data.getTarget();
        LivingEntity originTarget = data.getOriginTarget();
        LivingEntity owner = maid.getOwner();
        return currentTarget == owner && originTarget instanceof LivingEntity living && living != owner;
    }
}
