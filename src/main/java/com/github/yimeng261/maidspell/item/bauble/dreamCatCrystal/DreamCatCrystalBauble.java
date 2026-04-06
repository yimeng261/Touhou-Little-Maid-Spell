package com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 梦云水晶饰品逻辑
 * <p>
 * 主要效果：
 * - 取消法术冷却
 * - 女仆铁魔法法强翻倍（×2，MULTIPLY_TOTAL）
 * - 生命上限 +50%，全伤害抗性 30%，单次伤害上限 40
 * - 免疫：魔法、燃烧、溺水、爆炸、熔岩伤害
 * - 维度特定 Buff（主世界/下界/末地/归隐之地）
 * - 每 30 秒两种随机正面效果
 * - 直接攻击：真实伤害 + 时停 1 秒 + 弹幕溅射 10%
 * - 范围内女仆冷却降至 1/3，伤害 +50%
 * - 每秒修复整个背包物品 1 点耐久
 * - 概率复活（100% - N×10%，N 为 120s 内复活次数）
 * - 复活后 15 秒无敌
 * <p>
 * 组合效果：
 * - + 紫荆银冠：每次受伤直接反伤（不需要 N 次累计，见 LivingEntityMixin）
 * - + 混沌之书：真实伤害百分比翻倍（见 ChaosBookBauble.chaosBookProcess）
 * - + 双心之链：主人不分担伤害，女仆仅受 50%（见 DoubleHeartChainBauble）
 */
public class DreamCatCrystalBauble implements IMaidBauble {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ========== 时停状态追踪 ==========
    private static final Map<UUID, FrozenTargetState> FROZEN_TARGETS = new HashMap<>();
    private static final PriorityQueue<ScheduledExpiry> FROZEN_TARGET_EXPIRIES =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledExpiry::expiry));

    // ========== 范围强化追踪 ==========
    private static final Map<UUID, BoostedMaidState> BOOSTED_MAIDS = new HashMap<>();
    private static final PriorityQueue<ScheduledExpiry> BOOSTED_MAID_EXPIRIES =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledExpiry::expiry));

    // ========== 复活与无敌状态（替代 NBT，使用静态 Map 追踪） ==========
    // 女仆 UUID → 复活时间戳列表
    private static final Map<UUID, List<Long>> REVIVE_TIMESTAMPS = new HashMap<>();
    // 女仆 UUID → 无敌剩余 tick
    public static final Map<UUID, Integer> INVULNERABLE_TICKS = new HashMap<>();

    // ========== 属性修饰符 ResourceLocation ==========
    private static final ResourceLocation DC_HP_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_hp");
    private static final ResourceLocation DC_ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_speed");
    private static final ResourceLocation DC_NEARBY_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_nearby_boost");

    // ========== Curios 槽位修饰符 ==========
    private static final ResourceLocation DC_CURIOS_SLOT_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_slot");

    // ========== ISS 属性列表 ==========
    private static final List<Holder<net.minecraft.world.entity.ai.attributes.Attribute>> ISS_ATTRIBUTES = new ArrayList<>();

    static {
        // 初始化 ISS 属性（如果铁魔法加载）
        if (ModList.get().isLoaded("irons_spellbooks")) {
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
            EntityMaid maid = context.getSourceMaid();
            if (maid == null) {
                return;
            }

            LivingEntity target = context.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }

            float damage = context.getAmount();

            // 1. 真实伤害（额外等于攻击伤害）
            if (Config.dreamCrystalExtraTrueDamageEnabled) {
                TrueDamageUtil.dealTrueDamage(target, damage, maid);
            }

            // 2. 时停 1 秒（仅在服务端执行）
            if (!maid.level().isClientSide() && target.isAlive() && target instanceof Mob mob) {
                long unfreezeTime = maid.level().getGameTime() + 20L;
                freezeTarget(mob, unfreezeTime);
            }

            // 3. 弹幕溅射：对目标周围 5 格内的敌方实体造成 10% 伤害
            if (!maid.level().isClientSide()) {
                float barrageDamage = damage * 0.1f;
                maid.level().getEntitiesOfClass(
                        LivingEntity.class,
                        target.getBoundingBox().inflate(5.0),
                        entity -> entity != maid
                                && entity != target
                                && entity.isAlive()
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

            UUID maidUUID = maid.getUUID();

            // 获取并过滤最近 120 秒（2400 tick）内的复活时间戳
            long currentTime = maid.level().getGameTime();
            List<Long> timestamps = REVIVE_TIMESTAMPS.computeIfAbsent(maidUUID, k -> new ArrayList<>());
            synchronized (timestamps) {
                timestamps.removeIf(t -> currentTime - t > 2400L);

                int n = timestamps.size();
                // 复活概率 = 100% - N×10%
                float reviveChance = Math.max(0.0f, 1.0f - n * 0.1f);
                if (reviveChance <= 0.0f) return null; // N >= 10，无法复活

                if (maid.getRandom().nextFloat() >= reviveChance) return null; // 概率未触发

                // 触发复活
                event.setCanceled(true);

                // 恢复到最大生命值
                float healAmount = maid.getMaxHealth();
                maid.setHealth(healAmount);

                // 记录本次复活时间戳
                timestamps.add(currentTime);

                // 设置 15 秒无敌（300 tick）
                INVULNERABLE_TICKS.put(maidUUID, 300);

                // 播放音效
                maid.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);

                LOGGER.info("女仆 {} 触发梦云水晶概率复活（N={}，概率={}%），恢复至 {} 生命值",
                        maid.getCustomName() != null ? maid.getCustomName().getString() : "未命名",
                        n, (int) (reviveChance * 100), healAmount);
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
        if (maid.level().isClientSide()) return;

        int tick = maid.tickCount;
        UUID maidUUID = maid.getUUID();

        // ========== 每 tick：无敌倒计时 ==========
        handleInvulnerable(maidUUID);

        if (tick % 20 != 3) return;

        // ========== 每 20 tick（约 1 秒）的效果 ==========

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
                AttributeInstance instance = maid.getAttribute(attrHolder);
                if (instance == null) continue;
                instance.removeModifier(issId);
                instance.addTransientModifier(new AttributeModifier(issId, 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        // 4. 维度特定 Buff
        applyDimensionBuffs(maid);

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
        INVULNERABLE_TICKS.remove(maid.getUUID());
        // 卸下时移除 curios 额外槽位
        CuriosApi.getCuriosInventory(maid).ifPresent(handler ->
                handler.getCurios().keySet().forEach(slotType ->
                        handler.removeSlotModifier(slotType, DC_CURIOS_SLOT_ID))
        );

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
    }

    // ========== 无敌倒计时处理 ==========
    private void handleInvulnerable(UUID maidUUID) {
        Integer invulTime = INVULNERABLE_TICKS.get(maidUUID);
        if (invulTime == null) return;

        if (invulTime > 0) {
            INVULNERABLE_TICKS.put(maidUUID, invulTime - 1);
        } else {
            INVULNERABLE_TICKS.remove(maidUUID);
        }
    }

    // ========== 属性修饰符辅助方法 ==========
    private void applyAttributeModifier(EntityMaid maid, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                        ResourceLocation id, double value,
                                        AttributeModifier.Operation operation) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, value, operation));
    }

    private static void removeAttributeModifier(EntityMaid maid, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                                ResourceLocation id) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
    }

    // ========== 维度特定 Buff ==========
    private void applyDimensionBuffs(EntityMaid maid) {
        UUID maidUUID = maid.getUUID();

        // 归隐之地：无敌
        if (TheRetreatDimension.isInRetreat(maid)) {
            // 归隐之地期间持续刷新无敌计时（2 秒窗口，每 20tick 刷新）
            INVULNERABLE_TICKS.put(maidUUID, 40); // 维持 2 秒，每 20tick 刷新
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
    private void applyRangeMaidBoost(EntityMaid sourceMaid) {
        long expiry = sourceMaid.level().getGameTime() + 40L; // 40 tick 有效期（比 20tick 扫描间隔多 20tick 缓冲）

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
    private void repairInventory(EntityMaid maid) {
        var handler = maid.getAvailableInv(false);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.isDamaged()) {
                stack.setDamageValue(stack.getDamageValue() - 1);
            }
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
    private void applyRandomBeneficialEffects(EntityMaid maid) {
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
            int duration = 600 + rng.nextInt(601);    // 30-60 秒（600-1200 tick）
            maid.addEffect(new MobEffectInstance(effectHolder, duration, amplifier, false, true));
        }
    }

    // ========== 工具方法 ==========

    /**
     * 检查女仆是否处于无敌状态
     */
    public static boolean isInvulnerable(UUID maidUUID) {
        Integer ticks = INVULNERABLE_TICKS.get(maidUUID);
        return ticks != null && ticks > 0;
    }

    // ========== Curios 槽位 ==========

    /**
     * 为女仆的所有 curios 槽位各增加 1 个额外槽位（transient，需要每 20tick 刷新以维持）
     */
    private void applyCuriosSlots(EntityMaid maid) {
        CuriosApi.getCuriosInventory(maid).ifPresent(handler -> {
            handler.getCurios().keySet().forEach(slotType -> {
                handler.removeSlotModifier(slotType, DC_CURIOS_SLOT_ID);
                handler.addTransientSlotModifier(slotType, DC_CURIOS_SLOT_ID,
                        1.0, AttributeModifier.Operation.ADD_VALUE);
            });
        });
    }

    // ========== 调度器 ==========

    public static void processScheduledEffects() {
        processFrozenTargets();
        processBoostedMaids();
    }

    public static void clearScheduledEffects() {
        FROZEN_TARGETS.clear();
        FROZEN_TARGET_EXPIRIES.clear();
        BOOSTED_MAIDS.clear();
        BOOSTED_MAID_EXPIRIES.clear();
        REVIVE_TIMESTAMPS.clear();
        INVULNERABLE_TICKS.clear();
    }

    private static void freezeTarget(Mob mob, long expiry) {
        UUID targetUUID = mob.getUUID();
        FrozenTargetState existing = FROZEN_TARGETS.get(targetUUID);
        if (existing != null && existing.expiry >= expiry) {
            existing.target = mob;
            if (Config.dreamCrystalSetNoAiEnabled) {
                mob.setNoAi(true);
            }
            return;
        }

        FrozenTargetState state = new FrozenTargetState(mob, expiry);
        FROZEN_TARGETS.put(targetUUID, state);
        FROZEN_TARGET_EXPIRIES.add(new ScheduledExpiry(targetUUID, expiry));
        if (Config.dreamCrystalSetNoAiEnabled) {
            mob.setNoAi(true);
        }
    }

    private static void processFrozenTargets() {
        while (!FROZEN_TARGET_EXPIRIES.isEmpty()) {
            ScheduledExpiry scheduled = FROZEN_TARGET_EXPIRIES.peek();
            FrozenTargetState state = FROZEN_TARGETS.get(scheduled.entityId());
            if (state == null || state.expiry != scheduled.expiry()) {
                FROZEN_TARGET_EXPIRIES.poll();
                continue;
            }

            Mob target = state.target;
            if (target == null || !target.isAlive()) {
                FROZEN_TARGETS.remove(scheduled.entityId());
                FROZEN_TARGET_EXPIRIES.poll();
                continue;
            }

            if (target.level().getGameTime() < scheduled.expiry()) {
                break;
            }

            FROZEN_TARGETS.remove(scheduled.entityId());
            FROZEN_TARGET_EXPIRIES.poll();
            if (Config.dreamCrystalSetNoAiEnabled) {
                target.setNoAi(false);
            }
        }
    }

    private static boolean isMaidBoosted(EntityMaid maid) {
        BoostedMaidState state = BOOSTED_MAIDS.get(maid.getUUID());
        if (state == null) {
            return false;
        }
        state.maid = maid;
        return maid.level().getGameTime() < state.expiry;
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

    private static void processBoostedMaids() {
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

            if (maid.level().getGameTime() < scheduled.expiry()) {
                break;
            }

            removeAttributeModifier(maid, Attributes.ATTACK_DAMAGE, DC_NEARBY_DAMAGE_ID);
            BOOSTED_MAIDS.remove(scheduled.entityId());
            BOOSTED_MAID_EXPIRIES.poll();
        }
    }

    private record ScheduledExpiry(UUID entityId, long expiry) {
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
