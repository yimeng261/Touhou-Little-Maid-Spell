package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidYHSpellData;
import com.github.yimeng261.maidspell.spell.holders.MaidCardHolder;
import com.github.yimeng261.maidspell.spell.helpers.SpellCardHelper;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.LaserItem;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCardWrapper;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;

/**
 * Youkai-Homecoming弹幕物品提供者
 * 支持女仆使用弹幕物品、法术卡、激光武器和符卡系统
 */
public class YoukaiHomecomingProvider extends ISpellBookProvider<MaidYHSpellData, ItemStack> {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 随机发射配置
    private static final int MIN_DANMAKU_COUNT = 1;  // 最少发射数量
    private static final int MAX_DANMAKU_COUNT = 10;  // 最多发射数量
    private static final float FAN_ANGLE = 45.0f;    // 扇形角度（度）
    
    // 符卡配置
    private static final double SPELL_CARD_ACTIVATION_CHANCE = 0.5;

    private static final HashSet<Class<?>> DANMAKU_CLASSES = new HashSet<>(Arrays.asList(DanmakuItem.class,LaserItem.class));
    
    /**
     * 构造函数，绑定 MaidYHSpellData 数据类型和 ItemStack 法术类型
     * 注：对于Youkai-Homecoming，弹幕物品本身就是"法术"，同时支持符卡系统
     */
    public YoukaiHomecomingProvider() {
        super(MaidYHSpellData::getOrCreate, ItemStack.class);
        LOGGER.info("YoukaiHomecomingProvider initialized with SpellCard support");
    }
    
    // === ISpellBookProvider 接口实现 ===
    
    /**
     * 从单个弹幕物品中收集"法术"
     * 对于Youkai-Homecoming，弹幕物品本身就是法术，所以返回包含该物品的列表
     * @param spellBook 弹幕物品堆栈
     * @return 包含该物品的列表
     */
    @Override
    protected List<ItemStack> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        return List.of(spellBook);
    }
    
    /**
     * 检查物品是否为Youkai-Homecoming的弹幕物品
     * 支持：DanmakuItem（弹幕）、SpellItem（法术卡）、LaserItem（激光）
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        return DANMAKU_CLASSES.contains(itemStack.getItem().getClass()) || SpellCardHelper.isSpellCardItem(itemStack);
    }

    
    /**
     * 开始施法 - 随机选择多种弹幕进行扇形发射，或激活符卡
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);

        Set<ItemStack> allDanmakuItems = data.getSpellBooks();
        //LOGGER.debug("Initiating casting for {} items", allDanmakuItems.size());
        if (allDanmakuItems.isEmpty()) {
            return;
        }
        
        // 检查是否应该激活符卡（有一定概率）
        // 从已有的符卡物品中选择
        if (!data.hasActiveSpellCard() && Math.random() < SPELL_CARD_ACTIVATION_CHANCE) {
            activateSpellCardFromInventory(maid, data, allDanmakuItems);
            return;
        }

        if(!data.hasActiveSpellCard()){
            performRandomFanShooting(maid, allDanmakuItems);
        }

    }
    
    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);
        if (data == null || !data.isCasting()) {
            return;
        }

        data.updateCasting();
        // 处理符卡逻辑
        if (data.hasActiveSpellCard()) {
            //LOGGER.debug("Active spell card: {}", data.getSpellBooks());
            tickSpellCard(maid, data);
        }
        
        // 检查是否完成施法
        if (data.isSpellComplete()) {
            completeCasting(maid, data);
        }
    }
    
    /**
     * 停止施法
     */
    @Override
    public void stopCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);
        if (data != null && data.isCasting()) {
            data.resetCastingState();
            data.deactivateSpellCard();
        }
    }
    
    /**
     * 更新冷却时间
     */
    @Override
    public void updateCooldown(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);
        if (data != null) {
            data.updateCooldowns();
        }
    }
    
    /**
     * 执行随机扇形发射
     */
    private void performRandomFanShooting(EntityMaid maid, Set<ItemStack> allDanmakuItems) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }
        MaidYHSpellData data = getData(maid);
        Random random = new Random();
        // 随机选择发射数量
        int shotCount = random.nextInt(MIN_DANMAKU_COUNT, MAX_DANMAKU_COUNT + 1);
        Vec3 baseDirection = calculateShootDirection(maid, data);
        ItemStack selectedItem;
        List<ItemStack> availableItem = new ArrayList<>(allDanmakuItems.stream().toList());
        availableItem.removeIf(SpellCardHelper::isSpellCardItem);

        if(availableItem.isEmpty()) {
            LOGGER.debug("No danmaku available");
            return;
        }

        do{
            selectedItem = availableItem.get(random.nextInt(availableItem.size()));
        }while(SpellCardHelper.isSpellCardItem(selectedItem));

        // 发射多个弹幕
        for (int i = 0; i < shotCount; i++) {

            // 计算扇形发射角度
            float angleOffset = 0;
            if (shotCount > 1) {
                angleOffset = (FAN_ANGLE / (shotCount - 1)) * i - (FAN_ANGLE / 2);
            }
            
            // 计算发射方向
            Vec3 shootDirection = calculateFanDirection(baseDirection, angleOffset);
            fireDanmakuByType(maid, selectedItem, shootDirection);

        }

    }
    
    /**
     * 计算射击方向
     */
    private Vec3 calculateShootDirection(EntityMaid maid, MaidYHSpellData data) {
        if (data.isValidTarget()) {
            // 瞄准目标
            LivingEntity target = data.getTarget();
            Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
            Vec3 maidPos = maid.position().add(0, maid.getBbHeight() * 0.8, 0);
            return targetPos.subtract(maidPos).normalize();
        } else {
            // 使用女仆当前朝向
            return RayTraceUtil.getRayTerm(Vec3.ZERO, maid.getXRot(), maid.getYRot(), 1);
        }
    }
    
    /**
     * 计算扇形发射方向
     */
    private Vec3 calculateFanDirection(Vec3 baseDirection, float angleOffsetDegrees) {
        if (Math.abs(angleOffsetDegrees) < 0.1f) {
            return baseDirection;
        }
        
        // 将角度转换为弧度
        double angleRad = Math.toRadians(angleOffsetDegrees);
        
        // 计算旋转后的方向向量
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        
        // 在水平面上旋转（绕Y轴旋转）
        double newX = baseDirection.x * cos - baseDirection.z * sin;
        double newZ = baseDirection.x * sin + baseDirection.z * cos;
        
        return new Vec3(newX, baseDirection.y, newZ).normalize();
    }
    
    /**
     * 根据物品类型发射弹幕
     */
    private void fireDanmakuByType(EntityMaid maid, ItemStack item, Vec3 direction) {
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }
        
        if (item.getItem() instanceof DanmakuItem danmakuItem) {
            fireDanmakuWithDirection(maid, danmakuItem, item, direction);
        } else if (item.getItem() instanceof LaserItem laserItem) {
            fireLaserWithDirection(maid, laserItem, item, direction);
        }

    }
    
    /**
     * 按指定方向发射弹幕
     */
    private void fireDanmakuWithDirection(EntityMaid maid, DanmakuItem danmakuItem, ItemStack item, Vec3 direction) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        
        // 创建弹幕实体
        ItemDanmakuEntity danmaku = new ItemDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), maid, maid.level());
        danmaku.setItem(item);
        
        if (danmakuItem != null) {
            danmaku.setup(danmakuItem.type.damage(), 80, false, danmakuItem.type.bypass(), direction.scale(2.0));
        } else {
            // 法术卡的默认设置
            danmaku.setup(10.0f, 80, false, false, direction.scale(2.0));
        }
        
        serverLevel.addFreshEntity(danmaku);
    }
    
    /**
     * 按指定方向发射激光
     */
    private void fireLaserWithDirection(EntityMaid maid, LaserItem laserItem, ItemStack item, Vec3 direction) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        
        Vec3 startPos = maid.position().add(0, maid.getBbHeight() * 0.8, 0);
        
        // 创建激光实体
        ItemLaserEntity laser = new ItemLaserEntity(YHEntities.ITEM_LASER.get(), maid, maid.level());
        laser.setItem(item);
        laser.setup(laserItem.type.damage(), 60, 32, false, direction.scale(1.5));
        laser.setPos(startPos);
        
        serverLevel.addFreshEntity(laser);
    }
    
    /**
     * 完成施法
     */
    private void completeCasting(EntityMaid maid, MaidYHSpellData data) {

        // 符卡自然结束时触发冷却
        if (data.getActiveSpellCard() != null) {
            data.setSpellCooldown(data.getActiveSpellCard().modelId,1200,maid);
            data.deactivateSpellCard();
        }
        
        // 施法完成，重置状态
        data.resetCastingState();
    }
    
    // === 符卡系统方法 ===
    
    /**
     * 从背包中的符卡物品激活符卡
     */
    private void activateSpellCardFromInventory(EntityMaid maid, MaidYHSpellData data, Set<ItemStack> allItems) {
        // 收集所有符卡物品
        List<ItemStack> spellCardItems = new ArrayList<>();
        for (ItemStack item : allItems) {
            if (SpellCardHelper.isSpellCardItem(item)) {
                spellCardItems.add(item);
            }
        }
        
        if (spellCardItems.isEmpty()) {
            // 没有符卡物品，不激活
            return;
        }
        
        // 随机选择一个符卡物品
        ItemStack selectedItem = spellCardItems.get(maid.getRandom().nextInt(spellCardItems.size()));
        
        // 从物品创建符卡包装器
        SpellCardWrapper wrapper = SpellCardHelper.createSpellCardFromItem(selectedItem);
        if (wrapper == null || data.isSpellOnCooldown(wrapper.modelId)) {
            //LOGGER.debug("Failed to create spell card from item: {},cooldown: {}", selectedItem, data.getSpellCooldown(wrapper.modelId));
            return;
        }
        // 激活符卡
        data.activateSpellCard(wrapper);
    }
    
    /**
     * 符卡Tick处理
     */
    private void tickSpellCard(EntityMaid maid, MaidYHSpellData data) {
        SpellCardWrapper spellCard = data.getActiveSpellCard();
        if (spellCard == null || spellCard.card == null || data.getTarget() == null) {
            return;
        }
        
        // 创建CardHolder
        MaidCardHolder holder = new MaidCardHolder(maid, data.getTarget());
        spellCard.tick(holder);
    }
}
