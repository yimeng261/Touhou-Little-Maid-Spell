package com.github.yimeng261.maidspell.spell.providers;


import com.mojang.datafixers.util.Pair;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.slf4j.Logger;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidGoetySpellData;
import com.mojang.logging.LogUtils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.IChargingSpell;
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.Polarice3.Goety.common.items.magic.FocusBag;
import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Goety模组法术书提供者 - 单例版本
 * 支持诡恶巫法的法杖和聚晶系统
 * 全局只有一个实例，通过MaidGoetySpellData管理各女仆的数据
 */
public class GoetyProvider implements ISpellBookProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_INFINITE_CASTING_TIME = 45;
    

    /**
     * 私有构造函数，防止外部实例化
     */
    public GoetyProvider() {
        // 私有构造函数
    }


    /**
     * 获取指定女仆的法术数据
     */
    private MaidGoetySpellData getData(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        return MaidGoetySpellData.getOrCreate(maid.getUUID());
    }
    
    // === 核心方法（接受EntityMaid参数） ===
    
    /**
     * 检查物品是否为法术书
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof IWand;
    }

    /**
     * 设置目标
     */
    @Override
    public void setTarget(EntityMaid maid, LivingEntity target) {
        MaidGoetySpellData data = getData(maid);
        if (data != null) {
            data.setTarget(target);
        }
    }

    /**
     * 获取目标
     */
    @Override
    public LivingEntity getTarget(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        return data != null ? data.getTarget() : null;
    }


    /**
     * 设置法术书
     */
    @Override
    public void setSpellBook(EntityMaid maid, ItemStack spellBook) {
        MaidGoetySpellData data = getData(maid);
        if (data != null) {
            ItemStack oldSpellBook = data.getSpellBook()==null ? ItemStack.EMPTY : data.getSpellBook();
            ItemStack newSpellBook = spellBook==null ? ItemStack.EMPTY : spellBook;
            if(!ItemStack.isSameItemSameTags(oldSpellBook, newSpellBook)) {
                stopCasting(maid);
                data.setSpellBook(spellBook);
            }
        }
    }
    
    /**
     * 检查是否正在施法
     */
    @Override
    public boolean isCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        return data != null && data.isCasting();
    }
    
    /**
     * 开始施法
     */
    @Override
    public boolean initiateCasting(EntityMaid maid) {
        
        // 首先检查是否有有效的法杖
        MaidGoetySpellData data = getData(maid);
        if (data == null) {
            return false;
        }
        
        ItemStack spellBook = data.getSpellBook();
        if (!isSpellBook(spellBook)) {
            return false;
        }
        
        if (!canStartCasting(maid)) {
            return false;
        }
        
        ISpell spell = extractSpellFromWand(maid);
        if (spell == null) {
            return false;
        }
        
        
        // 只有在确认有有效法术后才装备法杖
        ensureWandEquipped(maid);
        
        // 设置女仆面向目标并挥动手臂
        prepareForCasting(maid);
        
        return startSpellCasting(maid, spell);
    }
    
    /**
     * 处理持续施法
     */
    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null || !isSpellBook(data.getSpellBook())) {
            return;
        }
        
        data.incrementCastingTime();
        // 检查目标状态
        if (!isValidTarget(data)) {
            stopCasting(maid);
            return;
        }
        
        // 更新朝向
        updateMaidOrientation(maid, data);
        
        // 处理法术逻辑
        if (data.getCurrentSpell() instanceof IChargingSpell chargingSpell) {
            processChargingSpell(maid, chargingSpell);
        } else {
            processNormalSpell(maid);
        }
    }
    
    /**
     * 停止施法
     */
    @Override
    public void stopCasting(EntityMaid maid) {
        if (maid.isUsingItem()) {
            maid.stopUsingItem();
        }
        MaidGoetySpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentSpell() == null) {
            return;
        }
        
        // 调用法术停止逻辑
        if (maid.level() instanceof ServerLevel serverLevel) {
            data.getCurrentSpell().stopSpell(serverLevel, maid, data.getSpellBook(),
                data.getMaxCastingTime() - data.getCastingTime());
        }
        
        // 设置部分冷却
        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);

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
        MaidGoetySpellData data = getData(maid);
        if (data != null) {
            data.updateCooldowns();
        }
    }


    // === 私有辅助方法 ===
    
    private boolean canStartCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null || data.isCasting()) {
            return false;
        }
        
        return isValidTarget(data);
    }
    
    private boolean isValidTarget(MaidGoetySpellData data) {
        LivingEntity target = data.getTarget();
        return target != null && target.isAlive();
    }
    
    /**
     * 从法杖中提取法术 - 重写版本，复用Goety模组的聚晶收集逻辑
     */
    private ISpell extractSpellFromWand(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) {
            return null;
        }
        
        ItemStack spellBook = data.getSpellBook();
        if (spellBook == null || spellBook.isEmpty()) {
            return null;
        }

        // 复用Goety模组的聚晶包查找逻辑
        ItemStack focusBag = findFocusBag(maid);
        
        // 收集所有可用的聚晶（复用Goety模组的逻辑）
        List<Pair<ItemStack, Integer>> availableFoci = collectAllAvailableFoci(maid, data, spellBook, focusBag);
        
        if (availableFoci.isEmpty()) {
            return null;
        }
        
        // 随机选择一个聚晶
        int randomIndex = (int) (Math.random() * availableFoci.size());
        Pair<ItemStack, Integer> selected = availableFoci.get(randomIndex);
        
        // 如果选择的聚晶与当前聚晶不同，进行切换
        if (selected.getSecond() != -1) {
            // 复用Goety模组的聚晶切换逻辑
            if (switchFocus(maid, spellBook, focusBag, selected.getSecond())) {
                ItemStack selectedFocus = selected.getFirst();
                if (selectedFocus.getItem() instanceof IFocus magicFocus) {
                    return magicFocus.getSpell();
                }
            }
        } else {
            // 使用当前法杖上的聚晶
            ItemStack currentFocus = selected.getFirst();
            if (currentFocus.getItem() instanceof IFocus magicFocus) {
                return magicFocus.getSpell();
            }
        }
        
        return null;
    }
    
    /**
     * 收集所有可用的聚晶 - 复用Goety模组的逻辑
     */
    private List<Pair<ItemStack, Integer>> collectAllAvailableFoci(EntityMaid maid, MaidGoetySpellData data, ItemStack spellBook, ItemStack focusBag) {
        List<Pair<ItemStack, Integer>> availableFoci = new ArrayList<>();

        // 检查当前法杖上的聚晶（复用Goety模组的逻辑）
        ItemStack currentFocus = IWand.getFocus(spellBook);
        if (!currentFocus.isEmpty() && currentFocus.getItem() instanceof IFocus focus) {
            ISpell spell = focus.getSpell();
            if (spell != null && !data.isSpellOnCooldown(getSpellId(spell))) {
                availableFoci.add(new Pair<>(currentFocus.copy(), -1));
            }
        }

        // 检查聚晶包中的聚晶（复用Goety模组的逻辑）
        if (!focusBag.isEmpty()) {
            try {
                FocusBagItemHandler bagHandler = FocusBagItemHandler.get(focusBag);
                // 复用Goety模组的聚晶包遍历逻辑，从索引1开始（索引0通常用于当前聚晶）
                for (int i = 1; i < bagHandler.getSlots(); i++) {
                    ItemStack focusStack = bagHandler.getStackInSlot(i);
                    if (!focusStack.isEmpty() && focusStack.getItem() instanceof IFocus focus) {
                        ISpell spell = focus.getSpell();
                        if (spell != null && !data.isSpellOnCooldown(getSpellId(spell))) {
                            availableFoci.add(new Pair<>(focusStack, i));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to access focus bag handler for maid: {}", maid.getUUID(), e);
            }
        }
        
        return availableFoci;
    }
    
    /**
     * 查找聚晶包 - 复用Goety模组的逻辑，适配女仆实体
     */
    private ItemStack findFocusBag(EntityMaid maid) {
        if (maid == null) {
            return ItemStack.EMPTY;
        }
        
        // 复用Goety模组的聚晶包查找逻辑，但适配女仆的背包系统
        var availableInv = maid.getAvailableInv(false);
        for (int i = 0; i < availableInv.getSlots(); i++) {
            ItemStack stack = availableInv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FocusBag) {
                return stack;
            }
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * 切换聚晶 - 复用Goety模组的聚晶切换逻辑
     */
    private boolean switchFocus(EntityMaid maid, ItemStack spellBook, ItemStack focusBag, int bagSlot) {
        try {
            // 记录切换前的状态
//            List<String> beforeState = captureFocusState(spellBook, focusBag);
//            logFocusSwitchBefore(maid, spellBook, focusBag, bagSlot);
            
            // 复用Goety模组的聚晶包处理器逻辑
            FocusBagItemHandler bagHandler = FocusBagItemHandler.get(focusBag);
            SoulUsingItemHandler wandHandler = SoulUsingItemHandler.get(spellBook);
            
            ItemStack currentFocus = wandHandler.extractItem();
            ItemStack selectedFocus = bagHandler.getStackInSlot(bagSlot);
            
            bagHandler.setStackInSlot(bagSlot, currentFocus);
            wandHandler.insertItem(selectedFocus);
            
            // 记录切换后的状态
//            logFocusSwitchAfter(maid, spellBook, focusBag);
//            List<String> afterState = captureFocusState(spellBook, focusBag);
//
//            // 检测并警告变化
//            checkFocusStateChanges(maid, beforeState, afterState, bagSlot);
            
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to switch focus for maid: {}", maid.getUUID(), e);
        }
        
        return false;
    }

    private void prepareForCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data != null && data.getTarget() != null) {
            BehaviorUtils.lookAtEntity(maid, data.getTarget());
        }
        maid.swing(InteractionHand.MAIN_HAND);
    }
    
    private boolean startSpellCasting(EntityMaid maid, ISpell spell) {
        if (spell.CastingSound() != null) {
            playSpellSound(maid, spell);
        }
        
        if (spell instanceof IChargingSpell chargingSpell) {
            return startChargingSpell(maid, chargingSpell);
        } else {
            return startNormalSpell(maid, spell);
        }
    }
    
    private boolean startNormalSpell(EntityMaid maid, ISpell spell) {
        int castDuration = spell.defaultCastDuration();
        if (castDuration <= 0) {
            executeInstantSpell(maid, spell);
            setCooldown(maid, spell);
        } else {        
            initiateCastingState(maid, spell, castDuration);
            startSpellExecution(maid, spell);
        }
        return true;
    }
    
    private boolean startChargingSpell(EntityMaid maid, IChargingSpell chargingSpell) {
        int maxDuration = chargingSpell.defaultCastDuration();
        
        if (chargingSpell.everCharge()) {
            maxDuration = Math.min(maxDuration, MAX_INFINITE_CASTING_TIME);
        }
        
        initiateCastingState(maid, chargingSpell, maxDuration);
        resetChargingCounters(maid);
        startSpellExecution(maid, chargingSpell);


        
        return true;
    }
    
    private void executeInstantSpell(EntityMaid maid, ISpell spell) {
        if (maid.level() instanceof ServerLevel serverLevel) {
            spell.startSpell(serverLevel, maid, getData(maid).getSpellBook(), spell.defaultStats());
            spell.SpellResult(serverLevel, maid, getData(maid).getSpellBook(), spell.defaultStats());
        }
    }
    
    private void initiateCastingState(EntityMaid maid, ISpell spell, int duration) {
        MaidGoetySpellData data = getData(maid);
        if (data != null) {
            data.initiateCastingState(spell, duration);
        }
    }
    
    private void resetChargingCounters(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data != null) {
            data.resetChargingCounters();
        }
    }
    
    private void startSpellExecution(EntityMaid maid, ISpell spell) {
        if (maid.level() instanceof ServerLevel serverLevel) {
            spell.startSpell(serverLevel, maid, getData(maid).getSpellBook(), spell.defaultStats());
        }
    }
    
    private void updateMaidOrientation(EntityMaid maid, MaidGoetySpellData data) {
        if (data.getTarget() != null) {
            BehaviorUtils.lookAtEntity(maid, data.getTarget());
            
            if (data.getCurrentSpell() instanceof IChargingSpell) {
                updatePreciseOrientation(maid, data);
            }
        }
    }
    
    private void updatePreciseOrientation(EntityMaid maid, MaidGoetySpellData data) {
        LivingEntity target = data.getTarget();
        if (target == null) return;
        
        double dx = target.getX() - maid.getX();
        double dy = target.getEyeY() - maid.getEyeY();
        double dz = target.getZ() - maid.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI));
        
        maid.setYRot(yaw);
        maid.setXRot(pitch);
        maid.yRotO = yaw;
        maid.xRotO = pitch;
    }
    
    private void processNormalSpell(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        if (maid.level() instanceof ServerLevel serverLevel && !data.spellUsed()) {
            data.getCurrentSpell().useSpell(serverLevel, maid, data.getSpellBook(), 
                data.getCastingTime(), data.getCurrentSpell().defaultStats());
            data.setSpellUsed(true);
        }
        
        if (data.getCastingTime() >= data.getMaxCastingTime()) {
            completeCasting(maid);
        }
    }
    
    private void processChargingSpell(EntityMaid maid, IChargingSpell chargingSpell) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        
        if (maid.level() instanceof ServerLevel serverLevel && !data.spellUsed()) {
            chargingSpell.useSpell(serverLevel, maid, data.getSpellBook(), 
                data.getCastingTime(), chargingSpell.defaultStats());
            data.setSpellUsed(true);
        }
        
        // 首先检查时间限制，确保无限蓄力法术也会在时间到达时停止
        if (data.getCastingTime() >= data.getMaxCastingTime()) {
            completeCasting(maid);
        }
        
        handleChargingLogic(maid, chargingSpell);
    }
    
    
    private void handleChargingLogic(EntityMaid maid, IChargingSpell chargingSpell) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        int castUpTime = getCastUpTime(maid, chargingSpell);
        
        // 如果还在蓄力阶段，不执行射击
        if (data.getCastingTime() < castUpTime && castUpTime > 0) {
            return;
        }
        
        data.incrementCoolCounter();
        int requiredCooldown = chargingSpell.Cooldown(maid, data.getSpellBook(), data.getShotsFired());
        
        
        if (data.getCoolCounter() >= requiredCooldown) {
            data.setCoolCounter(0);
            executeChargingShot(maid, chargingSpell);
            if(chargingSpell.shotsNumber(maid, data.getSpellBook()) <= data.getShotsFired()){
                completeCasting(maid);
            }
        }
    }
    
    private int getCastUpTime(EntityMaid maid, IChargingSpell chargingSpell) {
        try {
            return chargingSpell.castUp(maid, getData(maid).getSpellBook());
        } catch (NoSuchMethodError e) {
            try {
                return chargingSpell.defaultCastUp();
            } catch (Exception ex) {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    private void executeChargingShot(EntityMaid maid, IChargingSpell chargingSpell) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        
        // 先执行射击效果
        if (maid.level() instanceof ServerLevel serverLevel) {
            chargingSpell.SpellResult(serverLevel, maid, data.getSpellBook(), chargingSpell.defaultStats());
        }
        
    }
    

    
    private void playSpellSound(EntityMaid maid, ISpell spell) {
        maid.level().playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                Objects.requireNonNull(spell.CastingSound()), SoundSource.HOSTILE,
            spell.castingVolume(), spell.castingPitch());
    }
    
    private void ensureWandEquipped(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;


        ItemStack mainHandItem = maid.getMainHandItem();
        ItemStack originalOffHand = maid.getOffhandItem();
        boolean hasWandInMainHand = isSpellBook(mainHandItem);
        boolean hasWandInOffHand = isSpellBook(originalOffHand);
        InteractionHand wandHand = hasWandInMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        if (!isSpellBook(data.getSpellBook())){
            return;
        }

        if (!hasWandInMainHand && !hasWandInOffHand) {
            swapItem(maid, data.getSpellBook(), originalOffHand);
        }

        maid.startUsingItem(wandHand);
    }
    
    private void swapItem(EntityMaid maid, ItemStack spellBook, ItemStack handItem) {
        if (maid == null) return;

        CombinedInvWrapper availableInv = maid.getAvailableInv(true);
        
        for (int i = 0; i < availableInv.getSlots(); i++) {
            ItemStack stackInSlot = availableInv.getStackInSlot(i);
            if (ItemStack.isSameItem(stackInSlot, spellBook)) {
                availableInv.setStackInSlot(i, handItem);
                maid.setItemInHand(InteractionHand.OFF_HAND, spellBook);
                return;
            }
        }

    }
    
    private void completeCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null || data.getCurrentSpell() == null) {
            return;
        }
        
        if (maid.level() instanceof ServerLevel serverLevel) {
            data.getCurrentSpell().stopSpell(serverLevel, maid, data.getSpellBook(),0);
            data.getCurrentSpell().SpellResult(serverLevel, maid, data.getSpellBook(), 
                data.getCurrentSpell().defaultStats());
        }

        
        
        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);
    }
    

    
    private void resetCastingState(EntityMaid maid, MaidGoetySpellData data) {
        data.resetCastingState();
        maid.stopUsingItem();
    }
    
    private String getSpellId(ISpell spell) {
        return spell.getClass().getSimpleName();
    }
    
    private void setCooldown(EntityMaid maid, ISpell spell) {
        MaidGoetySpellData data = getData(maid);
        if (data != null && spell != null) {
            String spellId = getSpellId(spell);
            int cooldown = spell.defaultSpellCooldown();
            data.setSpellCooldown(spellId, cooldown, maid);
        }
    }
    
    /**
     * 记录聚晶切换前的状态
     */
    private void logFocusSwitchBefore(EntityMaid maid, ItemStack spellBook, ItemStack focusBag, int bagSlot) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        LOGGER.debug("==== 聚晶切换前状态 - 女仆: {} ====", maid.getUUID());
        
        // 记录当前法杖上的聚晶
        ItemStack currentFocus = IWand.getFocus(spellBook);
        if (!currentFocus.isEmpty() && currentFocus.getItem() instanceof IFocus focus) {
            ISpell spell = focus.getSpell();
            String spellName = spell != null ? spell.getClass().getSimpleName() : "未知";
            boolean onCooldown = spell != null && data.isSpellOnCooldown(getSpellId(spell));
            int cooldownRemaining = spell != null ? data.getSpellCooldown(getSpellId(spell)) : 0;
            LOGGER.debug("当前法杖聚晶: {} | 法术: {} | 冷却中: {} | 剩余冷却: {}刻",
                currentFocus.getDisplayName().getString(), spellName, onCooldown, cooldownRemaining);
        } else {
            LOGGER.debug("当前法杖聚晶: 无");
        }
        
        // 记录聚晶包中的所有聚晶
        if (!focusBag.isEmpty()) {
            try {
                FocusBagItemHandler bagHandler = FocusBagItemHandler.get(focusBag);
                LOGGER.debug("聚晶包中的聚晶:");
                for (int i = 0; i < bagHandler.getSlots(); i++) {
                    ItemStack focusStack = bagHandler.getStackInSlot(i);
                    if (!focusStack.isEmpty() && focusStack.getItem() instanceof IFocus focus) {
                        ISpell spell = focus.getSpell();
                        String spellName = spell != null ? spell.getClass().getSimpleName() : "未知";
                        boolean onCooldown = spell != null && data.isSpellOnCooldown(getSpellId(spell));
                        int cooldownRemaining = spell != null ? data.getSpellCooldown(getSpellId(spell)) : 0;
                        String isSelected = (i == bagSlot) ? " [即将切换]" : "";
                        LOGGER.debug("  槽位{}: {} | 法术: {} | 冷却中: {} | 剩余冷却: {}刻{}",
                            i, focusStack.getDisplayName().getString(), spellName, onCooldown, cooldownRemaining, isSelected);
                    } else {
                        LOGGER.debug("  槽位{}: 空", i);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("无法读取聚晶包状态: {}", e.getMessage());
            }

        } else {
            LOGGER.debug("聚晶包: 无");
        }
        
        LOGGER.debug("准备切换: 槽位{} -> 法杖", bagSlot);
    }
    
    /**
     * 记录聚晶切换后的状态
     */
    private void logFocusSwitchAfter(EntityMaid maid, ItemStack spellBook, ItemStack focusBag) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        LOGGER.debug("==== 聚晶切换后状态 - 女仆: {} ====", maid.getUUID());
        
        // 记录当前法杖上的聚晶
        ItemStack currentFocus = IWand.getFocus(spellBook);
        if (!currentFocus.isEmpty() && currentFocus.getItem() instanceof IFocus focus) {
            ISpell spell = focus.getSpell();
            String spellName = spell != null ? spell.getClass().getSimpleName() : "未知";
            boolean onCooldown = spell != null && data.isSpellOnCooldown(getSpellId(spell));
            int cooldownRemaining = spell != null ? data.getSpellCooldown(getSpellId(spell)) : 0;
            LOGGER.debug("新的法杖聚晶: {} | 法术: {} | 冷却中: {} | 剩余冷却: {}刻",
                currentFocus.getDisplayName().getString(), spellName, onCooldown, cooldownRemaining);
        } else {
            LOGGER.debug("新的法杖聚晶: 无");
        }
        
        // 记录聚晶包中的所有聚晶
        if (!focusBag.isEmpty()) {
            try {
                FocusBagItemHandler bagHandler = FocusBagItemHandler.get(focusBag);
                LOGGER.debug("切换后聚晶包状态:");
                for (int i = 0; i < bagHandler.getSlots(); i++) {
                    ItemStack focusStack = bagHandler.getStackInSlot(i);
                    if (!focusStack.isEmpty() && focusStack.getItem() instanceof IFocus focus) {
                        ISpell spell = focus.getSpell();
                        String spellName = spell != null ? spell.getClass().getSimpleName() : "未知";
                        boolean onCooldown = spell != null && data.isSpellOnCooldown(getSpellId(spell));
                        int cooldownRemaining = spell != null ? data.getSpellCooldown(getSpellId(spell)) : 0;
                        LOGGER.debug("  槽位{}: {} | 法术: {} | 冷却中: {} | 剩余冷却: {}刻",
                            i, focusStack.getDisplayName().getString(), spellName, onCooldown, cooldownRemaining);
                    } else {
                        LOGGER.debug("  槽位{}: 空", i);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("无法读取切换后聚晶包状态: {}", e.getMessage());
            }
        } else {
            LOGGER.debug("聚晶包: 无");
        }
        
        LOGGER.debug("==== 聚晶切换完成 ====");
    }
    
    /**
     * 捕获当前聚晶状态 - 用于变化检测
     */
    private List<String> captureFocusState(ItemStack spellBook, ItemStack focusBag) {
        List<String> state = new ArrayList<>();
        
        // 记录法杖上的聚晶
        ItemStack currentFocus = IWand.getFocus(spellBook);
        if (!currentFocus.isEmpty() && currentFocus.getItem() instanceof IFocus focus) {
            ISpell spell = focus.getSpell();
            String spellName = spell != null ? spell.getClass().getSimpleName() : "未知";
            state.add("WAND:" + currentFocus.getDisplayName().getString() + ":" + spellName);
        } else {
            state.add("WAND:EMPTY");
        }
        
        // 记录聚晶包中的聚晶
        if (!focusBag.isEmpty()) {
            try {
                FocusBagItemHandler bagHandler = FocusBagItemHandler.get(focusBag);
                for (int i = 0; i < bagHandler.getSlots(); i++) {
                    ItemStack focusStack = bagHandler.getStackInSlot(i);
                    if (!focusStack.isEmpty() && focusStack.getItem() instanceof IFocus focus) {
                        ISpell spell = focus.getSpell();
                        String spellName = spell != null ? spell.getClass().getSimpleName() : "未知";
                        state.add("BAG" + i + ":" + focusStack.getDisplayName().getString() + ":" + spellName);
                    } else {
                        state.add("BAG" + i + ":EMPTY");
                    }
                }
            } catch (Exception e) {
                state.add("BAG:ERROR:" + e.getMessage());
            }
        }
        
        return state;
    }
    
    /**
     * 检查聚晶状态变化并发出警告
     */
    private void checkFocusStateChanges(EntityMaid maid, List<String> beforeState, List<String> afterState, int switchedSlot) {
        // 预期的变化：法杖聚晶与指定槽位聚晶交换
        List<String> expectedAfterState = new ArrayList<>(beforeState);
        
        // 找到法杖和指定槽位的聚晶
        String wandBefore = beforeState.stream().filter(s -> s.startsWith("WAND:")).findFirst().orElse("WAND:EMPTY");
        String bagSlotBefore = beforeState.stream().filter(s -> s.startsWith("BAG" + switchedSlot + ":")).findFirst().orElse("BAG" + switchedSlot + ":EMPTY");
        
        // 构建预期的交换后状态
        for (int i = 0; i < expectedAfterState.size(); i++) {
            String item = expectedAfterState.get(i);
            if (item.startsWith("WAND:")) {
                expectedAfterState.set(i, bagSlotBefore.replace("BAG" + switchedSlot + ":", "WAND:"));
            } else if (item.startsWith("BAG" + switchedSlot + ":")) {
                expectedAfterState.set(i, wandBefore.replace("WAND:", "BAG" + switchedSlot + ":"));
            }
        }
        
        // 比较实际状态与预期状态
        boolean hasUnexpectedChanges = false;
        List<String> warnings = new ArrayList<>();
        
        if (afterState.size() != expectedAfterState.size()) {
            hasUnexpectedChanges = true;
            warnings.add("聚晶总数发生变化: 预期" + expectedAfterState.size() + "个, 实际" + afterState.size() + "个");
        } else {
            for (int i = 0; i < afterState.size(); i++) {
                String actual = afterState.get(i);
                String expected = expectedAfterState.get(i);
                
                if (!actual.equals(expected)) {
                    hasUnexpectedChanges = true;
                    warnings.add("位置异常变化: " + expected + " -> " + actual);
                }
            }
        }
        
        // 检查聚晶是否意外丢失或复制
        List<String> beforeFoci = beforeState.stream()
            .filter(s -> !s.endsWith(":EMPTY") && !s.contains(":ERROR:"))
            .map(s -> s.substring(s.indexOf(":") + 1)) // 去掉位置前缀
            .sorted()
            .toList();
            
        List<String> afterFoci = afterState.stream()
            .filter(s -> !s.endsWith(":EMPTY") && !s.contains(":ERROR:"))
            .map(s -> s.substring(s.indexOf(":") + 1)) // 去掉位置前缀
            .sorted()
            .toList();
        
        if (!beforeFoci.equals(afterFoci)) {
            hasUnexpectedChanges = true;
            
            // 检查丢失的聚晶
            List<String> lost = new ArrayList<>(beforeFoci);
            lost.removeAll(afterFoci);
            if (!lost.isEmpty()) {
                warnings.add("聚晶丢失: " + String.join(", ", lost));
            }
            
            // 检查新增的聚晶
            List<String> gained = new ArrayList<>(afterFoci);
            gained.removeAll(beforeFoci);
            if (!gained.isEmpty()) {
                warnings.add("聚晶意外出现: " + String.join(", ", gained));
            }
        }
        
        // 发出警告
        if (hasUnexpectedChanges) {
            LOGGER.warn("⚠️ 警告：女仆 {} 的聚晶切换过程中发生意外变化！", maid.getUUID());
            for (String warning : warnings) {
                LOGGER.warn("  - {}", warning);
            }
            LOGGER.warn("切换前状态: {}", beforeState);
            LOGGER.warn("切换后状态: {}", afterState);
            LOGGER.warn("预期状态: {}", expectedAfterState);
        } else {
            LOGGER.debug("✅ 聚晶切换正常完成，无异常变化");
        }
    }
} 