package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidYHSpellData;

import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.LaserItem;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;

/**
 * Youkai-Homecoming弹幕物品提供者
 * 支持女仆使用弹幕物品、法术卡和激光武器
 */
public class YoukaiHomecomingProvider implements ISpellBookProvider {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 随机发射配置
    private static final int MIN_DANMAKU_COUNT = 1;  // 最少发射数量
    private static final int MAX_DANMAKU_COUNT = 10;  // 最多发射数量
    private static final float FAN_ANGLE = 45.0f;    // 扇形角度（度）

    private static final HashSet<Class<?>> DANMAKU_CLASSES = new HashSet<>(Arrays.asList(DanmakuItem.class,SpellItem.class,LaserItem.class,ItemDanmakuEntity.class,ItemLaserEntity.class));
    
    public YoukaiHomecomingProvider() {
        LOGGER.info("YoukaiHomecomingProvider initialized");
    }
    
    /**
     * 获取指定女仆的弹幕数据
     */
    private MaidYHSpellData getData(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        return MaidYHSpellData.getOrCreate(maid.getUUID());
    }
    
    // === ISpellBookProvider 接口实现 ===
    
    /**
     * 检查物品是否为Youkai-Homecoming的弹幕物品
     * 支持：DanmakuItem（弹幕）、SpellItem（法术卡）、LaserItem（激光）
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        //LOGGER.debug("isSpellBook({}),result:{}", itemStack,DANMAKU_CLASSES.contains(itemStack.getItem().getClass()));
        return DANMAKU_CLASSES.contains(itemStack.getItem().getClass());
    }
    
    /**
     * 设置目标
     */
    @Override
    public void setTarget(EntityMaid maid, LivingEntity target) {
        MaidYHSpellData data = getData(maid);
        if (data != null) {
            data.setTarget(target);
        }
    }
    
    /**
     * 获取目标
     */
    @Override
    public LivingEntity getTarget(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);
        return data != null ? data.getTarget() : null;
    }
    
    /**
     * 设置弹幕物品
     */
    @Override
    public void setSpellBook(EntityMaid maid, ItemStack danmakuItem) {
        MaidYHSpellData data = getData(maid);
        data.setSpellBook(danmakuItem);
        if(danmakuItem==null){
            data.removeDanmakuItems();
        }else{
            data.addDanmakuItem(danmakuItem);
        }
    }
    
    /**
     * 检查是否正在施法
     */
    @Override
    public boolean isCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);
        return data != null && data.isCasting();
    }
    
    /**
     * 开始施法 - 随机选择多种弹幕进行扇形发射
     */
    @Override
    public boolean initiateCasting(EntityMaid maid) {
        MaidYHSpellData data = getData(maid);

        HashSet<ItemStack> allDanmakuItems = data.getDanmakuItems();
        LOGGER.debug("Initiating casting for {} items", allDanmakuItems.size());
        if (allDanmakuItems.isEmpty()) {
            return false;
        }
        
        // 面向目标
        prepareForCasting(maid, data);
        
        // 随机选择发射数量和种类，进行扇形发射
        return performRandomFanShooting(maid, data, allDanmakuItems);
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
        
        // 清理无效目标
        data.cleanupTarget();
        
        // 更新施法时间
        data.updateCasting();
        
        // 更新朝向
        updateMaidOrientation(maid, data);
        
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
            maid.stopUsingItem();
        }
    }
    
    /**
     * 执行法术（简化版本）
     */
    @Override
    public boolean castSpell(EntityMaid maid) {
        return initiateCasting(maid);
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
    private boolean performRandomFanShooting(EntityMaid maid, MaidYHSpellData data, Set<ItemStack> allDanmakuItems) {
        if (!(maid.level() instanceof ServerLevel)) {
            return false;
        }

        Random random = new Random();
        // 随机选择发射数量
        int shotCount = random.nextInt(MIN_DANMAKU_COUNT, MAX_DANMAKU_COUNT + 1);
        Vec3 baseDirection = calculateShootDirection(maid, data);
        LOGGER.debug("danmaku set: {}",allDanmakuItems.toArray());
        ItemStack selectedItem = (ItemStack) allDanmakuItems.toArray()[random.nextInt(allDanmakuItems.size())];
        
        boolean success = false;
        
        // 发射多个弹幕
        for (int i = 0; i < shotCount; i++) {

            // 计算扇形发射角度
            float angleOffset = 0;
            if (shotCount > 1) {
                angleOffset = (FAN_ANGLE / (shotCount - 1)) * i - (FAN_ANGLE / 2);
            }
            
            // 计算发射方向
            Vec3 shootDirection = calculateFanDirection(baseDirection, angleOffset);
            
            // 根据物品类型发射
            if (fireDanmakuByType(maid, data, selectedItem, shootDirection)) {
                success = true;
            }

        }
        
        return success;
    }
    
    /**
     * 准备施法姿态
     */
    private void prepareForCasting(EntityMaid maid, MaidYHSpellData data) {
        if (data.isValidTarget()) {
            BehaviorUtils.lookAtEntity(maid, data.getTarget());
        }
        maid.swing(InteractionHand.OFF_HAND);
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
    private boolean fireDanmakuByType(EntityMaid maid, MaidYHSpellData data, ItemStack item, Vec3 direction) {
        if (!(maid.level() instanceof ServerLevel)) {
            return false;
        }
        
        if (item.getItem() instanceof DanmakuItem danmakuItem) {
            return fireDanmakuWithDirection(maid, danmakuItem, item, direction);
        } else if (item.getItem() instanceof LaserItem laserItem) {
            return fireLaserWithDirection(maid, laserItem, item, direction);
        } else if (item.getItem() instanceof SpellItem) {
            // 法术卡暂时使用弹幕的发射方式
            return fireDanmakuWithDirection(maid, null, item, direction);
        }
        
        return false;
    }
    
    /**
     * 按指定方向发射弹幕
     */
    private boolean fireDanmakuWithDirection(EntityMaid maid, DanmakuItem danmakuItem, ItemStack item, Vec3 direction) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return false;
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
        return true;
    }
    
    /**
     * 按指定方向发射激光
     */
    private boolean fireLaserWithDirection(EntityMaid maid, LaserItem laserItem, ItemStack item, Vec3 direction) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        
        Vec3 startPos = maid.position().add(0, maid.getBbHeight() * 0.8, 0);
        
        // 创建激光实体
        ItemLaserEntity laser = new ItemLaserEntity(YHEntities.ITEM_LASER.get(), maid, maid.level());
        laser.setItem(item);
        laser.setup(laserItem.type.damage(), 60, 32, false, direction.scale(1.5));
        laser.setPos(startPos);
        
        serverLevel.addFreshEntity(laser);
        return true;
    }

    
    /**
     * 更新女仆朝向
     */
    private void updateMaidOrientation(EntityMaid maid, MaidYHSpellData data) {
        if (data.isValidTarget()) {
            BehaviorUtils.lookAtEntity(maid, data.getTarget());
        }
    }
    
    /**
     * 完成施法
     */
    private void completeCasting(EntityMaid maid, MaidYHSpellData data) {

        // 施法完成，重置状态
        data.resetCastingState();
        maid.stopUsingItem();
    }
}
