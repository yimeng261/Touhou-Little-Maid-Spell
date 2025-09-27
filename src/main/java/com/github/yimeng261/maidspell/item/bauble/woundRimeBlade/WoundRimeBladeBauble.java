package com.github.yimeng261.maidspell.item.bauble.woundRimeBlade;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

/**
 * 破愈咒锋饰品实现
 * 监听周围敌对实体的治疗并阻止
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class WoundRimeBladeBauble implements IExtendBauble {
    
    public WoundRimeBladeBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onAdd(EntityMaid maid) {}
    
    @Override
    public void onRemove(EntityMaid maid) {
        // 移除饰品时不需要特殊处理
    }
    
    @SubscribeEvent
    public void onEntityHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag tag = new CompoundTag();
        entity.readAdditionalSaveData(tag);
        if(tag.getBoolean("wound_rime_blade")) {
            event.setCanceled(true);
        }
    }

    static {
        Global.bauble_damageProcessors_pre.put(MaidSpellItems.itemDesc(MaidSpellItems.WOUND_RIME_BLADE),(event, maid) -> {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("wound_rime_blade", true);
            event.getEntity().addAdditionalSaveData(tag);
            return null;
        });
    }
}
