package com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.mixin.LivingEntityAccessor;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.PortableTimerMath;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.github.yimeng261.maidspell.compat.curios.CuriosCompat;
import com.github.yimeng261.maidspell.compat.curios.DreamCrystalCurios;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 梦云水晶的战斗、支援、耐久修复与复活效果，女仆与佩戴它的玩家共用。具体数值由各处理方法维护。 */
public class DreamCatCrystalBauble implements IMaidBauble {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ========== 时停状态追踪 ==========
    private static final String FROZEN_UNTIL_TAG = MaidSpellMod.MOD_ID + ":dream_crystal_frozen_until";
    private static final long FREEZE_TICKS = 20L;
    private static final Map<UUID, FrozenTargetState> FROZEN_TARGETS = new HashMap<>();
    private static final PriorityQueue<ScheduledExpiry> FROZEN_TARGET_EXPIRIES =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledExpiry::expiry));

    // ========== 范围强化追踪 ==========
    private static final Map<UUID, BoostedMaidState> BOOSTED_MAIDS = new HashMap<>();
    private static final PriorityQueue<ScheduledExpiry> BOOSTED_MAID_EXPIRIES =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledExpiry::expiry));

    private static final int REVIVE_CLOCK_VERSION = 1;
    private static final int MAX_REVIVE_TIMESTAMPS = 10;
    private static final long REVIVE_WINDOW_TICKS = 2400L;
    private static final long REVIVE_INVULNERABLE_TICKS = 300L;
    private static final long RETREAT_EXIT_INVULNERABLE_TICKS = 40L;

    // ========== 属性修饰符 ResourceLocation ==========
    private static final ResourceLocation DC_HP_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_hp");
    private static final ResourceLocation DC_ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_speed");
    private static final ResourceLocation DC_NEARBY_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_nearby_boost");


    // ========== ISS 属性列表 ==========
    private static final List<Holder<net.minecraft.world.entity.ai.attributes.Attribute>> ISS_ATTRIBUTES = new ArrayList<>();

    static {
        // 初始化 ISS 属性（如果铁魔法加载）
        if (IronsSpellbooksCompat.isLoaded()) {
            BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
                if (holder.key().location().toString().startsWith("irons_spellbooks:")) {
                    ISS_ATTRIBUTES.add(holder);
                }
            });
        }

        // ========== 法术冷却取消 ==========
        Global.baubleCooldownHandlers.put(MaidSpellItems.DREAM_CAT_CRYSTAL.get(), (coolDown) -> {
            if (coolDown.maid != null && BaubleStateManager.hasBauble(coolDown.maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) {
                coolDown.cooldownticks = 0;
            }
            return null;
        });

        // ========== 女仆受伤最终处理：30% 抗性 + 40 伤害上限 ==========
        Global.baubleSetHealthFinalHandlers.put(MaidSpellItems.DREAM_CAT_CRYSTAL.get(), (data) -> {
            float amount = data.getAmount();
            // 30% 全伤害抗性
            amount *= 0.7f;
            // 单次伤害上限 40
            amount = Math.min(amount, 40.0f);
            data.setAmount(amount);
            return null;
        });

        // ========== 女仆造成伤害头部处理：真实伤害 + 时停 + 弹幕溅射 ==========
        Global.registerBaubleHurtHeadHandler(MaidSpellItems.DREAM_CAT_CRYSTAL.get(), context -> {
            if (TrueDamageUtil.isApplyingQueuedDamage()) return;
            EntityMaid maid = context.getSourceMaid();
            if (maid == null) {
                return;
            }

            LivingEntity target = context.getTarget();
            if (!IAuthoritativeHealth.combatAlive(target)) {
                return;
            }

            float damage = context.getAmount();
            // 受击间隔内原版只结算超出上一次伤害的部分，整击被挡下时不追加真伤、时停和溅射
            if (target.invulnerableTime > 10 && !context.getDamageSource().is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                damage -= ((LivingEntityAccessor) target).maidspell$getLastHurt();
                if (damage <= 0.0F) {
                    return;
                }
            }

            // 1. 真实伤害（额外等于攻击伤害）
            if (Config.dreamCrystalExtraTrueDamageEnabled) {
                TrueDamageUtil.dealTrueDamage(target, damage, maid);
            }

            // 2. 时停 1 秒（仅在服务端执行）
            if (!maid.level().isClientSide() && IAuthoritativeHealth.combatAlive(target)
                    && target instanceof Mob mob) {
                freezeTarget(mob);
            }

            // 3. 弹幕溅射：对目标周围 5 格内的敌方实体造成 10% 伤害
            if (!maid.level().isClientSide()) {
                float barrageDamage = damage * 0.1f;
                maid.level().getEntitiesOfClass(
                        LivingEntity.class,
                        target.getBoundingBox().inflate(5.0),
                        entity -> entity != maid
                                && entity != target
                                && IAuthoritativeHealth.combatAlive(entity)
                                && !(entity instanceof net.minecraft.world.entity.player.Player)
                                && !(entity instanceof EntityMaid)
                ).forEach(nearby -> TrueDamageUtil.dealTrueDamage(nearby, barrageDamage, maid));
            }

        });

        // ========== 有害效果双重免疫过滤器（与 MobEffectMixin + LivingEntityMixin 配合） ==========
        // 返回 true → 阻止效果写入 activeEffects（不 tick/不显示）且阻止其属性修改器被应用
        Global.baubleEffectBlockFilters.put(MaidSpellItems.DREAM_CAT_CRYSTAL.get(),
                (maid, effect) -> effect.value().getCategory() != MobEffectCategory.BENEFICIAL);

        // ========== 死亡概率复活 ==========
        Global.baubleDeathHandlers.put(MaidSpellItems.DREAM_CAT_CRYSTAL.get(), (event, maid) -> {
            if (event.isCanceled()) return null;
            ItemStack baubleStack = findDreamCrystalStack(maid);
            if (baubleStack.isEmpty()) {
                return null;
            }

            if (DreamCatCrystalBauble.tryRevive(maid, baubleStack)) {
                event.setCanceled(true);
            }
            return null;
        });
    }

    /**
     * 注册到 commonCoolDownCalc（在 Config.onLoad 后调用，防止被 resetCommonCoolDownCalc 清除）
     * 为被范围强化的邻近女仆降低法术冷却至 1/3
     */
    public static void registerCommonCallbacks() {
        Global.commonCoolDownCalc.add(coolDown -> {
            if (coolDown.maid == null) return null;
            if (isMaidBoosted(coolDown.maid)) {
                coolDown.cooldownticks = coolDown.cooldownticks / 3;
            }
            return null;
        });
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        tickWearer(maid, baubleItem);
    }

    public void tickWearer(LivingEntity maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) return;

        int tick = maid.tickCount;

        // ========== 每 tick：无敌到期 ==========
        handleInvulnerable(maid, baubleItem);

        if (tick % 20 != 3) return;

        // ========== 每 20 tick（约 1 秒）的效果 ==========

        normalizeReviveHistory(maid, baubleItem);

        // 1. 血量上限 +50%
        applyAttributeModifier(maid, Attributes.MAX_HEALTH, DC_HP_ID,
                0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // 2. 攻击速度 ×2
        applyAttributeModifier(maid, Attributes.ATTACK_SPEED, DC_ATTACK_SPEED_ID,
                1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // 3. ISS 法强翻倍（如果铁魔法已加载）
        if (!ISS_ATTRIBUTES.isEmpty()) {
            for (Holder<net.minecraft.world.entity.ai.attributes.Attribute> attrHolder : ISS_ATTRIBUTES) {
                ResourceLocation issId = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID,
                        "dream_crystal_iss_" + attrHolder.getRegisteredName().replace(':', '_'));
                applyAttributeModifier(maid, attrHolder, issId, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }

        // 4. 维度特定 Buff
        applyDimensionBuffs(maid, baubleItem);

        // 5. 扫描范围内女仆并施加强化
        applyRangeMaidBoost(maid);

        // 6. 修复整个背包物品耐久度（每 20tick = 每秒 1 点）
        repairInventory(maid);

        // 7. 维持 curios 额外槽位（transient modifier 需要周期性刷新）
        applyCuriosSlots(maid);

        // ========== 每 600 tick（30 秒）随机正面效果 ==========
        if (tick % 600 == 3) {
            applyRandomBeneficialEffects(maid);
        }
    }

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) return;
        // 装备时立即增加 curios 槽位
        applyCuriosSlots(maid);
        // 触发"万法皆通"进度
        grantAdvancement(maid);
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) return;
        // 卸下时清除无敌状态
        baubleItem.remove(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS);
        baubleItem.remove(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL);
        removeWearerEffects(maid);
    }

    public void removeWearerEffects(LivingEntity maid) {
        if (maid.level().isClientSide()) return;
        // 卸下时移除 curios 额外槽位
        if (CuriosCompat.isLoaded()) DreamCrystalCurios.setExtraSlots(maid, false);

        // 卸下时移除属性修饰符
        removeAttributeModifier(maid, Attributes.MAX_HEALTH, DC_HP_ID);
        removeAttributeModifier(maid, Attributes.ATTACK_SPEED, DC_ATTACK_SPEED_ID);
        if (!ISS_ATTRIBUTES.isEmpty()) {
            for (Holder<net.minecraft.world.entity.ai.attributes.Attribute> attrHolder : ISS_ATTRIBUTES) {
                ResourceLocation issId = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID,
                        "dream_crystal_iss_" + attrHolder.getRegisteredName().replace(':', '_'));
                removeAttributeModifier(maid, attrHolder, issId);
            }
        }
        maid.setHealth(Math.min(maid.getHealth(), maid.getMaxHealth()));
    }

    /**
     * 按最近 120 秒内的复活次数 N 以 100% - N×10% 的概率复活：回满血并获得 15 秒无敌。
     */
    public static boolean tryRevive(LivingEntity maid, ItemStack baubleStack) {
        MinecraftServer server = maid.getServer();
        if (server == null) {
            return false;
        }

        // 获取并过滤最近 120 秒（2400 tick）内的复活时间戳
        long currentTime = globalGameTime(server);
        ReviveHistory history = readReviveHistory(baubleStack, currentTime);
        if (history == null) {
            return false;
        }
        List<Long> timestamps = history.timestamps();

        int n = timestamps.size();
        // 复活概率 = 100% - N×10%
        float reviveChance = Math.max(0.0f, 1.0f - n * 0.1f);
        boolean revived = reviveChance > 0.0f && maid.getRandom().nextFloat() < reviveChance;
        if (revived) {
            timestamps.add(currentTime);
        }
        if (history.changed() || revived) {
            saveReviveHistory(baubleStack, timestamps);
        }
        if (!revived) {
            return false;
        }

        // 恢复到最大生命值
        float healAmount = maid.getMaxHealth();
        maid.setHealth(healAmount);

        // 设置 15 秒无敌（300 tick）
        baubleStack.set(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL,
                PortableTimerMath.saturatingAdd(currentTime, REVIVE_INVULNERABLE_TICKS));

        maid.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);

        LOGGER.info("{} 触发梦云水晶概率复活（N={}，概率={}%），恢复至 {} 生命值",
                maid.getName().getString(), n, (int) (reviveChance * 100), healAmount);
        return true;
    }

    // ========== 无敌到期处理 ==========
    /**
     * 无敌按截止时间判断，物品组件只在复活、离开归隐之地和到期时改写，Curios 不会因此反复触发卸下、装备。
     */
    private void handleInvulnerable(LivingEntity wearer, ItemStack baubleItem) {
        MinecraftServer server = wearer.getServer();
        if (server == null) {
            return;
        }
        long now = globalGameTime(server);
        migrateLegacyInvulnerableTicks(baubleItem, now);
        Long until = baubleItem.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL);
        if (until != null && now >= until) {
            baubleItem.remove(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL);
        }
    }

    /**
     * 佩戴者从归隐之地去往别的维度时，无敌截止时间至少延到 2 秒后，落地瞬间不会受伤。
     * 只在换维度时写一次组件，已有更晚的截止时间（如复活无敌）时不动。
     */
    public static void extendInvulnerableOnRetreatExit(LivingEntity wearer, ItemStack stack, ResourceLocation destination) {
        MinecraftServer server = wearer.getServer();
        if (stack.isEmpty() || server == null || !TheRetreatDimension.isInRetreat(wearer)
                || TheRetreatDimension.isRetreatDimension(destination)) {
            return;
        }
        long now = globalGameTime(server);
        migrateLegacyInvulnerableTicks(stack, now);
        extendInvulnerableUntil(stack, PortableTimerMath.saturatingAdd(now, RETREAT_EXIT_INVULNERABLE_TICKS));
    }

    /** 旧版按剩余 tick 记的无敌换成截止时间，剩余时间最多按一次复活无敌算 */
    private static void migrateLegacyInvulnerableTicks(ItemStack stack, long now) {
        Integer legacyTicks = stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS);
        if (legacyTicks != null) {
            stack.remove(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS);
            extendInvulnerableUntil(stack, PortableTimerMath.saturatingAdd(now,
                    PortableTimerMath.clampRemaining(legacyTicks, REVIVE_INVULNERABLE_TICKS, 0L)));
        }
    }

    /** 已有更晚的截止时间时不动 */
    private static void extendInvulnerableUntil(ItemStack stack, long until) {
        Long current = stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL);
        if (current == null || current < until) {
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL, until);
        }
    }

    // ========== 属性修饰符辅助方法 ==========
    private void applyAttributeModifier(LivingEntity maid, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                        ResourceLocation id, double value,
                                        AttributeModifier.Operation operation) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null) return;
        if (instance.getModifier(id) == null) {
            instance.addTransientModifier(new AttributeModifier(id, value, operation));
        }
    }

    private static void removeAttributeModifier(LivingEntity maid, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                                ResourceLocation id) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
    }

    // ========== 维度特定 Buff ==========
    private void applyDimensionBuffs(LivingEntity maid, ItemStack baubleStack) {
        // 归隐之地里的无敌由 isInvulnerable 按所在维度判断
        if (TheRetreatDimension.isInRetreat(maid)) {
            return;
        }

        Level level = maid.level();
        if (level.dimension() == Level.OVERWORLD) {
            // 主世界：饱和 II、抗性提升 II、生命恢复 II
            maid.addEffect(new MobEffectInstance(MobEffects.SATURATION, 300, 1, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 1, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1, false, false));
        } else if (level.dimension() == Level.NETHER) {
            // 下界：力量 III、迅捷 III、急迫 III
            maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 2, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 2, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 2, false, false));
        } else if (level.dimension() == Level.END) {
            // 末地：夜视 IV、抗性提升 IV、伤害提升 IV
            maid.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1360, 3, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 3, false, false));
            maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 3, false, false));
        }
    }

    // ========== 范围女仆强化 ==========
    private void applyRangeMaidBoost(LivingEntity sourceMaid) {
        MinecraftServer server = sourceMaid.getServer();
        if (server == null) {
            return;
        }
        long expiry = PortableTimerMath.saturatingAdd(globalGameTime(server), 40L); // 40 tick 有效期（比 20tick 扫描间隔多 20tick 缓冲）

        // 扫描 20 格内的女仆（排除自身）
        List<EntityMaid> nearbyMaids = sourceMaid.level().getEntitiesOfClass(
                EntityMaid.class,
                sourceMaid.getBoundingBox().inflate(20.0),
                m -> m != sourceMaid && m.isAlive()
        );

        for (EntityMaid nearMaid : nearbyMaids) {
            applyOrRefreshMaidBoost(nearMaid, expiry);
        }
    }

    // ========== 修复背包耐久度 ==========
    private void repairInventory(LivingEntity wearer) {
        IItemHandler handler;
        if (wearer instanceof EntityMaid maid) {
            handler = maid.getAvailableInv(false);
        } else if (wearer instanceof Player player) {
            handler = new InvWrapper(player.getInventory());
        } else {
            return;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.isDamaged()) {
                stack.setDamageValue(stack.getDamageValue() - 1);
            }
        }
        if (wearer instanceof Player && CuriosCompat.isLoaded()) {
            DreamCrystalCurios.repairEquipment(wearer);
        }
    }

    // ========== 正面效果缓存 ==========
    // 缓存所有正面效果，避免每次都遍历注册表
    private static List<Holder.Reference<MobEffect>> CACHED_BENEFICIAL_EFFECTS = null;

    /**
     * 获取缓存的正面效果列表（延迟初始化）
     * 只在第一次调用时构建，后续直接返回缓存
     */
    private static List<Holder.Reference<MobEffect>> getBeneficialEffects() {
        if (CACHED_BENEFICIAL_EFFECTS != null) {
            return CACHED_BENEFICIAL_EFFECTS;
        }

        List<Holder.Reference<MobEffect>> candidates = new ArrayList<>();
        boolean useWhitelist = Config.dreamCrystalUseEffectWhitelist;
        EffectMatcher whitelistMatcher = useWhitelist
                ? EffectMatcher.from(Config.dreamCrystalEffectWhitelist)
                : EffectMatcher.empty();
        EffectMatcher blacklistMatcher = EffectMatcher.from(Config.dreamCrystalEffectBlacklist);

        BuiltInRegistries.MOB_EFFECT.holders().forEach(holder -> {
            MobEffect effect = holder.value();
            if (effect.getCategory() == MobEffectCategory.BENEFICIAL) {
                ResourceLocation location = holder.key().location();
                if (location == null) {
                    return;
                }

                String effectId = location.toString();
                boolean allowedByWhitelist = !useWhitelist || whitelistMatcher.matches(effectId);
                boolean blockedByBlacklist = blacklistMatcher.matches(effectId);
                if (allowedByWhitelist && !blockedByBlacklist) {
                    candidates.add(holder);
                }
            }
        });

        CACHED_BENEFICIAL_EFFECTS = candidates;
        return candidates;
    }

    public static void invalidateBeneficialEffectsCache() {
        CACHED_BENEFICIAL_EFFECTS = null;
    }

    private record EffectMatcher(Set<String> exactMatches, List<Pattern> regexPatterns) {
        private static final String REGEX_PREFIX = "regex:";
        private static final EffectMatcher EMPTY = new EffectMatcher(Collections.emptySet(), Collections.emptyList());

        private static EffectMatcher empty() {
            return EMPTY;
        }

        private static EffectMatcher from(List<String> entries) {
            if (entries == null || entries.isEmpty()) {
                return empty();
            }

            Set<String> exactMatches = new HashSet<>();
            List<Pattern> regexPatterns = new ArrayList<>();

            for (String entry : entries) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }

                if (entry.startsWith(REGEX_PREFIX)) {
                    String regex = entry.substring(REGEX_PREFIX.length());
                    if (regex.isBlank()) {
                        continue;
                    }
                    try {
                        regexPatterns.add(Pattern.compile(regex));
                    } catch (PatternSyntaxException exception) {
                        LOGGER.warn("梦云水晶效果匹配正则无效：{}，已跳过", entry, exception);
                    }
                    continue;
                }

                exactMatches.add(entry);
            }

            if (exactMatches.isEmpty() && regexPatterns.isEmpty()) {
                return empty();
            }
            return new EffectMatcher(exactMatches, regexPatterns);
        }

        private boolean matches(String effectId) {
            if (exactMatches.contains(effectId)) {
                return true;
            }
            for (Pattern pattern : regexPatterns) {
                if (pattern.matcher(effectId).matches()) {
                    return true;
                }
            }
            return false;
        }
    }

    // ========== 随机正面效果 ==========
    public void applyRandomBeneficialEffects(LivingEntity maid) {
        applyRandomBeneficialEffects(maid, 600, 1200);
    }

    public void applyRandomBeneficialEffects(LivingEntity maid, int minDuration, int maxDuration) {
        // 使用缓存列表，避免重复遍历注册表
        List<Holder.Reference<MobEffect>> candidates = getBeneficialEffects();

        if (candidates.isEmpty()) return;

        RandomSource rng = maid.getRandom();
        // 随机选 2 个不重复的正面效果
        Set<Integer> chosen = new HashSet<>();
        int attempts = 0;
        while (chosen.size() < 2 && attempts < 50) {
            chosen.add(rng.nextInt(candidates.size()));
            attempts++;
        }

        for (int index : chosen) {
            Holder.Reference<MobEffect> effectHolder = candidates.get(index);
            int amplifier = 1 + rng.nextInt(9);       // 等级 2-10（amplifier 1-9）
            int duration = minDuration + rng.nextInt(maxDuration - minDuration + 1);
            maid.addEffect(new MobEffectInstance(effectHolder, duration, amplifier, false, true));
        }
    }

    // ========== 工具方法 ==========

    /**
     * 检查女仆是否处于无敌状态
     */
    public static boolean isInvulnerable(EntityMaid maid) {
        return isInvulnerable(maid, findDreamCrystalStack(maid));
    }

    /**
     * 佩戴者身处归隐之地，或在复活后的无敌时间内
     */
    public static boolean isInvulnerable(LivingEntity wearer, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (TheRetreatDimension.isInRetreat(wearer)) {
            return true;
        }
        MinecraftServer server = wearer.getServer();
        if (server == null) {
            return false;
        }
        long now = globalGameTime(server);
        Long until = stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL);
        if (until != null) {
            return now < until;
        }
        return stack.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS, 0) > 0;
    }

    public static ItemStack findDreamCrystalStack(EntityMaid maid) {
        BaubleItemHandler handler = maid.getMaidBauble();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.is(MaidSpellItems.DREAM_CAT_CRYSTAL.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 读取复活时间戳，只保留 120 秒窗口内的记录；记录来自更新的版本时返回 {@code null}，调用方不改动它。
     * <p>
     * 旧版本按各维度自己的时钟记录，无法可靠换算到主世界时钟，直接丢弃。
     */
    @Nullable
    private static ReviveHistory readReviveHistory(ItemStack stack, long serverNow) {
        int storedClockVersion = stack.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION, 0);
        if (storedClockVersion > REVIVE_CLOCK_VERSION) {
            return null;
        }
        List<Long> storedTimestamps = stack.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS, List.of());
        List<Long> normalized = new ArrayList<>(Math.min(storedTimestamps.size(), MAX_REVIVE_TIMESTAMPS));
        long oldestAllowed = PortableTimerMath.saturatingSubtract(serverNow, REVIVE_WINDOW_TICKS);

        List<Long> current = storedClockVersion < REVIVE_CLOCK_VERSION ? List.of() : storedTimestamps;
        for (long storedTimestamp : current) {
            long timestamp = Math.min(storedTimestamp, serverNow);
            if (timestamp >= oldestAllowed) {
                normalized.add(timestamp);
            }
        }

        normalized.sort(Long::compareTo);
        if (normalized.size() > MAX_REVIVE_TIMESTAMPS) {
            normalized = new ArrayList<>(normalized.subList(
                    normalized.size() - MAX_REVIVE_TIMESTAMPS,
                    normalized.size()
            ));
        }

        boolean changed = storedClockVersion != REVIVE_CLOCK_VERSION
                || !storedTimestamps.equals(normalized);
        return new ReviveHistory(normalized, changed);
    }

    private static void normalizeReviveHistory(LivingEntity maid, ItemStack baubleItem) {
        if (!baubleItem.has(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION)
                && !baubleItem.has(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS)) {
            return;
        }
        MinecraftServer server = maid.getServer();
        if (server == null) {
            return;
        }

        ReviveHistory history = readReviveHistory(baubleItem, globalGameTime(server));
        if (history != null && history.changed()) {
            saveReviveHistory(baubleItem, history.timestamps());
        }
    }

    private static void saveReviveHistory(ItemStack stack, List<Long> timestamps) {
        if (stack.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION, 0) != REVIVE_CLOCK_VERSION) {
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION, REVIVE_CLOCK_VERSION);
        }
        if (timestamps.isEmpty()) {
            stack.remove(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS);
            return;
        }
        if (!timestamps.equals(stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS))) {
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS, List.copyOf(timestamps));
        }
    }


    // ========== Curios 槽位 ==========

    /**
     * 为女仆的所有 curios 槽位各增加 1 个额外槽位（transient，需要每 20tick 刷新以维持）
     */
    private void applyCuriosSlots(LivingEntity maid) {
        if (CuriosCompat.isLoaded()) DreamCrystalCurios.setExtraSlots(maid, true);
    }

    // ========== 调度器 ==========

    public static void processScheduledEffects(MinecraftServer server) {
        long currentTime = globalGameTime(server);
        processFrozenTargets(currentTime);
        processBoostedMaids(currentTime);
    }

    public static void clearScheduledEffects() {
        FROZEN_TARGETS.clear();
        FROZEN_TARGET_EXPIRIES.clear();
        BOOSTED_MAIDS.clear();
        BOOSTED_MAID_EXPIRIES.clear();
    }

    /**
     * 时停定身 1 秒。到期时间记在实体数据里，随区块卸载、换维度、停服一起保存；
     * 实体重新进入世界时由 {@link #onFrozenTargetJoin} 接着计时或解除。
     * <p>
     * 配置关闭时不定身；本来就是 NoAI 的生物不打标记，到期也不会被解除。
     */
    public static void freezeTarget(Mob mob) {
        MinecraftServer server = mob.getServer();
        if (!Config.dreamCrystalSetNoAiEnabled || server == null) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (mob.isNoAi() && !data.contains(FROZEN_UNTIL_TAG, Tag.TAG_LONG)) {
            return;
        }
        long expiry = PortableTimerMath.saturatingAdd(globalGameTime(server), FREEZE_TICKS);
        mob.setNoAi(true);
        data.putLong(FROZEN_UNTIL_TAG, Math.max(expiry, data.getLong(FROZEN_UNTIL_TAG)));
        scheduleUnfreeze(mob, expiry);
    }

    private static void scheduleUnfreeze(Mob mob, long expiry) {
        UUID targetUUID = mob.getUUID();
        FrozenTargetState existing = FROZEN_TARGETS.get(targetUUID);
        if (existing != null && existing.expiry >= expiry) {
            existing.target = mob;
            return;
        }
        FROZEN_TARGETS.put(targetUUID, new FrozenTargetState(mob, expiry));
        FROZEN_TARGET_EXPIRIES.add(new ScheduledExpiry(targetUUID, expiry));
    }

    private static void unfreeze(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.contains(FROZEN_UNTIL_TAG, Tag.TAG_LONG)) {
            data.remove(FROZEN_UNTIL_TAG);
            mob.setNoAi(false);
        }
    }

    /**
     * 被时停的生物重新进入世界（区块重新加载、换维度、重启服务器）：已到期就解除 NoAI，否则接着计时。
     */
    public static void onFrozenTargetJoin(Mob mob, MinecraftServer server) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(FROZEN_UNTIL_TAG, Tag.TAG_LONG)) {
            return;
        }
        long expiry = data.getLong(FROZEN_UNTIL_TAG);
        if (globalGameTime(server) >= expiry) {
            unfreeze(mob);
        } else {
            scheduleUnfreeze(mob, expiry);
        }
    }

    private static void processFrozenTargets(long currentTime) {
        while (!FROZEN_TARGET_EXPIRIES.isEmpty()) {
            ScheduledExpiry scheduled = FROZEN_TARGET_EXPIRIES.peek();
            FrozenTargetState state = FROZEN_TARGETS.get(scheduled.entityId());
            if (state == null || state.expiry != scheduled.expiry()) {
                FROZEN_TARGET_EXPIRIES.poll();
                continue;
            }

            Mob target = state.target;
            if (!IAuthoritativeHealth.combatAlive(target)) {
                FROZEN_TARGETS.remove(scheduled.entityId());
                FROZEN_TARGET_EXPIRIES.poll();
                continue;
            }

            if (currentTime < scheduled.expiry()) {
                break;
            }

            FROZEN_TARGETS.remove(scheduled.entityId());
            FROZEN_TARGET_EXPIRIES.poll();
            unfreeze(target);
        }
    }

    private static boolean isMaidBoosted(EntityMaid maid) {
        BoostedMaidState state = BOOSTED_MAIDS.get(maid.getUUID());
        if (state == null) {
            return false;
        }
        MinecraftServer server = maid.getServer();
        if (server == null) {
            return false;
        }
        state.maid = maid;
        return globalGameTime(server) < state.expiry;
    }

    private static void applyOrRefreshMaidBoost(EntityMaid maid, long expiry) {
        AttributeInstance attackDmg = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDmg == null) {
            return;
        }

        if (attackDmg.getModifier(DC_NEARBY_DAMAGE_ID) == null) {
            attackDmg.addTransientModifier(new AttributeModifier(
                    DC_NEARBY_DAMAGE_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }

        UUID maidUUID = maid.getUUID();
        BoostedMaidState existing = BOOSTED_MAIDS.get(maidUUID);
        if (existing != null && existing.expiry >= expiry) {
            existing.maid = maid;
            return;
        }

        BOOSTED_MAIDS.put(maidUUID, new BoostedMaidState(maid, expiry));
        BOOSTED_MAID_EXPIRIES.add(new ScheduledExpiry(maidUUID, expiry));
    }

    private static void processBoostedMaids(long currentTime) {
        while (!BOOSTED_MAID_EXPIRIES.isEmpty()) {
            ScheduledExpiry scheduled = BOOSTED_MAID_EXPIRIES.peek();
            BoostedMaidState state = BOOSTED_MAIDS.get(scheduled.entityId());
            if (state == null || state.expiry != scheduled.expiry()) {
                BOOSTED_MAID_EXPIRIES.poll();
                continue;
            }

            EntityMaid maid = state.maid;
            if (maid == null || !maid.isAlive()) {
                BOOSTED_MAIDS.remove(scheduled.entityId());
                BOOSTED_MAID_EXPIRIES.poll();
                continue;
            }

            if (currentTime < scheduled.expiry()) {
                break;
            }

            removeAttributeModifier(maid, Attributes.ATTACK_DAMAGE, DC_NEARBY_DAMAGE_ID);
            BOOSTED_MAIDS.remove(scheduled.entityId());
            BOOSTED_MAID_EXPIRIES.poll();
        }
    }

    private record ScheduledExpiry(UUID entityId, long expiry) {
    }

    private record ReviveHistory(List<Long> timestamps, boolean changed) {
    }

    private static long globalGameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static final class FrozenTargetState {
        private Mob target;
        private final long expiry;

        private FrozenTargetState(Mob target, long expiry) {
            this.target = target;
            this.expiry = expiry;
        }
    }

    private static final class BoostedMaidState {
        private EntityMaid maid;
        private final long expiry;

        private BoostedMaidState(EntityMaid maid, long expiry) {
            this.maid = maid;
            this.expiry = expiry;
        }
    }

    // ========== 进度触发 ==========

    /**
     * 给女仆主人授予"万法皆通"进度
     */
    private void grantAdvancement(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) return;
        if (maid.getOwnerUUID() == null) return;

        ServerPlayer owner = serverLevel.getServer().getPlayerList()
                .getPlayer(maid.getOwnerUUID());
        if (owner == null) return;

        ResourceLocation advancementId = ResourceLocation.fromNamespaceAndPath(
                MaidSpellMod.MOD_ID, "dream_crystal/all_spells_mastered");

        var advancementHolder = serverLevel.getServer().getAdvancements().get(advancementId);
        if (advancementHolder == null) return;

        var playerAdvancements = owner.getAdvancements();
        if (!playerAdvancements.getOrStartProgress(advancementHolder).isDone()) {
            advancementHolder.value().criteria().keySet().forEach(criterion ->
                    playerAdvancements.award(advancementHolder, criterion));
        }
    }
}
