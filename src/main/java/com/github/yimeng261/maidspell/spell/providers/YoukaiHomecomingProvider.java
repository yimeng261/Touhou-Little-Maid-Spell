package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidYHSpellData;
import com.github.yimeng261.maidspell.spell.helpers.SpellCardHelper;
import com.github.yimeng261.maidspell.spell.holders.MaidCardHolder;
import com.mojang.logging.LogUtils;
import dev.xkmc.danmakuapi.content.entity.ItemBulletEntity;
import dev.xkmc.danmakuapi.content.entity.ItemLaserEntity;
import dev.xkmc.danmakuapi.content.item.DanmakuItem;
import dev.xkmc.danmakuapi.content.item.LaserItem;
import dev.xkmc.danmakuapi.content.spell.spellcard.SpellCardWrapper;
import dev.xkmc.danmakuapi.init.registrate.DanmakuEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 妖怪的归家弹幕物品提供者
 * 支持女仆使用弹幕、激光和符卡；弹幕与符卡来自 1.21 非官方移植版内嵌的 danmaku_api（官方 1.21 版没有弹幕）
 */
public class YoukaiHomecomingProvider extends ISpellBookProvider<MaidYHSpellData, ItemStack> {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 随机发射配置
    private static final int MIN_DANMAKU_COUNT = 1;  // 最少发射数量
    private static final int MAX_DANMAKU_COUNT = 10;  // 最多发射数量
    private static final float FAN_ANGLE = 45.0f;    // 扇形角度（度）

    // 符卡配置
    private static final double SPELL_CARD_ACTIVATION_CHANCE = 0.5;

    /**
     * 弹幕物品本身就是"法术"，同时支持符卡系统
     */
    public YoukaiHomecomingProvider() {
        super(MaidYHSpellData::getOrCreate, MaidYHSpellData::get,
                MaidYHSpellData::remove, MaidYHSpellData::clearAll, ItemStack.class);
    }

    // === ISpellBookProvider 接口实现 ===

    @Override
    protected List<ItemStack> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        return List.of(spellBook);
    }

    /**
     * 支持：DanmakuItem（弹幕）、LaserItem（激光）、有符卡映射的 SpellItem（符卡）
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        Item item = itemStack.getItem();
        return item instanceof DanmakuItem
                || item instanceof LaserItem
                || SpellCardHelper.isSpellCardItem(itemStack);
    }

    /**
     * 开始施法 - 随机选择弹幕进行扇形发射，或激活符卡
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);

        List<ItemStack> allDanmakuItems = data.getSpellBooks();
        if (allDanmakuItems.isEmpty()) {
            return;
        }

        // 有一定概率从已有的符卡物品中激活符卡
        if (!data.hasActiveSpellCard() && Math.random() < SPELL_CARD_ACTIVATION_CHANCE) {
            activateSpellCardFromInventory(maid, data, allDanmakuItems);
            return;
        }

        if (!data.hasActiveSpellCard()) {
            performRandomFanShooting(maid, allDanmakuItems);
        }
    }

    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);
        if (data == null || !data.isCasting()) {
            return;
        }

        data.updateCasting();
        if (data.hasActiveSpellCard()) {
            tickSpellCard(maid, data);
            data.cleanupActiveProjectiles();
        }

        if (data.isSpellComplete()) {
            completeCasting(maid, data);
        }
    }

    @Override
    public void stopCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);
        if (data != null && data.isCasting()) {
            LOGGER.debug("[MaidSpell/YH] stop casting maid={} tick={} activeCard={} target={}",
                    maid.getId(), maid.tickCount, getActiveSpellCardId(data), describeTarget(data.getTarget()));
            data.resetCastingState();
            data.deactivateSpellCard();
        }
    }

    /**
     * 执行随机扇形发射
     */
    private void performRandomFanShooting(EntityMaid maid, List<ItemStack> allDanmakuItems) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }
        MaidYHSpellData data = getData(maid);
        Random random = new Random();
        int shotCount = random.nextInt(MIN_DANMAKU_COUNT, MAX_DANMAKU_COUNT + 1);
        Vec3 baseDirection = calculateShootDirection(maid, data);
        List<ItemStack> availableItem = new ArrayList<>(allDanmakuItems);
        availableItem.removeIf(SpellCardHelper::isSpellCardItem);
        if (availableItem.isEmpty()) {
            return;
        }
        ItemStack selectedItem = availableItem.get(random.nextInt(availableItem.size()));

        for (int i = 0; i < shotCount; i++) {
            // 计算扇形发射角度
            float angleOffset = 0;
            if (shotCount > 1) {
                angleOffset = (FAN_ANGLE / (shotCount - 1)) * i - (FAN_ANGLE / 2);
            }
            Vec3 shootDirection = calculateFanDirection(baseDirection, angleOffset);
            fireDanmakuByType(maid, selectedItem, shootDirection);
        }
    }

    /**
     * 计算射击方向
     */
    private Vec3 calculateShootDirection(EntityMaid maid, MaidYHSpellData data) {
        if (data.isValidTarget()) {
            LivingEntity target = data.getTarget();
            Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
            Vec3 maidPos = maid.position().add(0, maid.getBbHeight() * 0.8, 0);
            return targetPos.subtract(maidPos).normalize();
        }
        // 使用女仆当前朝向
        return Vec3.directionFromRotation(maid.getXRot(), maid.getYRot());
    }

    /**
     * 计算扇形发射方向：在水平面上绕Y轴旋转
     */
    private Vec3 calculateFanDirection(Vec3 baseDirection, float angleOffsetDegrees) {
        if (Math.abs(angleOffsetDegrees) < 0.1f) {
            return baseDirection;
        }
        double angleRad = Math.toRadians(angleOffsetDegrees);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double newX = baseDirection.x * cos - baseDirection.z * sin;
        double newZ = baseDirection.x * sin + baseDirection.z * cos;
        return new Vec3(newX, baseDirection.y, newZ).normalize();
    }

    /**
     * 根据物品类型发射弹幕
     */
    private void fireDanmakuByType(EntityMaid maid, ItemStack item, Vec3 direction) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (item.getItem() instanceof DanmakuItem danmakuItem) {
            ItemBulletEntity danmaku = new ItemBulletEntity(DanmakuEntities.ITEM_DANMAKU.get(), maid, serverLevel);
            danmaku.setItem(item);
            danmaku.setup(danmakuItem.type.damage(), 80, false, danmakuItem.type.bypass(), direction.scale(2.0));
            serverLevel.addFreshEntity(danmaku);
        } else if (item.getItem() instanceof LaserItem laserItem) {
            ItemLaserEntity laser = new ItemLaserEntity(DanmakuEntities.ITEM_LASER.get(), maid, serverLevel);
            laser.setItem(item);
            laser.setup(laserItem.type.damage(), 60, 32, false, direction.scale(1.5));
            laser.setPos(maid.position().add(0, maid.getBbHeight() * 0.8, 0));
            serverLevel.addFreshEntity(laser);
        }
    }

    /**
     * 完成施法：符卡自然结束时触发冷却
     */
    private void completeCasting(EntityMaid maid, MaidYHSpellData data) {
        if (data.getActiveSpellCard() != null) {
            LOGGER.debug("[MaidSpell/YH] complete spell card maid={} tick={} card={}",
                    maid.getId(), maid.tickCount, getActiveSpellCardId(data));
            data.setSpellCooldown(data.getActiveSpellCard().modelId, 1200, maid);
            data.deactivateSpellCard();
        }
        data.resetCastingState();
    }

    private static String getActiveSpellCardId(MaidYHSpellData data) {
        return data != null && data.getActiveSpellCard() != null ? data.getActiveSpellCard().modelId : "null";
    }

    private static String describeTarget(LivingEntity target) {
        if (target == null) {
            return "null";
        }
        return target.getType() + "#" + target.getId() + " alive=" + target.isAlive()
                + " dying=" + target.isDeadOrDying() + " removed=" + target.isRemoved()
                + " hp=" + target.getHealth() + "/" + target.getMaxHealth();
    }

    // === 符卡系统方法 ===

    /**
     * 从背包中的符卡物品随机激活一张不在冷却中的符卡
     */
    private void activateSpellCardFromInventory(EntityMaid maid, MaidYHSpellData data, List<ItemStack> allItems) {
        List<ItemStack> spellCardItems = new ArrayList<>();
        for (ItemStack item : allItems) {
            if (SpellCardHelper.isSpellCardItem(item)) {
                spellCardItems.add(item);
            }
        }
        if (spellCardItems.isEmpty()) {
            return;
        }

        Collections.shuffle(spellCardItems, new Random(maid.getRandom().nextLong()));
        for (ItemStack selectedItem : spellCardItems) {
            SpellCardWrapper wrapper = SpellCardHelper.createSpellCardFromItem(selectedItem);
            if (wrapper == null || data.isSpellOnCooldown(wrapper.modelId)) {
                continue;
            }
            data.activateSpellCard(wrapper);
            LOGGER.debug("[MaidSpell/YH] activate spell card maid={} tick={} item={} card={} target={}",
                    maid.getId(), maid.tickCount, BuiltInRegistries.ITEM.getKey(selectedItem.getItem()),
                    wrapper.modelId, describeTarget(data.getTarget()));
            return;
        }
    }

    /**
     * 符卡Tick处理；无目标符卡会回退到女仆朝向
     */
    private void tickSpellCard(EntityMaid maid, MaidYHSpellData data) {
        SpellCardWrapper spellCard = data.getActiveSpellCard();
        if (spellCard == null || spellCard.card == null) {
            return;
        }
        MaidCardHolder holder = new MaidCardHolder(maid, data.getTarget(), data.getActiveProjectiles());
        holder.updateCachedProjectiles();
        spellCard.tick(holder);
    }
}
