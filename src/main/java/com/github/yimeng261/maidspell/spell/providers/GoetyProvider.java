package com.github.yimeng261.maidspell.spell.providers;

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
    private static final int MAX_TARGET_DISTANCE = 32;
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
            ItemStack newSpellBook = spellBook==null ? ItemStack.EMPTY : spellBook.copy();
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
        restoreOriginalFocus(maid, data);
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
    
    private ISpell extractSpellFromWand(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) {
            return null;
        }
        
        ItemStack spellBook = data.getSpellBook();
        
        // 收集所有可用的聚晶
        List<ItemStack> availableFoci = collectAllAvailableFoci(maid, data, spellBook);
        
        if (availableFoci.isEmpty()) {
            return null;
        }
        
        // 随机选择一个聚晶
        int randomIndex = (int) (Math.random() * availableFoci.size());
        ItemStack selectedFocus = availableFoci.get(randomIndex);
        
        // 切换聚晶（如果需要）
        ItemStack currentFocus = IWand.getFocus(spellBook);
        if (!ItemStack.isSameItemSameTags(currentFocus, selectedFocus)) {
            if (!switchToFocus(maid, data, selectedFocus, spellBook)) {
                return null;
            }
        }
        
        // 返回法术
        if (selectedFocus.getItem() instanceof IFocus magicFocus) {
            return magicFocus.getSpell();
        }
        
        return null;
    }
    
    private List<ItemStack> collectAllAvailableFoci(EntityMaid maid, MaidGoetySpellData data, ItemStack spellBook) {
        List<ItemStack> availableFoci = new ArrayList<>();
        
        // 检查当前法杖上的聚晶
        ItemStack currentFocus = IWand.getFocus(spellBook);
        if (!currentFocus.isEmpty() && currentFocus.getItem() instanceof IFocus) {
            IFocus focus = (IFocus) currentFocus.getItem();
            ISpell spell = focus.getSpell();
            if (spell != null && !data.isSpellOnCooldown(getSpellId(spell))) {
                availableFoci.add(currentFocus.copy());
            }
        }
        
        // 检查聚晶包中的聚晶
        ItemStack focusBag = findFocusBag(maid);
        if (!focusBag.isEmpty()) {
            try {
                FocusBagItemHandler bagHandler = FocusBagItemHandler.get(focusBag);
                for (int i = 1; i < bagHandler.getSlots(); i++) {
                    ItemStack focusStack = bagHandler.getStackInSlot(i);
                    if (!focusStack.isEmpty() && focusStack.getItem() instanceof IFocus) {
                        IFocus focus = (IFocus) focusStack.getItem();
                        ISpell spell = focus.getSpell();
                        if (spell != null && !data.isSpellOnCooldown(getSpellId(spell))) {
                            boolean isDuplicate = availableFoci.stream()
                                .anyMatch(existing -> ItemStack.isSameItemSameTags(existing, focusStack));
                            if (!isDuplicate) {
                                availableFoci.add(focusStack.copy());
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
        
        return availableFoci;
    }
    
    private ItemStack findFocusBag(EntityMaid maid) {
        if (maid == null) {
            return ItemStack.EMPTY;
        }
        
        var availableInv = maid.getAvailableInv(false);
        for (int i = 0; i < availableInv.getSlots(); i++) {
            ItemStack stack = availableInv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FocusBag) {
                return stack;
            }
        }
        
        return ItemStack.EMPTY;
    }
    
    private boolean switchToFocus(EntityMaid maid, MaidGoetySpellData data, ItemStack newFocus, ItemStack spellBook) {
        if (maid == null || !isSpellBook(spellBook) || newFocus.isEmpty()) {
            return false;
        }
        
        try {
            if (data.getOriginalFocus().isEmpty()) {
                data.setOriginalFocus(IWand.getFocus(spellBook).copy());
            }
            
            SoulUsingItemHandler wandHandler = SoulUsingItemHandler.get(spellBook);
            wandHandler.setStackInSlot(0, newFocus.copy());
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void prepareForCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data != null && data.getTarget() != null) {
            maid.getLookControl().setLookAt(data.getTarget(), 30.0F, 30.0F);
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
            maid.getLookControl().setLookAt(data.getTarget(), 30.0F, 30.0F);
            
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

        if (!hasWandInMainHand && !hasWandInOffHand) {
            swapItem(maid, data.getSpellBook(), originalOffHand);
        }

        maid.startUsingItem(wandHand);
    }
    
    private void swapItem(EntityMaid maid, ItemStack targetItem, ItemStack sourceItem) {
        if (maid == null) return;
        
        if (targetItem == null) targetItem = ItemStack.EMPTY;
        if (sourceItem == null) sourceItem = ItemStack.EMPTY;
        
        int ti = -1, si = -1;
        var availableInv = maid.getAvailableInv(true);
        
        for (int i = 0; i < availableInv.getSlots(); i++) {
            ItemStack stackInSlot = availableInv.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(stackInSlot, sourceItem)) {
                si = i;
            }
            if (ItemStack.isSameItemSameTags(stackInSlot, targetItem)) {
                ti = i;
            }
            if (ti != -1 && si != -1) {
                break;
            }
        }
        
        if (ti != -1) {
            availableInv.setStackInSlot(ti, sourceItem);
        }
        if (si != -1) {
            availableInv.setStackInSlot(si, targetItem);
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
        restoreOriginalFocus(maid, data);
        resetCastingState(maid, data);
    }
    
    private void restoreOriginalFocus(EntityMaid maid, MaidGoetySpellData data) {
        if (!data.getOriginalFocus().isEmpty() && isSpellBook(data.getSpellBook())) {
            try {
                SoulUsingItemHandler wandHandler = SoulUsingItemHandler.get(data.getSpellBook());
                wandHandler.setStackInSlot(0, data.getOriginalFocus().copy());
                data.setOriginalFocus(ItemStack.EMPTY);
            } catch (Exception e) {
            }
        }
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
            data.setSpellCooldown(spellId, cooldown);
        }
    }
} 