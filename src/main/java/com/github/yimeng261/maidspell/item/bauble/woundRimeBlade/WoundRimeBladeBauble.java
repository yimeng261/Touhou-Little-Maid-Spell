package com.github.yimeng261.maidspell.item.bauble.woundRimeBlade;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.mixin.CombatTrackerMixin;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

/**
 * 破愈咒锋饰品实现
 * 监听周围敌对实体的治疗并阻止
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class WoundRimeBladeBauble implements IExtendBauble {

    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<LivingEntity,Float>> maidWoundRimeBladeMap = new ConcurrentHashMap<>();
    private static final Set<UUID> maidWoundRimeBladeSet = new HashSet<>();
    
    public WoundRimeBladeBauble() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onAdd(EntityMaid maid) {}
    
    @Override
    public void onRemove(EntityMaid maid) {
        maidWoundRimeBladeMap.remove(maid.getUUID());
    }
    
    @SubscribeEvent
    public void onEntityHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if(entity instanceof Player){
            return;
        }
        if(maidWoundRimeBladeSet.contains(entity.getUUID())) {
            event.setAmount(0);
        }
    }

    public void onTick(EntityMaid maid, ItemStack baubleItem){
        if(maidWoundRimeBladeMap.containsKey(maid.getUUID())){
            ConcurrentHashMap<LivingEntity,Float> map = maidWoundRimeBladeMap.get(maid.getUUID());
            map.forEach((entity, health) -> {
                if(!entity.isAlive()){
                    map.remove(entity);
                    return;
                }
                float nowHealth = entity.getHealth();
                List<CombatEntry> entries = ((CombatTrackerMixin)entity.getCombatTracker()).getEntries();
                if(entries.size() > 0){
                    CombatEntry entry = entries.get(entries.size() - 1);
                    if(nowHealth - (health - entry.damage()) < entry.damage()*0.8 ){
                        map.put(entity, health - entry.damage());
                    }
                }
                if(nowHealth > health){
                    TrueDamageUtil.dealTrueDamage(entity, nowHealth - health);
                }
            });
        }
    }

    static {
        Global.bauble_damageProcessors_pre.put(MaidSpellItems.itemDesc(MaidSpellItems.WOUND_RIME_BLADE),(event, maid) -> {
            LivingEntity entity = event.getEntity();
            maidWoundRimeBladeSet.add(entity.getUUID());
            ConcurrentHashMap<LivingEntity,Float> map = maidWoundRimeBladeMap.computeIfAbsent(maid.getUUID(), (uuid) -> new ConcurrentHashMap<>());
            if(!map.containsKey(entity)){
                map.put(entity, entity.getHealth());
            }else{
                if(map.get(entity) > entity.getHealth()){
                    map.put(entity, entity.getHealth());
                }
            }
            return null;
        });
    }
}
