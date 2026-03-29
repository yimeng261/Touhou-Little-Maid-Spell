package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 全局状态管理
 *
 * 修复说明：
 * 1. ownerMaidRegistry 改为 ConcurrentHashMap 保证线程安全
 * 2. activeMaids 改为 ConcurrentHashMap.newKeySet() 保证线程安全
 * 3. 其他回调列表改为 CopyOnWriteArrayList 或 ConcurrentHashMap 保证线程安全
 */
public class Global {

    public static final Logger LOGGER = LogUtils.getLogger();

    // 通用层handler
    public static final List<BiFunction<LivingIncomingDamageEvent, EntityMaid, Void>> commonHurtHandlers = new CopyOnWriteArrayList<>();
    public static final List<BiFunction<LivingIncomingDamageEvent, EntityMaid, Void>> commonHurtCalc = new CopyOnWriteArrayList<>();
    public static final List<Function<IMaidSpellData.CoolDown, Void>> commonCoolDownCalc = new CopyOnWriteArrayList<>();

    // 玩家层handler
    public static final List<BiFunction<LivingIncomingDamageEvent, Player, Void>> playerDamageHandlers = new CopyOnWriteArrayList<>();

    // 外层 Map: 玩家UUID -> 女仆Map
    // 内层 Map: 女仆UUID -> 女仆实体
    public static final Map<UUID, Map<UUID, EntityMaid>> ownerMaidRegistry = new ConcurrentHashMap<>();
    public static final Set<EntityMaid> activeMaids = ConcurrentHashMap.newKeySet();

    // 饰品层handler,按触发阶段顺序排列
    public static final Map<Item, BiFunction<LivingIncomingDamageEvent, EntityMaid, Void>> baubleHurtHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, BiFunction<LivingIncomingDamageEvent, EntityMaid, Void>> baubleHurtEventHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, BiFunction<LivingDamageEvent.Post, EntityMaid, Void>> baubleDamageHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, Function<DataItem, Void>> baubleSetHealthHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, Function<DataItem, Void>> baubleSetHealthFinalHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, Function<IMaidSpellData.CoolDown, Void>> baubleCooldownHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, BiFunction<MobEffectEvent.Added, EntityMaid, Void>> baubleEffectAddedHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, BiFunction<LivingDeathEvent, EntityMaid, Void>> baubleDeathHandlers = new ConcurrentHashMap<>();

    /**
     * 女仆效果双重阻断过滤器。
     *
     * <p>同时作用于两个拦截点，形成双重防护：
     * <ol>
     *   <li>{@code LivingEntity.addEffect} 中的 {@code activeEffects.put} 调用被 @Redirect 重定向——
     *       若过滤器返回 {@code true}，效果不会写入 activeEffects Map，
     *       因此既不会触发 tick 效果，也不会显示粒子/图标。</li>
     *   <li>{@code MobEffect.addAttributeModifiers} 被 @Inject 拦截——
     *       即使效果通过其他途径绕过了第一关（如直接操作 activeEffects），
     *       其属性修改器也不会被应用到实体属性上。</li>
     * </ol>
     *
     * <p>返回 {@code true} 表示阻止该效果；返回 {@code false} 表示放行。
     */
    public static final Map<Item, BiFunction<EntityMaid, Holder<MobEffect>, Boolean>> baubleEffectBlockFilters = new ConcurrentHashMap<>();

    /**
     * 跨 Mixin 通信标志：当 LivingEntityMixin 的 @Redirect 阻止了效果写入时设为 true，
     * MobEffectMixin 的 @Inject 检查并清除此标志以阻止属性修改器被应用。
     * 使用 ThreadLocal 保证线程安全（addEffect 整个流程在同一线程同步执行）。
     */
    public static final ThreadLocal<Boolean> effectBlockFlag = new ThreadLocal<>();

    public static void resetCommonDamageCalc() {
        commonHurtHandlers.clear();
        commonHurtHandlers.add((event, maid) -> {
            LivingEntity entity = event.getEntity();
            if (entity instanceof EntityMaid) {
                event.setCanceled(true);
            } else if (entity instanceof Player) {
                event.setCanceled(true);
            }
            return null;
        });
        commonHurtHandlers.add((hurtEvent, maid) -> {
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
        return ownerMaidRegistry.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
    }

    public static void updateMaidInfo(EntityMaid maid, Boolean add) {
        UUID ownerUUID = maid.getOwnerUUID();
        if(add){
            activeMaids.add(maid);
            if(ownerUUID != null){
                getOrCreatePlayerMaidMap(ownerUUID).put(maid.getUUID(),maid);
            }
        }else{
            activeMaids.remove(maid);
            if(ownerUUID != null){
                getOrCreatePlayerMaidMap(ownerUUID).remove(maid.getUUID());
            }
        }
    }
}
