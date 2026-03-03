package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 晋升之环女仆效果事件监听器
 * 参考 revelationfix 的 HaloEvents 和 AbilityEvents 实现
 *
 * 事件优先级说明：
 * - LivingChangeTargetEvent (HIGHEST) - 怪物仇恨控制
 * - LivingHurtEvent (HIGHEST) - 记录女仆攻击的 Boss
 * - LivingAttackEvent (HIGHEST) - 用于完全免疫特定伤害类型
 * - LivingDamageEvent (HIGHEST) - 用于特殊伤害类型的额外减免
 * - LivingDamageEvent (LOWEST) - 用于维度减伤和限伤
 */
@Mod.EventBusSubscriber
public class AscensionHaloMaidEvents {

    private static final String NBT_INVULNERABLE_TIME = "ascension_halo_invulnerable_time";

    /**
     * 记录女仆最近攻击过的 Boss（用于 Boss 中立判定）
     * Key: 女仆 UUID, Value: Map<Boss UUID, 最后攻击时间>
     */
    private static final Map<UUID, Map<UUID, Long>> MAID_ATTACKED_BOSSES = new HashMap<>();
    private static final long BOSS_AGGRO_TIMEOUT = 6000; // 5分钟（6000 ticks）

    /**
     * 药水效果免疫（最高优先级）
     * 参考：revelationfix/CommonEventHandler#haloNoEffects
     *
     * 效果：
     * - 免疫所有负面和中性药水效果
     * - 保留正面效果
     * - 特殊保留：夜视效果（即使是中性也允许）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo == null || !BaubleStateManager.hasBauble(maid, ascensionHalo)) {
            return;
        }

        MobEffect effect = event.getEffectInstance().getEffect();
        MobEffectCategory category = effect.getCategory();

        // 只阻止负面和中性效果，保留正面效果
        if (category == MobEffectCategory.HARMFUL ||
            category == MobEffectCategory.NEUTRAL) {
            // 特殊保留：夜视效果即使是中性也允许
            if (effect != MobEffects.NIGHT_VISION) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    /**
     * 怪物仇恨控制（最高优先级）
     * 参考：revelationfix/AbilityEvents#onMobChangeTarget
     *
     * 效果：
     * - 非 Boss 怪物完全不会主动攻击装备晋升之环的女仆
     * - Boss 怪物中立（除非女仆先攻击）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof EntityMaid maid)) {
            return;
        }

        Item ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo == null || !BaubleStateManager.hasBauble(maid, ascensionHalo)) {
            return;
        }

        LivingEntity attacker = event.getEntity();

        // 检查是否为 Boss
        if (attacker.getType().is(Tags.EntityTypes.BOSSES)) {
            // Boss 中立：只有女仆先攻击过才会反击
            boolean maidAttackedThisBoss = hasMaidRecentlyAttackedBoss(maid, attacker);
            event.setCanceled(!maidAttackedThisBoss);
        } else {
            // 非 Boss 完全不攻击
            event.setCanceled(true);
        }
    }

    /**
     * 记录女仆攻击 Boss 的事件（最高优先级）
     * 参考：revelationfix/AbilityEvents#onPlayerAttackingEntity
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidHurtEntity(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof EntityMaid maid)) {
            return;
        }

        Item ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo == null || !BaubleStateManager.hasBauble(maid, ascensionHalo)) {
            return;
        }

        LivingEntity target = event.getEntity();

        // 如果攻击的是 Boss，记录下来
        if (target.getType().is(Tags.EntityTypes.BOSSES)) {
            recordMaidAttackedBoss(maid, target);
        }
    }
    
    /**
     * 处理伤害免疫（最高优先级）
     * 参考：revelationfix/AbilityEvents#onLivingAttack
     * 
     * 女仆装备晋升之环时免疫以下伤害：
     * - 火焰、熔岩、爆炸、溺水、摔落、窒息、挤压
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        
        Item ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo == null || !BaubleStateManager.hasBauble(maid, ascensionHalo)) {
            return;
        }
        
        DamageSource source = event.getSource();
        
        // 检查无敌状态
        ItemStack baubleStack = findAscensionHaloBauble(maid, ascensionHalo);
        if (baubleStack != null && isInvulnerable(baubleStack)) {
            event.setCanceled(true);
            return;
        }
        
        // 检查是否免疫该伤害类型
        if (isInvulnerableTo(source)) {
            event.setCanceled(true);
        }
    }
    
    /**
     * 处理特殊伤害类型的额外减免（最高优先级）
     * 参考：revelationfix/AbilityEvents#onLivingDamage
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidLivingDamageHighest(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        
        Item ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo == null || !BaubleStateManager.hasBauble(maid, ascensionHalo)) {
            return;
        }
        
        DamageSource source = event.getSource();
        
        // 再次检查免疫（双重保险）
        if (isInvulnerableTo(source)) {
            event.setCanceled(true);
            return;
        }
        
        // 虚空伤害拥有66.6%的减伤
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setAmount(event.getAmount() * (1.0F - 0.66F));
        }
        // 弹射物有额外85%的减伤
        else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            event.setAmount(event.getAmount() * (1.0F - 0.85F));
        }
    }
    
    /**
     * 处理维度减伤和限伤（最低优先级，在 setHealth 前）
     * 参考：revelationfix/HaloEvents#onLivingDamage
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMaidLivingDamageLowest(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        
        Item ascensionHalo = MaidSpellItems.getAscensionHalo();
        if (ascensionHalo == null || !BaubleStateManager.hasBauble(maid, ascensionHalo)) {
            return;
        }
        
        // 检查无敌状态
        ItemStack baubleStack = findAscensionHaloBauble(maid, ascensionHalo);
        if (baubleStack != null && isInvulnerable(baubleStack)) {
            event.setAmount(0.0F);
            return;
        }
        
        // 维度减伤（与原版 HaloEvents 完全一致）
        float reduce;
        ResourceKey<Level> dimension = maid.level().dimension();

        if (dimension == Level.NETHER) {
            reduce = 0.75F; // 下界75%减伤
        } else if (dimension == Level.END) {
            reduce = 0.99F; // 末地99%减伤
        } else {
            reduce = 0.5F; // 主世界50%减伤
        }
        
        // 应用减伤和限伤
        float damageAfterReduction = event.getAmount() * (1.0F - reduce);
        float finalDamage = damageScale(damageAfterReduction, maid);
        
        event.setAmount(finalDamage);
    }
    
    /**
     * 伤害限制（参考 OdamanePlayerExpandedContext.damageScale）
     * 单次伤害不超过限制值
     */
    private static float damageScale(float amount, EntityMaid maid) {
        return Math.min(amount, damageLimit(maid));
    }
    
    /**
     * 计算伤害上限（参考 OdamanePlayerExpandedContext.damageLimit）
     * 取 20 点和最大生命值 25% 中的较小值
     */
    private static float damageLimit(EntityMaid maid) {
        return Math.min(20.0F, maid.getMaxHealth() * 0.25F);
    }
    
    /**
     * 判断是否免疫该伤害类型
     * 参考：OdamanePlayerExpandedContext.isInvulnerableTo
     */
    private static boolean isInvulnerableTo(DamageSource damageSource) {
        // 无法绕过无敌的伤害（如 /kill）不能免疫
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        
        // 免疫的伤害类型
        return damageSource.is(DamageTypeTags.IS_FIRE) ||        // 火焰伤害
               damageSource.is(DamageTypeTags.IS_FALL) ||        // 摔落伤害
               damageSource.is(DamageTypeTags.IS_DROWNING) ||    // 溺水伤害
               damageSource.is(DamageTypeTags.IS_LIGHTNING) ||   // 闪电伤害
               damageSource.is(DamageTypeTags.IS_EXPLOSION) ||   // 爆炸伤害
               damageSource.is(DamageTypeTags.IS_FREEZING) ||    // 冰冻伤害
               damageSource.is(DamageTypes.MAGIC) ||             // 魔法伤害
               damageSource.is(DamageTypes.CACTUS) ||            // 仙人掌伤害
               damageSource.is(DamageTypes.CRAMMING) ||          // 挤压伤害
               damageSource.is(DamageTypes.LAVA) ||              // 熔岩伤害
               damageSource.is(DamageTypes.FALLING_ANVIL) ||     // 铁砧坠落
               damageSource.is(DamageTypes.FLY_INTO_WALL) ||     // 动能（鞘翅撞击）
               damageSource.is(DamageTypes.FREEZE) ||            // 冰冻细雪
               damageSource.is(DamageTypes.IN_WALL);             // 窒息伤害
    }
    
    /**
     * 查找女仆装备的晋升之环
     */
    private static ItemStack findAscensionHaloBauble(EntityMaid maid, Item ascensionHalo) {
        for (int i = 0; i < maid.getMaidBauble().getSlots(); i++) {
            ItemStack stack = maid.getMaidBauble().getStackInSlot(i);
            if (stack.getItem() == ascensionHalo) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 检查女仆是否处于无敌状态
     */
    private static boolean isInvulnerable(ItemStack baubleItem) {
        CompoundTag tag = baubleItem.getOrCreateTag();
        return tag.contains(NBT_INVULNERABLE_TIME) &&
               tag.getInt(NBT_INVULNERABLE_TIME) > 0;
    }

    /**
     * 记录女仆攻击了某个 Boss
     */
    private static void recordMaidAttackedBoss(EntityMaid maid, LivingEntity boss) {
        UUID maidUUID = maid.getUUID();
        UUID bossUUID = boss.getUUID();
        long currentTime = maid.level().getGameTime();

        MAID_ATTACKED_BOSSES.computeIfAbsent(maidUUID, k -> new HashMap<>())
            .put(bossUUID, currentTime);
    }

    /**
     * 检查女仆是否最近攻击过某个 Boss
     */
    private static boolean hasMaidRecentlyAttackedBoss(EntityMaid maid, LivingEntity boss) {
        UUID maidUUID = maid.getUUID();
        UUID bossUUID = boss.getUUID();
        long currentTime = maid.level().getGameTime();

        Map<UUID, Long> attackedBosses = MAID_ATTACKED_BOSSES.get(maidUUID);
        if (attackedBosses == null) {
            return false;
        }

        Long lastAttackTime = attackedBosses.get(bossUUID);
        if (lastAttackTime == null) {
            return false;
        }

        // 检查是否在超时时间内
        return (currentTime - lastAttackTime) < BOSS_AGGRO_TIMEOUT;
    }
}
