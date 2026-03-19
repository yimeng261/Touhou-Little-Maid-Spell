package com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.utils.TrueDamageUtil;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.*;

/**
 * 梦云水晶饰品逻辑
 *
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
 *
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

    // ========== NBT 键名 ==========
    private static final String NBT_REVIVE_TIMESTAMPS = "dream_crystal_revive_timestamps";
    public static final String NBT_INVULNERABLE_TIME = "dream_crystal_invulnerable_time";

    // ========== 正面效果缓存 ==========
    // 缓存所有正面效果，避免每次都遍历注册表
    private static List<MobEffect> CACHED_BENEFICIAL_EFFECTS = null;

    // ========== 加速梦云水晶查找 ==========
    private static final Map<UUID, ItemStack> HAS_DREAM_CRYSTAL = new HashMap<>();

    /**
     * 获取缓存的正面效果列表（延迟初始化）
     * 只在第一次调用时构建，后续直接返回缓存
     */
    private static List<MobEffect> getBeneficialEffects() {
        if (CACHED_BENEFICIAL_EFFECTS != null) {
            return CACHED_BENEFICIAL_EFFECTS;
        }

        List<MobEffect> candidates = new ArrayList<>();
        Set<String> blacklist = new HashSet<>(Config.dreamCrystalEffectBlacklist);

        BuiltInRegistries.MOB_EFFECT.entrySet().forEach(entry -> {
            MobEffect effect = entry.getValue();
            if (effect.getCategory() == MobEffectCategory.BENEFICIAL) {
                ResourceLocation location = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (location != null && !blacklist.contains(location.toString())) {
                    candidates.add(effect);
                }
            }
        });

        CACHED_BENEFICIAL_EFFECTS = candidates;
        return candidates;
    }

    // ========== 属性修饰符 UUID ==========
    private static final UUID DC_HP_UUID = UUID.fromString("dc000001-0000-0000-0000-000000000001");
    private static final UUID DC_ATTACK_SPEED_UUID = UUID.fromString("dc000001-0000-0000-0000-000000000002");
    private static final UUID DC_NEARBY_DAMAGE_UUID = UUID.fromString("dc000001-0000-0000-0000-000000000003");

    // ========== Curios 槽位修饰符 UUID ==========
    private static final UUID DC_CURIOS_SLOT_UUID = UUID.fromString("dc000001-0000-0000-0000-000000000004");

    // ========== 进度 ResourceLocation ==========
    @SuppressWarnings("removal")
    private static final ResourceLocation ADVANCEMENT_ALL_SPELLS =
        new ResourceLocation(MaidSpellMod.MOD_ID + ":dream_crystal/all_spells_mastered");

    // ========== ISS 属性列表 ==========
    private static final List<Attribute> ISS_ATTRIBUTES = new ArrayList<>();

    static {
        // 初始化 ISS 属性（如果铁魔法加载）
        if (ModList.get().isLoaded("irons_spellbooks")) {
            ForgeRegistries.ATTRIBUTES.forEach(attr -> {
                if (attr.getDescriptionId().startsWith("attribute.irons_spellbooks.")) {
                    ISS_ATTRIBUTES.add(attr);
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
            TrueDamageUtil.dealTrueDamage(target, damage, maid);

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
                        && !(entity instanceof Player)
                        && !(entity instanceof EntityMaid)
                ).forEach(nearby -> TrueDamageUtil.dealTrueDamage(nearby, barrageDamage, maid));
            }

        });

        // ========== 有害效果双重免疫过滤器（与 MobEffectMixin + LivingEntityMixin 配合） ==========
        // 返回 true → 阻止效果写入 activeEffects（不 tick/不显示）且阻止其属性修改器被应用
        Global.baubleEffectBlockFilters.put(MaidSpellItems.DREAM_CAT_CRYSTAL.get(),
            (maid, effect) -> effect.getCategory() != MobEffectCategory.BENEFICIAL);

        // ========== 死亡概率复活 ==========
        Global.baubleDeathHandlers.put(MaidSpellItems.DREAM_CAT_CRYSTAL.get(), (event, maid) -> {
            if (event.isCanceled()) return null;

            ItemStack baubleStack = findDreamCrystalStack(maid);
            if (baubleStack == null) return null;

            // 获取并过滤最近 120 秒（2400 tick）内的复活时间戳
            CompoundTag tag = baubleStack.getOrCreateTag();
            long currentTime = maid.level().getGameTime();
            List<Long> timestamps = getReviveTimestamps(tag);
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
            saveReviveTimestamps(tag, timestamps);

            // 设置 15 秒无敌（300 tick）
            tag.putInt(NBT_INVULNERABLE_TIME, 300);

            // 播放音效
            maid.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);

            LOGGER.info("女仆 {} 触发梦云水晶概率复活（N={}，概率={}%），恢复至 {} 生命值",
                maid.getCustomName() != null ? maid.getCustomName().getString() : "未命名",
                n, (int)(reviveChance * 100), healAmount);

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

        // ========== 每 tick：无敌倒计时 ==========
        handleInvulnerable(maid, baubleItem);

        if (tick % 20 != 3) return;

        // ========== 每 20 tick（约 1 秒）的效果 ==========

        // 1. 血量上限 +50%
        applyAttributeModifier(maid, Attributes.MAX_HEALTH, DC_HP_UUID,
            "dream_crystal_hp", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);

        // 2. 攻击速度 ×2
        applyAttributeModifier(maid, Attributes.ATTACK_SPEED, DC_ATTACK_SPEED_UUID,
            "dream_crystal_speed", 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

        // 3. ISS 法强翻倍（如果铁魔法已加载）
        if (!ISS_ATTRIBUTES.isEmpty()) {
            for (Attribute attr : ISS_ATTRIBUTES) {
                UUID uuid = new UUID("dream_crystal_iss".hashCode(),
                    attr.getDescriptionId().hashCode());
                applyAttributeModifier(maid, attr, uuid, "dream_crystal_iss", 1.0,
                    AttributeModifier.Operation.MULTIPLY_TOTAL);
            }
        }

        // 4. 维度特定 Buff
        applyDimensionBuffs(maid);

        // 5. 扫描范围内女仆并施加强化
        applyRangeMaidBoost(maid);

        // 6. 修复整个背包物品耐久度（每 20tick = 每秒 1 点）
        repairInventory(maid);

        // 7. 维持 curios 额外槽位（transient modifier 需要周期性刷新）
        applyCuriosSlot(maid);

        // ========== 每 600 tick（30 秒）随机正面效果 ==========
        if (tick % 600 == 2) {
            applyRandomBeneficialEffects(maid);
        }
    }

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        HAS_DREAM_CRYSTAL.put(maid.getUUID(), baubleItem);
        if (maid.level().isClientSide()) return;

        applyCuriosSlot(maid);
        // 触发"万法皆通"进度
        grantAdvancement(maid);
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        HAS_DREAM_CRYSTAL.remove(maid.getUUID(), baubleItem);
        if (maid.level().isClientSide()) return;
        // 卸下时移除 curios 额外槽位
        CuriosApi.getCuriosInventory(maid).ifPresent(handler ->
            removeCuriosSlots(handler)
        );

        removeAttributeModifier(maid, Attributes.MAX_HEALTH, DC_HP_UUID);
        removeAttributeModifier(maid, Attributes.ATTACK_SPEED, DC_ATTACK_SPEED_UUID);
        if (!ISS_ATTRIBUTES.isEmpty()) {
            for (Attribute attr : ISS_ATTRIBUTES) {
                removeAttributeModifier(maid, attr, new UUID("dream_crystal_iss".hashCode(),
                    attr.getDescriptionId().hashCode()));
            }
        }
    }

    // ========== 无敌倒计时处理 ==========
    private void handleInvulnerable(EntityMaid maid, ItemStack baubleItem) {
        CompoundTag tag = baubleItem.getOrCreateTag();
        if (!tag.contains(NBT_INVULNERABLE_TIME)) return;

        int invulTime = tag.getInt(NBT_INVULNERABLE_TIME);
        if (invulTime > 0) {
            tag.putInt(NBT_INVULNERABLE_TIME, invulTime - 1);
        } else {
            tag.remove(NBT_INVULNERABLE_TIME);
        }
    }

    // ========== 属性修饰符辅助方法 ==========
    private void applyAttributeModifier(EntityMaid maid, Attribute attribute, UUID uuid,
                                        String name, double value,
                                        AttributeModifier.Operation operation) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier modifier = new AttributeModifier(uuid, name, value, operation);
        instance.removeModifier(uuid);
        instance.addTransientModifier(modifier);
    }

    private static void removeAttributeModifier(EntityMaid maid, Attribute attribute, UUID uuid) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(uuid);
    }

    // ========== 维度特定 Buff ==========
    private void applyDimensionBuffs(EntityMaid maid) {
        // 归隐之地：无敌（通过 NBT 机制在 DreamCrystalMaidEvents 中处理）
        if (TheRetreatDimension.isInRetreat(maid)) {
            // 归隐之地期间持续刷新无敌计时（2 秒窗口，每 20tick 刷新）
            ItemStack baubleStack = findDreamCrystalStack(maid);
            if (baubleStack != null) {
                CompoundTag tag = baubleStack.getOrCreateTag();
                tag.putInt(NBT_INVULNERABLE_TIME, 40); // 维持 2 秒，每 20tick 刷新
            }
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
        CombinedInvWrapper handler = maid.getAvailableInv(false);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.isDamaged()) {
                stack.setDamageValue(stack.getDamageValue() - 1);
            }
        }
    }

    // ========== 随机正面效果 ==========
    private void applyRandomBeneficialEffects(EntityMaid maid) {
        // 使用缓存列表，避免重复遍历注册表
        List<MobEffect> candidates = getBeneficialEffects();

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
            MobEffect effect = candidates.get(index);
            int amplifier = 1 + rng.nextInt(9);       // 等级 2-10（amplifier 1-9）
            int duration = 600 + rng.nextInt(601);    // 30-60 秒（600-1200 tick）
            maid.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
        }
    }

    // ========== 工具方法 ==========

    /**
     * 在女仆饰品背包中查找梦云水晶的 ItemStack
     */
    public static ItemStack findDreamCrystalStack(EntityMaid maid) {
        ItemStack result = HAS_DREAM_CRYSTAL.getOrDefault(maid.getUUID(), null);
        if(result == null && BaubleStateManager.hasBauble(maid,MaidSpellItems.DREAM_CAT_CRYSTAL)) {
            BaubleItemHandler handler = maid.getMaidBauble();
            for(int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if(stack.getItem() == MaidSpellItems.DREAM_CAT_CRYSTAL.get()){
                    result = stack;
                    HAS_DREAM_CRYSTAL.put(maid.getUUID(), stack);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 从 NBT 读取复活时间戳列表
     */
    private static List<Long> getReviveTimestamps(CompoundTag tag) {
        List<Long> result = new ArrayList<>();
        if (tag.contains(NBT_REVIVE_TIMESTAMPS)) {
            long[] arr = tag.getLongArray(NBT_REVIVE_TIMESTAMPS);
            for (long t : arr) result.add(t);
        }
        return result;
    }

    /**
     * 将复活时间戳列表写入 NBT
     */
    private static void saveReviveTimestamps(CompoundTag tag, List<Long> timestamps) {
        long[] arr = new long[timestamps.size()];
        for (int i = 0; i < timestamps.size(); i++) arr[i] = timestamps.get(i);
        tag.putLongArray(NBT_REVIVE_TIMESTAMPS, arr);
    }

    // ========== Curios 槽位 ==========

    /**
     * 为女仆当前已有的每一种 curios 槽位类型增加 1 格（transient，需要周期性刷新）
     */
    private void applyCuriosSlot(EntityMaid maid) {
        CuriosApi.getCuriosInventory(maid).ifPresent(this::applyCuriosSlots);
    }

    private void applyCuriosSlots(ICuriosItemHandler handler) {
        for (String slotType : handler.getCurios().keySet()) {
            handler.removeSlotModifier(slotType, DC_CURIOS_SLOT_UUID);
            handler.addTransientSlotModifier(slotType, DC_CURIOS_SLOT_UUID,
                "dream_crystal_slot", 1.0, AttributeModifier.Operation.ADDITION);
        }
    }

    private void removeCuriosSlots(ICuriosItemHandler handler) {
        for (String slotType : handler.getCurios().keySet()) {
            handler.removeSlotModifier(slotType, DC_CURIOS_SLOT_UUID);
        }
    }

    // ========== 调度器 ==========

    public static void processScheduledEffects(MinecraftServer server) {
        if (server == null) {
            return;
        }
        processFrozenTargets();
        processBoostedMaids();
    }

    public static void clearScheduledEffects() {
        FROZEN_TARGETS.clear();
        FROZEN_TARGET_EXPIRIES.clear();
        BOOSTED_MAIDS.clear();
        BOOSTED_MAID_EXPIRIES.clear();
        HAS_DREAM_CRYSTAL.clear();
    }

    private static void freezeTarget(Mob mob, long expiry) {
        UUID targetUUID = mob.getUUID();
        FrozenTargetState existing = FROZEN_TARGETS.get(targetUUID);
        if (existing != null && existing.expiry >= expiry) {
            existing.target = mob;
            mob.setNoAi(true);
            return;
        }

        FrozenTargetState state = new FrozenTargetState(mob, expiry);
        FROZEN_TARGETS.put(targetUUID, state);
        FROZEN_TARGET_EXPIRIES.add(new ScheduledExpiry(targetUUID, expiry));
        mob.setNoAi(true);
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
            target.setNoAi(false);
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

        if (attackDmg.getModifier(DC_NEARBY_DAMAGE_UUID) == null) {
            attackDmg.addTransientModifier(new AttributeModifier(
                DC_NEARBY_DAMAGE_UUID,
                "dream_crystal_nearby_boost",
                0.5,
                AttributeModifier.Operation.MULTIPLY_TOTAL
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

            removeAttributeModifier(maid, Attributes.ATTACK_DAMAGE, DC_NEARBY_DAMAGE_UUID);
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

        ServerAdvancementManager manager = serverLevel.getServer().getAdvancements();
        Advancement advancement = manager.getAdvancement(ADVANCEMENT_ALL_SPELLS);
        if (advancement == null) return;

        PlayerAdvancements playerAdvancements = owner.getAdvancements();
        if (!playerAdvancements.getOrStartProgress(advancement).isDone()) {
            advancement.getCriteria().keySet().forEach(criterion ->
                playerAdvancements.award(advancement, criterion));
        }
    }
}
