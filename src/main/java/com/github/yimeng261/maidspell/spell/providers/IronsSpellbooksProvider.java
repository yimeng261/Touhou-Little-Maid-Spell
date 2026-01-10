package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.blueNote.BlueNote;
import com.github.yimeng261.maidspell.item.bauble.blueNote.contianer.BlueNoteSpellManager;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;

import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.entity.spells.target_area.TargetedAreaEntity;
import io.redspace.ironsspellbooks.api.item.weapons.MagicSwordItem;
import io.redspace.ironsspellbooks.spells.TargetAreaCastData;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 铁魔法模组的法术书提供者
 * 包含完整的施法逻辑，支持持续性法术和复杂的冷却系统
 * 通过 MaidIronsSpellData 管理各女仆的数据
 * 支持的法术容器类型：
 * - SpellBook: 法术书
 * - StaffItem: 法杖（继承自CastingItem）
 * - MagicSwordItem: 魔剑（带有法术容器功能的剑）
 * - 其他继承自CastingItem的物品
 */
public class IronsSpellbooksProvider extends ISpellBookProvider<MaidIronsSpellData, SpellData> {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 版本兼容层 - 用于处理新旧版本的 Iron's Spellbooks API 差异
     * 新版本：getActiveSpells() 返回 List<SpellSlot>
     * 旧版本：getActiveSpells() 返回 List<SpellData>
     */
    public static class ApiCompatLayer {
        // 是否为新版本API（使用SpellSlot）
        private static final boolean IS_NEW_API;
        // 缓存的SpellSlot类
        private static final Class<?> SPELL_SLOT_CLASS;
        // 缓存的spellData()方法
        private static final Method SPELL_DATA_METHOD;
        
        static {
            boolean isNewApi = false;
            Class<?> spellSlotClass = null;
            Method spellDataMethod = null;
            
            try {
                // 尝试加载SpellSlot类
                spellSlotClass = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellSlot");
                // 如果成功加载，说明是新版本API
                isNewApi = true;
                // 获取spellData()方法
                spellDataMethod = spellSlotClass.getMethod("spellData");
                LOGGER.info("[MaidSpell] 检测到新版本 Iron's Spellbooks API (SpellSlot)");
            } catch (ClassNotFoundException e) {
                // SpellSlot类不存在，说明是旧版本API
                LOGGER.info("[MaidSpell] 检测到旧版本 Iron's Spellbooks API (直接使用 SpellData)");
            } catch (NoSuchMethodException e) {
                // 找到了SpellSlot类但没有spellData()方法，这种情况不应该发生
                LOGGER.error("[MaidSpell] 发现 SpellSlot 类但无法获取 spellData() 方法", e);
                isNewApi = false;
            }
            
            IS_NEW_API = isNewApi;
            SPELL_SLOT_CLASS = spellSlotClass;
            SPELL_DATA_METHOD = spellDataMethod;
        }
        
        /**
         * 将 getActiveSpells() 返回的列表转换为 SpellData 列表
         * @param rawList getActiveSpells() 返回的原始列表
         * @return SpellData 列表
         */
        @SuppressWarnings("unchecked")
        public static List<SpellData> convertToSpellDataList(List<?> rawList) {
            if (rawList == null || rawList.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 如果是旧版本API，直接返回（已经是List<SpellData>）
            if (!IS_NEW_API) {
                return (List<SpellData>) rawList;
            }
            
            // 如果是新版本API，需要从SpellSlot中提取SpellData
            List<SpellData> spellDataList = new ArrayList<>(rawList.size());
            for (Object obj : rawList) {
                try {
                    if (SPELL_SLOT_CLASS.isInstance(obj)) {
                        // 调用 spellData() 方法获取 SpellData
                        SpellData spellData = (SpellData) SPELL_DATA_METHOD.invoke(obj);
                        if (spellData != null) {
                            spellDataList.add(spellData);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[MaidSpell] 从 SpellSlot 提取 SpellData 时出错", e);
                }
            }
            
            return spellDataList;
        }
    }

    /**
     * 构造函数，绑定 MaidIronsSpellData 数据类型和 SpellData 法术类型
     */
    public IronsSpellbooksProvider() {
        super(MaidIronsSpellData::getOrCreate, SpellData.class);
    }
    
    // === 核心方法（接受EntityMaid参数） ===
    
    /**
     * 从单个法术容器中收集所有法术
     * @param spellBook 法术容器物品堆栈
     * @return 该法术容器中的所有法术数据列表
     */
    @Override
    protected List<SpellData> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<SpellData> spells = new ArrayList<>();

        // 获取法术容器接口
        ISpellContainer spellContainer = ISpellContainer.get(spellBook);
        if (spellContainer.isEmpty()) {
            return spells;
        }
        
        // 使用兼容层转换：新版本会从 List<SpellSlot> 提取 SpellData，旧版本直接返回 List<SpellData>
        return ApiCompatLayer.convertToSpellDataList(spellContainer.getActiveSpells());
    }
    
    /**
     * 检查物品是否为法术容器
     * 支持任何包含法术容器数据的物品
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        return ISpellContainer.isSpellContainer(itemStack);
    }

    
    /**
     * 开始施法
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data == null) {
            return;
        }

        // 检查是否有可用的法术容器
        if (data.getSpellBooks().isEmpty()) {
            return;
        }

        // 从所有法术容器中收集法术
        List<SpellData> availableSpells = collectSpellFromAvailableSpellBooks(maid);

        // 过滤掉无效、冷却中或黑名单中的法术
        availableSpells.removeIf(spellData -> {
            if(spellData == null || spellData.getSpell() == null) {
                return true;
            }
            String spellId = spellData.getSpell().getSpellId();
            if (data.isSpellOnCooldown(spellId)) {
                return true;
            }
            // 检查法术是否在黑名单中
            if (Config.spellBlacklist != null && Config.spellBlacklist.contains(spellId)) {
                LOGGER.debug("法术 {} 在黑名单中，女仆 {} 跳过施放", spellId, maid.getUUID());
                return true;
            }
            return false;
        });

        //LOGGER.debug("[MaidSpell] {} spells available, {} spellbooks added", availableSpells.size(), data.getSpellBooks().size());

        if (availableSpells.isEmpty()) {
            return;
        }

        // 随机选择一个可用的法术
        int index = (int) (Math.random() * availableSpells.size());
        SpellData spellData = availableSpells.get(index);
        //LOGGER.debug("Spell {} selected", spellData.getSpell().getSpellId());
        if(BaubleStateManager.hasBauble(maid,MaidSpellItems.BLUE_NOTE)){
            ItemStack bauble = null;
            BaubleItemHandler baubleItemHandler = maid.getMaidBauble();
            for(int i=0; i<baubleItemHandler.getSlots(); i++){
                bauble = baubleItemHandler.getStackInSlot(i);
                if(bauble.getItem() instanceof BlueNote){
                    break;
                }
            }
            if (bauble != null && BlueNoteSpellManager.getStoredSpellIds(bauble).contains(spellData.getSpell().getSpellId())) {
                LOGGER.debug("should cast to owner : {}", spellData.getSpell().getSpellId());
                data.switchTargetToOwner(maid);
            }
        }

        actualCasting(maid, spellData);
    }
    
    /**
     * 开始施法特定法术
     */
    private void actualCasting(EntityMaid maid, SpellData spellData) {
        MaidIronsSpellData data = getData(maid);
        
        try {
            AbstractSpell spell = spellData.getSpell();
            
            // 确保女仆面向目标（特别是对于投射法术）
            forceLookAtTarget(maid, data);

            // 设置目标相关的施法数据（在checkPreCastConditions之前）
            setupSpellTargetData(maid, spell);
            
            MagicData magicData = data.getMagicData();
            
            // 检查前置条件
            if (!spell.checkPreCastConditions(maid.level(), spellData.getLevel(), maid, magicData)) {
                LOGGER.debug("spell check failed : {}", spellData.getSpell().getSpellId());
                return;
            }
            
            int effectiveCastTime = spell.getEffectiveCastTime(spellData.getLevel(), maid);
            CastSource castSource = getCastSource(data, spellData);
            // 缓存 CastSource，避免持续施法期间重复扫描
            data.setCachedCastSource(castSource);
            magicData.initiateCast(spell, spellData.getLevel(), effectiveCastTime, castSource, "offhand");
            
            // 调用施法前处理
            spell.onServerPreCast(maid.level(), spellData.getLevel(), maid, magicData);
            
            // 设置当前施法状态
            data.setCurrentCastingSpell(spellData);
            data.setCasting(true);
            // maid.swing(maid.getUsedItemHand());

        } catch (Exception e) {
            // 重置状态以防出错
            LOGGER.debug("[MaidSpell] error: {}",e.getMessage());
            data.resetCastingState();
        }
    }
    
    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidIronsSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentCastingSpell() == null) {
            return;
        }
        
        AbstractSpell spell = data.getCurrentCastingSpell().getSpell();
        MagicData magicData = data.getMagicData();
        
        // 持续更新女仆朝向目标（重要：确保持续性法术始终面向目标）
        forceLookAtTarget(maid, data);

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
                    // 使用缓存的 CastSource，避免每次都扫描容器
                    CastSource castSource = data.getCachedCastSource();
                    if (castSource == null) {
                        castSource = getCastSource(data, data.getCurrentCastingSpell());
                    }
                    spell.onCast(maid.level(), data.getCurrentCastingSpell().getLevel(), maid, castSource, magicData);
                }
            }
        }
    }

    private void forceLookAtTarget(EntityMaid maid, MaidIronsSpellData data) {
        LivingEntity target = data.getTarget();
        if (target != null) {
            if(target==maid.getOwner()){
                LOGGER.debug("look at owner");
            }
            BehaviorUtils.lookAtEntity(maid, target);
            maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0.0F));
            maid.lookAt(Anchor.EYES, target.getEyePosition());
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
     * 根据当前施放的法术找到对应的容器并确定施法源
     */
    private CastSource getCastSource(MaidIronsSpellData data, SpellData currentSpell) {
        if (data == null || currentSpell == null) {
            return CastSource.SPELLBOOK;
        }
        
        // 在所有法术容器中查找包含当前法术的容器
        for (ItemStack container : data.getSpellBooks()) {
            if (container.isEmpty()) continue;
            
            ISpellContainer spellContainer = ISpellContainer.get(container);
            if (spellContainer.isEmpty()) continue;
            
            // 检查此容器是否包含当前法术
            for (SpellData spellData : ApiCompatLayer.convertToSpellDataList(spellContainer.getActiveSpells())) {
                if (spellData.equals(currentSpell)) {
                    return getCastSource(container);
                }
            }
        }
        
        // 如果找不到，返回默认值
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

        data.setSpellCooldown(spellId, cooldownTicks, maid);
        
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
            CastSource castSource = getCastSource(data, data.getCurrentCastingSpell());
            spell.onCast(maid.level(), data.getCurrentCastingSpell().getLevel(), maid, castSource, magicData);
        }
        // CONTINUOUS类型的法术在施法过程中已经多次调用onCast，这里不需要再调用

        spell.onServerCastComplete(maid.level(), data.getCurrentCastingSpell().getLevel(), maid, magicData, false);

        setCooldown(maid, spell);
        
        // 重置施法状态
        data.resetCastingState();
    }
    
    /**
     * 为不同类型的法术设置目标数据
     */
    private void setupSpellTargetData(EntityMaid maid, AbstractSpell spell) {
        MaidIronsSpellData data = getData(maid);
        if (data == null) return;
        
        LivingEntity target = data.getTarget();
        if (target == null) return;
        
        MagicData magicData = data.getMagicData();
        String spellId = spell.getSpellId();
        
        // 传送类法术需要特殊的TeleportData
        if (spellId.equals("irons_spellbooks:teleport") || spellId.contains("step")) {
            setTeleportLocationBehindTarget(maid, target, magicData);
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
    private void setTeleportLocationBehindTarget(EntityMaid maid, LivingEntity target, MagicData magicData) {
        if (target == null || maid == null) {
            return;
        }
        
        // 根据法术类型确定传送距离
        int distance = 10; // 默认距离
        Vec3 teleportPos = target.position();
        boolean validPositionFound = false;
        
        // 尝试在目标后方找到合适传送位置
        for (int i = 0; i < 24; i++) {
            Vec3 randomness = Utils.getRandomVec3(.15f * i).multiply(1, 0, 1);
            teleportPos = Utils.moveToRelativeGroundLevel(
                maid.level(), 
                target.position().subtract(new Vec3(0, 0, distance / (float) (i / 7 + 1))
                    .yRot(-(target.getYRot() + i * 45) * Mth.DEG_TO_RAD))
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
        
        magicData.setAdditionalCastData(new TeleportSpell.TeleportData(teleportPos));
    }
} 