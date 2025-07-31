package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.entity.spells.target_area.TargetedAreaEntity;
import io.redspace.ironsspellbooks.item.CastingItem;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import io.redspace.ironsspellbooks.api.item.weapons.MagicSwordItem;
import io.redspace.ironsspellbooks.spells.TargetAreaCastData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 铁魔法模组的法术书提供者 - 单例版本
 * 包含完整的施法逻辑，支持持续性法术和复杂的冷却系统
 * 通过 MaidIronsSpellData 管理各女仆的数据
 * 
 * 支持的法术容器类型：
 * - SpellBook: 法术书
 * - StaffItem: 法杖（继承自CastingItem）
 * - MagicSwordItem: 魔剑（带有法术容器功能的剑）
 * - 其他继承自CastingItem的物品
 */
public class IronsSpellbooksProvider implements ISpellBookProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 私有构造函数，防止外部实例化
     */
    public IronsSpellbooksProvider() {
        // 私有构造函数
    }

    /**
     * 获取指定女仆的法术数据
     */
    private MaidIronsSpellData getData(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        return MaidIronsSpellData.getOrCreate(maid.getUUID());
    }
    
    // === 核心方法（接受EntityMaid参数） ===
    
    /**
     * 检查物品是否为法术容器
     * 支持以下类型：
     * 1. SpellBook - 传统法术书
     * 2. CastingItem - 法杖等施法物品（包括StaffItem）
     * 3. MagicSwordItem - 魔剑
     * 4. 任何包含法术容器数据的物品
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        // 检查传统法术书
        if (itemStack.getItem() instanceof SpellBook) {
            return true;
        }
        
        // 检查法杖等施法物品
        if (itemStack.getItem() instanceof CastingItem) {
            return true;
        }
        
        // 检查魔剑
        if (itemStack.getItem() instanceof MagicSwordItem) {
            return true;
        }
        
        // 检查是否包含法术容器数据（通用检查）
        if (ISpellContainer.isSpellContainer(itemStack)) {
            return true;
        }
        
        return false;
    }

    /**
     * 设置目标
     */
    @Override
    public void setTarget(EntityMaid maid, LivingEntity target) {
        MaidIronsSpellData data = getData(maid);
        if (data != null) {
            data.setTarget(target);
        }
    }

    /**
     * 获取目标
     */
    @Override
    public LivingEntity getTarget(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        return data != null ? data.getTarget() : null;
    }

    /**
     * 设置法术容器（法术书、法杖、魔剑等）
     */
    @Override
    public void setSpellBook(EntityMaid maid, ItemStack spellBook) {
        String itemType = "unknown";
        if (spellBook != null && !spellBook.isEmpty()) {
            if (spellBook.getItem() instanceof SpellBook) {
                itemType = "SpellBook";
            } else if (spellBook.getItem() instanceof MagicSwordItem) {
                itemType = "MagicSword";
            } else if (spellBook.getItem() instanceof StaffItem) {
                itemType = "Staff";
            } else if (spellBook.getItem() instanceof CastingItem) {
                itemType = "CastingItem";
            } else if (ISpellContainer.isSpellContainer(spellBook)) {
                itemType = "SpellContainer";
            }
        }
        
        MaidIronsSpellData data = getData(maid);
        if (data != null) {
            // 根据物品类型分别存储到不同的槽位
            if (spellBook != null && !spellBook.isEmpty()) {
                if (spellBook.getItem() instanceof SpellBook) {
                    data.setSpellBook(spellBook);
                } else if (spellBook.getItem() instanceof MagicSwordItem) {
                    data.setMagicSword(spellBook);
                } else if (spellBook.getItem() instanceof StaffItem || spellBook.getItem() instanceof CastingItem) {
                    data.setStaff(spellBook);
                } else {
                    // 其他类型默认存储为法术书
                    data.setSpellBook(spellBook);
                }
            } else {
                // 如果传入null或空物品，清除所有法术容器
                clearAllSpellContainers(maid);
            }
        }
    }
    
    /**
     * 清除所有法术容器
     */
    private void clearAllSpellContainers(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data != null) {
            data.setSpellBook(ItemStack.EMPTY);
            data.setMagicSword(ItemStack.EMPTY);
            data.setStaff(ItemStack.EMPTY);
        }
    }
    
    /**
     * 检查是否正在施法
     */
    @Override
    public boolean isCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        return data != null && data.isCasting();
    }
    
    /**
     * 开始施法
     */
    @Override
    public boolean initiateCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data == null) {
            return false;
        }

        if (!data.hasAnySpellContainer()) {
            return false;
        }

        

        // 尝试施放可用的法术
        List<SpellData> availableSpells = new ArrayList<>();
        for(ItemStack container : data.getAllSpellContainers()){
            ISpellContainer spellContainer = ISpellContainer.get(container);
            if(!spellContainer.isEmpty()){
                availableSpells.addAll(Arrays.asList(spellContainer.getAllSpells()));
            }
        }
        
        availableSpells.removeIf(spellData -> spellData == null || data.isSpellOnCooldown(spellData.getSpell().getSpellId()));

        if (availableSpells.isEmpty()) {
            return false;
        }

        // 随机选择一个可用的法术
        int index = (int) (Math.random() * availableSpells.size());
        SpellData spellData = availableSpells.get(index);
        if (!data.isSpellOnCooldown(spellData.getSpell().getSpellId())) {
            return initiateCasting(maid, spellData);
        }

        return false;
    }
    
    /**
     * 开始施法特定法术
     */
    private boolean initiateCasting(EntityMaid maid, SpellData spellData) {
        MaidIronsSpellData data = getData(maid);
        if (spellData == null || spellData.getSpell() == null || maid == null || data == null) {
            return false;
        }
        if (data.isCasting()) {
            return false; // 如果正在施法，不能开始新的施法
        }
        
        try {
            AbstractSpell spell = spellData.getSpell();
            String spellId = spell.getSpellId();
            
            // 确保女仆面向目标（特别是对于投射法术）
            LivingEntity target = data.getTarget();
            if (target != null) {
                maid.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
                // 强制更新朝向
                forceLookAtTarget(maid, target);
            }
            
            // 设置目标相关的施法数据（在checkPreCastConditions之前）
            setupSpellTargetData(maid, spell, spellData.getLevel());
            
            MagicData magicData = data.getMagicData();
            
            // 检查前置条件
            if (!spell.checkPreCastConditions(maid.level(), spellData.getLevel(), maid, magicData)) {
                return false;
            }
            
            int effectiveCastTime = spell.getEffectiveCastTime(spellData.getLevel(), maid);
            CastSource castSource = getCastSource(data.getSpellBook());
            magicData.initiateCast(spell, spellData.getLevel(), effectiveCastTime, castSource, "offhand");
            
            // 调用施法前处理
            spell.onServerPreCast(maid.level(), spellData.getLevel(), maid, magicData);
            
            // 设置当前施法状态
            data.setCurrentCastingSpell(spellData);
            data.setCasting(true);
            maid.swing(maid.getUsedItemHand());
            
            return true;
            
        } catch (Exception e) {
            // 重置状态以防出错
            data.resetCastingState();
            return false;
        }
    }
    
    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentCastingSpell() == null || maid == null) {
            return;
        }
        
        AbstractSpell spell = data.getCurrentCastingSpell().getSpell();
        MagicData magicData = data.getMagicData();
        
        // 持续更新女仆朝向目标（重要：确保持续性法术始终面向目标）
        LivingEntity target = data.getTarget();
        if (target != null) {
            maid.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
            // 强制立即更新朝向，确保AbstractConeProjectile能够获取到正确的朝向
            forceLookAtTarget(maid, target);
        }
        
        // 更新施法持续时间
        magicData.handleCastDuration();

        // 调用法术的Tick处理
        if (magicData.isCasting()) {
            spell.onServerCastTick(maid.level(), data.getCurrentCastingSpell().getLevel(), maid, magicData);
        }
        
        // 检查施法是否完成
        int remaining = magicData.getCastDurationRemaining();
        
        if (remaining <= 0) {
            completeCasting(maid);
        } else {
            // 对于持续性法术，每隔一定时间调用onCast
            if (spell.getCastType() == CastType.CONTINUOUS) {
                // 每10tick调用一次onCast（与AbstractSpellCastingMob保持一致）
                if ((remaining + 1) % 10 == 0) {    
                    CastSource castSource = getCastSource(data.getSpellBook());
                    spell.onCast(maid.level(), data.getCurrentCastingSpell().getLevel(), maid, castSource, magicData);
                }
            }
        }
    }

    /**
     * 停止施法
     */
    @Override
    public void stopCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentCastingSpell() == null) {
            return;
        }
        
        // 强制完成施法（当施法被外部中断时）
        forceCompleteCasting(maid);
    }
    
    /**
     * 执行法术
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
        MaidIronsSpellData data = getData(maid);
        if (data != null) {
            data.updateCooldowns();
        }
    }

    // === 私有辅助方法 ===
    
    /**
     * 根据法术容器类型确定施法源
     */
    private CastSource getCastSource(ItemStack spellContainer) {
        if (spellContainer == null || spellContainer.isEmpty()) {
            return CastSource.SPELLBOOK; // 默认值
        }
        
        // 魔剑使用SWORD源
        if (spellContainer.getItem() instanceof MagicSwordItem) {
            return CastSource.SWORD;
        }
        
        // 法术书和法杖等其他施法物品使用SPELLBOOK源
        return CastSource.SPELLBOOK;
    }
    
    /**
     * 设置冷却
     */
    private void setCooldown(EntityMaid maid, AbstractSpell spell) {
        MaidIronsSpellData data = getData(maid);
        if (data == null || spell == null) return;
        
        double cooldownModifier = maid.getAttributeValue(AttributeRegistry.COOLDOWN_REDUCTION.get());
        int cooldownTicks = (int)(spell.getSpellCooldown() * (2 - Utils.softCapFormula(cooldownModifier)));
        String spellId = spell.getSpellId();

        data.setSpellCooldown(spellId, cooldownTicks);
        
    }
    
    /**
     * 强制完成施法（当施法被外部中断时）
     */
    private void forceCompleteCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data == null || data.getCurrentCastingSpell() == null) return;
        
        AbstractSpell spell = data.getCurrentCastingSpell().getSpell();
        setCooldown(maid, spell);
        

        // 重置状态
        data.resetCastingState();
    }
    
    /**
     * 完成施法
     */
    private void completeCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data == null || data.getCurrentCastingSpell() == null) return;
        
        AbstractSpell spell = data.getCurrentCastingSpell().getSpell();
        MagicData magicData = data.getMagicData();
        
        // 根据法术类型调用相应的onCast
        if (spell.getCastType() == CastType.LONG || spell.getCastType() == CastType.INSTANT) {
            // LONG和INSTANT类型在施法完成时调用onCast
            CastSource castSource = getCastSource(data.getSpellBook());
            spell.onCast(maid.level(), data.getCurrentCastingSpell().getLevel(), maid, castSource, magicData);
        }
        // CONTINUOUS类型的法术在施法过程中已经多次调用onCast，这里不需要再调用

        spell.onServerCastComplete(maid.level(), data.getCurrentCastingSpell().getLevel(), maid, magicData, false);

        setCooldown(maid, spell);
        
        // 重置施法状态
        data.resetCastingState();
    }
    
    /**
     * 强制女仆立即面向目标
     */
    private void forceLookAtTarget(EntityMaid maid, LivingEntity target) {
        if (target == null || maid == null) return;
        
        double dx = target.getX() - maid.getX();
        double dy = target.getEyeY() - maid.getEyeY();
        double dz = target.getZ() - maid.getZ();
        
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        // 计算Yaw（水平旋转）
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        
        // 计算Pitch（垂直旋转）
        float pitch = (float) (-(Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI));
        
        // 设置女仆的朝向
        maid.setYRot(yaw);
        maid.setXRot(pitch);
        maid.yRotO = yaw;
        maid.xRotO = pitch;
        maid.yHeadRot = yaw;
        maid.yHeadRotO = yaw;
        
    }
    
    /**
     * 为不同类型的法术设置目标数据
     */
    private void setupSpellTargetData(EntityMaid maid, AbstractSpell spell, int spellLevel) {
        MaidIronsSpellData data = getData(maid);
        if (data == null) return;
        
        LivingEntity target = data.getTarget();
        if (target == null) return;
        
        MagicData magicData = data.getMagicData();
        String spellId = spell.getSpellId();
        
        // 传送类法术需要特殊的TeleportData
        if (spellId.equals("irons_spellbooks:teleport") || spellId.contains("step")) {
            setTeleportLocationBehindTarget(maid, target, magicData, spell, spellLevel);
        }
        // 为需要目标区域的法术设置目标区域数据
        else if (spellId.equals("irons_spellbooks:starfall") || 
            spellId.equals("irons_spellbooks:scorch") ||
            spellId.contains("storm") || 
            spellId.contains("surge")) {
            
            // 对于范围型法术，将目标区域设置在敌人位置
            Vec3 targetPosition = target.position();
            
            // 对于某些法术，需要特殊的目标区域处理
            if (spellId.equals("irons_spellbooks:starfall")) {
                // Starfall需要地面目标区域
                targetPosition = Utils.moveToRelativeGroundLevel(maid.level(), targetPosition, 12);
                TargetedAreaEntity area = TargetedAreaEntity.createTargetAreaEntity(
                    maid.level(), targetPosition, 6.0f, 0x60008c);
                magicData.setAdditionalCastData(new TargetAreaCastData(targetPosition, area));
            } else if (spellId.equals("irons_spellbooks:scorch")) {
                // Scorch需要目标区域
                float radius = 2.5f;
                TargetedAreaEntity area = TargetedAreaEntity.createTargetAreaEntity(
                    maid.level(), targetPosition, radius, Utils.packRGB(spell.getTargetingColor()));
                magicData.setAdditionalCastData(new TargetAreaCastData(targetPosition, area));
            }
        }
    }
    
    /**
     * 为传送法术设置目标位置在敌人后方
     * 参考AbstractSpellCastingMob.setTeleportLocationBehindTarget方法
     */
    private void setTeleportLocationBehindTarget(EntityMaid maid, LivingEntity target, MagicData magicData, AbstractSpell spell, int spellLevel) {
        if (target == null || maid == null) {
            // 没有目标时传送到原地
            magicData.setAdditionalCastData(new io.redspace.ironsspellbooks.spells.ender.TeleportSpell.TeleportData(maid.position()));
            return;
        }
        
        // 根据法术类型确定传送距离
        int distance = 10; // 默认距离
        Vec3 teleportPos = target.position();
        boolean validPositionFound = false;
        
        // 尝试在目标后方找到合适的传送位置
        for (int i = 0; i < 24; i++) {
            Vec3 randomness = Utils.getRandomVec3(.15f * i).multiply(1, 0, 1);
            teleportPos = Utils.moveToRelativeGroundLevel(
                maid.level(), 
                target.position().subtract(new Vec3(0, 0, distance / (float) (i / 7 + 1))
                    .yRot(-(target.getYRot() + i * 45) * net.minecraft.util.Mth.DEG_TO_RAD))
                    .add(randomness), 
                5
            );
            teleportPos = new Vec3(teleportPos.x, teleportPos.y + .1f, teleportPos.z);
            
            var reposBB = maid.getBoundingBox().move(teleportPos.subtract(maid.position()));
            if (!maid.level().collidesWithSuffocatingBlock(maid, reposBB.inflate(-.05f))) {
                validPositionFound = true;
                break;
            }
        }
        
        if (!validPositionFound) {
            teleportPos = target.position();
        }
        
        magicData.setAdditionalCastData(new io.redspace.ironsspellbooks.spells.ender.TeleportSpell.TeleportData(teleportPos));
    }
} 