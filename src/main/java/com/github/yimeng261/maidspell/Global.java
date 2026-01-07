package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;

import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 全局状态管理
 * 
 * 修复说明：
 * 1. maidInfos 改为 ConcurrentHashMap 保证线程安全
 * 2. maidList 改为 ConcurrentHashMap.newKeySet() 保证线程安全
 * 3. 其他回调列表改为 CopyOnWriteArrayList 或 ConcurrentHashMap 保证线程安全
 */
public class Global {

    public static final Logger LOGGER = LogUtils.getLogger();

    // 使用 CopyOnWriteArrayList 保证线程安全，适合读多写少的场景
    public static final List<BiFunction<LivingHurtEvent, EntityMaid, Void>> commonDamageCalc = new CopyOnWriteArrayList<>();

    public static final List<BiFunction<LivingDamageEvent, Player, Void>> playerHurtCalcAft = new CopyOnWriteArrayList<>();

    // 使用 ConcurrentHashMap 保证线程安全
    // 外层 Map: 玩家UUID -> 女仆Map
    // 内层 Map: 女仆UUID -> 女仆实体
    public static final Map<UUID, Map<UUID, EntityMaid>> maidInfos = new ConcurrentHashMap<>();

    // 使用线程安全的 Set
    public static final Set<EntityMaid> maidList = ConcurrentHashMap.newKeySet();

    public static final List<BiFunction<LivingHurtEvent, EntityMaid, Void>> commonHurtCalc = new CopyOnWriteArrayList<>();

    public static final List<Function<IMaidSpellData.CoolDown, Void>> commonCoolDownCalc = new CopyOnWriteArrayList<>();

    // 饰品相关的回调使用 ConcurrentHashMap
    public static final Map<Item, BiFunction<LivingDamageEvent, EntityMaid, Void>> baubleDamageCalcAft = new ConcurrentHashMap<>();

    public static final Map<Item, BiFunction<LivingHurtEvent, EntityMaid, Void>> baubleDamageCalcPre = new ConcurrentHashMap<>();

    public static final Map<Item, BiFunction<LivingHurtEvent, EntityMaid, Void>> baubleCommonHurtCalcPre = new ConcurrentHashMap<>();

    public static final Map<Item, Function<DataItem, Void>> baubleHurtCalcPre = new ConcurrentHashMap<>();

    public static final Map<Item, Function<DataItem, Void>> baubleHurtCalcFinal = new ConcurrentHashMap<>();

    public static final Map<Item, Function<IMaidSpellData.CoolDown, Void>> baubleCoolDownCalc = new ConcurrentHashMap<>();

    public static final Map<Item, BiFunction<MobEffectEvent.Added, EntityMaid, Void>> baubleEffectAddedCalc = new ConcurrentHashMap<>();

    public static final Map<Item, BiFunction<LivingDeathEvent, EntityMaid, Void>> baubleDeathCalc = new ConcurrentHashMap<>();

    public static void resetCommonDamageCalc() {
        commonDamageCalc.clear();
        commonDamageCalc.add((event, maid) -> {
            LivingEntity entity = event.getEntity();
            if (entity instanceof EntityMaid) {
                event.setCanceled(true);
            } else if (entity instanceof Player) {
                event.setCanceled(true);
            }
            return null;
        });
        commonDamageCalc.add((hurtEvent, maid) -> {
            if (maid.getTask().getUid().toString().startsWith("maidspell")) {
                hurtEvent.setAmount((float) (hurtEvent.getAmount() * Config.spellDamageMultiplier));
            }
            return null;
        });
    }

    public static void resetCommonCoolDownCalc() {
        commonCoolDownCalc.clear();
        commonCoolDownCalc.add((coolDown -> {
            coolDown.cooldownticks = (int) (coolDown.cooldownticks * Config.coolDownMultiplier);
            return null;
        }));
    }
    
    /**
     * 安全地获取或创建玩家的女仆映射
     * 使用 computeIfAbsent 保证线程安全
     */
    public static Map<UUID, EntityMaid> getOrCreatePlayerMaidMap(UUID playerUUID) {
        return maidInfos.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
    }

    public static void updateMaidInfo(EntityMaid maid, Boolean add) {
        UUID ownerUUID = maid.getOwnerUUID();
        if(add){
            maidList.add(maid);
            if(ownerUUID != null){
                getOrCreatePlayerMaidMap(ownerUUID).put(maid.getUUID(),maid);
            }
        }else{
            maidList.remove(maid);
            if(ownerUUID != null){
                getOrCreatePlayerMaidMap(ownerUUID).remove(maid.getUUID());
            }
        }
    }
}
