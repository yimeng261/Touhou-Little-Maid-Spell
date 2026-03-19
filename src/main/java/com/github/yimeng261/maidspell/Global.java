package com.github.yimeng261.maidspell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.DataItem;
import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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

    // 通用层handler
    public static final List<Consumer<HurtHeadContext>> commonHurtHeadHandlers = new CopyOnWriteArrayList<>();
    public static final List<BiFunction<LivingHurtEvent, EntityMaid, Void>> commonHurtHandlers = new CopyOnWriteArrayList<>();
    public static final List<BiFunction<LivingHurtEvent, EntityMaid, Void>> commonHurtCalc = new CopyOnWriteArrayList<>();
    public static final List<Function<IMaidSpellData.CoolDown, Void>> commonCoolDownCalc = new CopyOnWriteArrayList<>();

    // 玩家层handler
    public static final List<BiFunction<LivingDamageEvent, Player, Void>> playerDamageHandlers = new CopyOnWriteArrayList<>();

    // 外层 Map: 玩家UUID -> 女仆Map
    // 内层 Map: 女仆UUID -> 女仆实体
    public static final Map<UUID, Map<UUID, EntityMaid>> ownerMaidRegistry = new ConcurrentHashMap<>();
    public static final Set<EntityMaid> activeMaids = ConcurrentHashMap.newKeySet();




    // 饰品层handler,按触发阶段顺序排列
    public static final List<BaubleHurtHeadHandler> baubleHurtHeadHandlers = new CopyOnWriteArrayList<>();
    public static final Map<Item, BiFunction<LivingHurtEvent, EntityMaid, Void>> baubleHurtHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, BiFunction<LivingHurtEvent, EntityMaid, Void>> baubleHurtEventHandlers = new ConcurrentHashMap<>();
    public static final Map<Item, BiFunction<LivingDamageEvent, EntityMaid, Void>> baubleDamageHandlers = new ConcurrentHashMap<>();
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
     * <p>注册示例（在饰品 static 块中）：
     * <pre>{@code
     * Global.baubleEffectBlockFilter.put(MaidSpellItems.MY_BAUBLE.get(),
     *     (maid, effect) -> effect.getCategory() == MobEffectCategory.HARMFUL);
     * }</pre>
     *
     * <p>返回 {@code true} 表示阻止该效果；返回 {@code false} 表示放行。
     */
    public static final Map<Item, BiFunction<EntityMaid, MobEffect, Boolean>> baubleEffectBlockFilters = new ConcurrentHashMap<>();

    /**
     * `LivingEntity#hurt` 头部阶段的上下文。
     *
     * <p>这是比 Forge `LivingHurtEvent` 更早的切入点，适合：
     * <ul>
     *   <li>最先判定是否拦截本次 hurt 调用；</li>
     *   <li>处理需要在原版 invulnerable / event 流程前运行的逻辑；</li>
     *   <li>避免 `InfoDamageSource` 一类二次伤害在后续链路中重复展开。</li>
     * </ul>
     *
     * <p>注意：这里的“最高优先级”仅指本模组内部在 `LivingEntityMixin#onHurt`
     * 中最先执行；若其他模组也对 `LivingEntity#hurt` 注入 Mixin，最终顺序仍受 Mixin
     * `priority` 影响。
     */
    public static class HurtHeadContext {
        private final @NotNull LivingEntity entity;
        private final @NotNull DamageSource damageSource;
        private final float amount;
        private Boolean returnValue;

        public HurtHeadContext(@NotNull LivingEntity entity, @NotNull DamageSource damageSource, float amount) {
            this.entity = entity;
            this.damageSource = damageSource;
            this.amount = amount;
        }

        public @NotNull LivingEntity getEntity() {
            return entity;
        }

        public @NotNull LivingEntity getTarget() {
            return entity;
        }

        public @NotNull DamageSource getDamageSource() {
            return damageSource;
        }

        public float getAmount() {
            return amount;
        }

        public Entity getSourceEntity() {
            return damageSource.getEntity();
        }

        public Entity getDirectEntity() {
            return damageSource.getDirectEntity();
        }

        public EntityMaid getSourceMaid() {
            Entity sourceEntity = getSourceEntity();
            if (sourceEntity instanceof EntityMaid maid) {
                return maid;
            }
            Entity directEntity = getDirectEntity();
            if (directEntity instanceof EntityMaid maid) {
                return maid;
            }
            return null;
        }

        public boolean isHandled() {
            return returnValue != null;
        }

        public boolean getReturnValue() {
            return Boolean.TRUE.equals(returnValue);
        }

        public void setReturnValue(boolean returnValue) {
            this.returnValue = returnValue;
        }

        public void cancel() {
            this.returnValue = false;
        }

        public void pass() {
            this.returnValue = null;
        }
    }

    public record BaubleHurtHeadHandler(Predicate<EntityMaid> matcher, Consumer<HurtHeadContext> handler) {
    }

    /**
     * 分发 `LivingEntity#hurt` 头部阶段处理器。
     *
     * @return 若任一处理器已决定本次 hurt 的返回值，返回已处理的上下文；否则返回未处理上下文
     */
    public static HurtHeadContext dispatchHurtHeadHandlers(LivingEntity entity, DamageSource damageSource, float amount) {
        HurtHeadContext context = new HurtHeadContext(entity, damageSource, amount);
        for (Consumer<HurtHeadContext> handler : commonHurtHeadHandlers) {
            handler.accept(context);
            if (context.isHandled()) {
                return context;
            }
        }

        EntityMaid maid = context.getSourceMaid();
        if (maid == null) {
            return context;
        }

        for (BaubleHurtHeadHandler entry : baubleHurtHeadHandlers) {
            if (entry.matcher().test(maid)) {
                entry.handler().accept(context);
            }
            if (context.isHandled()) {
                return context;
            }
        }
        return context;
    }

    public static void registerBaubleHurtHeadHandler(Item item, Consumer<HurtHeadContext> handler) {
        baubleHurtHeadHandlers.add(new BaubleHurtHeadHandler(maid -> BaubleStateManager.hasBauble(maid, item), handler));
    }

    public static void registerBaubleHurtHeadHandler(RegistryObject<Item> item, Consumer<HurtHeadContext> handler) {
        baubleHurtHeadHandlers.add(new BaubleHurtHeadHandler(maid -> BaubleStateManager.hasBauble(maid, item), handler));
    }

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
