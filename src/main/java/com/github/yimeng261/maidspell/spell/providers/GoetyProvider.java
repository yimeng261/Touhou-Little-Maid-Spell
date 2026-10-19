package com.github.yimeng261.maidspell.spell.providers;


import org.slf4j.Logger;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.goety.VirtualGoetyWandItem;
import com.github.yimeng261.maidspell.compat.goety.VirtualWandManager;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.data.MaidGoetySpellData;
import com.mojang.logging.LogUtils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.IChargingSpell;
import com.Polarice3.Goety.common.items.magic.FocusBag;
import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.utils.WandUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/**
 * Goety模组法术书提供者
 * 支持诡恶巫法的聚晶袋(FocusBag)系统
 * 将FocusBag视为法术书,直接从袋中读取聚晶法术
 * 全局只有一个实例，通过MaidGoetySpellData管理各女仆的数据
 */
public class GoetyProvider extends ISpellBookProvider<MaidGoetySpellData,ItemStack> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_INFINITE_CASTING_TIME = 45;

    /**
     * 构造函数，绑定 MaidGoetySpellData 数据类型
     */
    public GoetyProvider() {
        super(MaidGoetySpellData::getOrCreate, MaidGoetySpellData::get,
                MaidGoetySpellData::remove, MaidGoetySpellData::clearAll, ItemStack.class);
    }


    @Override
    protected List<ItemStack> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<ItemStack> availableFoci = new ArrayList<>();
        MaidGoetySpellData data = getData(maid);
        FocusBagItemHandler bagHandler = FocusBagItemHandler.get(spellBook);
        // 遍历FocusBag中所有槽位的聚晶
        for (int i = 0; i < bagHandler.getSlots(); i++) {
            ItemStack focusStack = bagHandler.getStackInSlot(i);
            if (!focusStack.isEmpty() && focusStack.getItem() instanceof IFocus focus) {
                ISpell spell = focus.getSpell();
                if (spell != null && !data.isSpellOnCooldown(getSpellId(spell))) {
                    availableFoci.add(focusStack);
                    data.addFocusSource(spellBook, i, focusStack);
                }
            }
        }

        return availableFoci;
    }

    /**
     * 检查物品是否为法术书
     */
    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof FocusBag;
    }

    /**
     * 开始施法
     */
    @Override
    public void initiateCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);

        
        // 检查目标（只在开始施法时检查一次）
        LivingEntity target = data.getTarget();
        if (!IAuthoritativeHealth.combatAlive(target)) {
            return;
        }
        
        ISpell spell = selectSpell(maid);
        if (spell == null) {
            return;
        }

        updateMaidOrientation(maid, data);
        startSpellCasting(maid, spell);
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
        
        // 更新朝向（如果目标仍然存在）
        updateMaidOrientation(maid, data);
        processSpellCasting(maid);
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
        
        if (maid.level() instanceof ServerLevel serverLevel) {
            ISpell spell = data.getCurrentSpell();
            ItemStack focus = getCurrentFocus(data);
            ItemStack staff = getCurrentStaff(data);
            if (!isUsableStaff(staff)) {
                resetCastingState(maid, data);
                return;
            }
            // Goety 的 stopSpell 需要已施法时间，而不是剩余时间。
            spell.stopSpell(serverLevel, maid, staff, focus, data.getCastingTime(), getSpellStats(maid, spell));
            flushFocus(staff);
        }
        
        // 设置部分冷却
        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);

    }

    
    private ISpell selectSpell(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        
        // 调试：检查 spellBook
        if (data.getSpellBook() == null || data.getSpellBook().isEmpty()) {
            data.clearFocusSources();
            return null;
        }

        // 上一轮如果异常中断，旧上下文不能继续存活到下一次施法。
        if (data.getVirtualWandId() != null) {
            VirtualWandManager.release(data.getVirtualWandId());
            data.setVirtualWandId(null);
        }

        data.beginFocusCollection();
        List<ItemStack> availableFoci = collectSpellFromAvailableSpellBooks(maid);

        if (availableFoci.isEmpty()) {
            data.clearFocusSources();
            return null;
        }
        
        // 随机选择一个可用的聚晶
        int randomIndex = (int) (Math.random() * availableFoci.size());
        ItemStack focusStack = availableFoci.get(randomIndex);
        if(focusStack.getItem() instanceof IFocus focus) {
            ISpell spell = focus.getSpell();
            data.setCurrentFocus(focusStack);
            MaidGoetySpellData.FocusSource source = data.getFocusSource(randomIndex);
            if (source == null) {
                data.setCurrentFocus(ItemStack.EMPTY);
                data.clearFocusSources();
                return null;
            }
            ItemStack staff = findHeldStaff(maid);
            if (!isUsableStaff(staff)) {
                // No real wand in either hand: use a hidden IWand whose focus
                // capability proxies the selected FocusBag slot.
                VirtualWandManager.Handle virtualWand = VirtualWandManager.create(
                        maid, source.bag(), source.slot(), focusStack);
                if (virtualWand == null) {
                    data.setCurrentFocus(ItemStack.EMPTY);
                    data.clearFocusSources();
                    return null;
                }
                staff = virtualWand.stack();
                data.setVirtualWandId(virtualWand.id());
            } else {
                data.setVirtualWandId(null);
            }
            data.setCurrentStaff(staff);
            data.clearFocusSources();
            return spell;
        }
        data.clearFocusSources();
        return null;
    }

    
    private void startSpellCasting(EntityMaid maid, ISpell spell) {
        MaidGoetySpellData data = getData(maid);
        ItemStack staff = getCurrentStaff(data);
        if (!isUsableStaff(staff)) {
            // 双保险：施法开始前再次确认法杖仍然存在，避免中途换手后把聚晶包
            // 或空栈传给 Goety 的法术实现。
            resetCastingState(maid, data);
            return;
        }

        if (spell.CastingSound() != null) {
            maid.level().playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                    Objects.requireNonNull(spell.CastingSound()), SoundSource.HOSTILE,
                    spell.castingVolume(), spell.castingPitch());
        }

        int castDuration = spell.castDuration(maid, staff);

        // 即时法术特殊处理（只有非蓄力法术才可能是即时的）
        if (castDuration <= 0 && !(spell instanceof IChargingSpell)) {
            executeInstantSpell(maid, spell);
            setCooldown(maid, spell);
            resetCastingState(maid, data);
            return;
        }

        // 蓄力法术的特殊处理
        if (spell instanceof IChargingSpell chargingSpell) {
            // 无限蓄力时间限制
            if (chargingSpell.everCharge()) {
                castDuration = MAX_INFINITE_CASTING_TIME;
            }
            // 重置蓄力计数器
            resetChargingCounters(maid);
        }

        // 通用逻辑
        initiateCastingState(maid, spell, castDuration);
        startSpellExecution(maid, spell);
    }

    
    private void executeInstantSpell(EntityMaid maid, ISpell spell) {
        if (maid.level() instanceof ServerLevel serverLevel) {
            ItemStack staff = getCurrentStaff(getData(maid));
            if (!isUsableStaff(staff)) {
                return;
            }
            SpellStat spellStat = getSpellStats(maid, spell);
            spell.startSpell(serverLevel, maid, staff, spellStat);
            flushFocus(staff);
            spell.SpellResult(serverLevel, maid, staff, spellStat);
            flushFocus(staff);
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
            ItemStack staff = getCurrentStaff(getData(maid));
            if (!isUsableStaff(staff)) {
                resetCastingState(maid, getData(maid));
                return;
            }
            spell.startSpell(serverLevel, maid, staff, getSpellStats(maid, spell));
            flushFocus(staff);
        }
    }
    
    private void updateMaidOrientation(EntityMaid maid, MaidGoetySpellData data) {
        LivingEntity target = data.getTarget();
        
        // 只有当目标存在且存活时才更新朝向
        if (IAuthoritativeHealth.combatAlive(target)) {
            BehaviorUtils.lookAtEntity(maid, target);
            // Goety 的投射物会立即读取 caster.getViewVector；非蓄力瞬发法术也必须同步精确朝向。
            updatePreciseOrientation(maid, data);
        }
        // 如果目标不存在，保持当前朝向继续施法
    }
    
    private void updatePreciseOrientation(EntityMaid maid, MaidGoetySpellData data) {
        SpellProviderUtils.orientToTarget(maid, data.getTarget());
    }
    
    private void processSpellCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        ISpell spell = data.getCurrentSpell();
        if (spell == null) return;

        ItemStack staff = getCurrentStaff(data);
        if (!isUsableStaff(staff)) {
            // The wand can be unequipped while a cast is in progress. Goety's
            // spell callbacks assume a real IWand and may read its focus handler.
            resetCastingState(maid, data);
            return;
        }
        
        // Goety 原版 DarkWand 会在每个使用 tick 驱动 useSpell，持续类法术依赖这个节奏。
        if (maid.level() instanceof ServerLevel serverLevel) {
            spell.useSpell(serverLevel, maid, staff, data.getCastingTime(), getSpellStats(maid, spell));
            flushFocus(staff);
        }
        
        // 通用部分：检查时间限制
        if (data.getCastingTime() >= data.getMaxCastingTime()) {
            completeCasting(maid);
            return;
        }
        
        // 差异化处理：仅对 chargingSpell 执行额外逻辑
        if (spell instanceof IChargingSpell chargingSpell) {
            handleChargingLogic(maid, chargingSpell);
        }
    }
    
    
    private void handleChargingLogic(EntityMaid maid, IChargingSpell chargingSpell) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;

        ItemStack staff = getCurrentStaff(data);
        if (!isUsableStaff(staff)) {
            resetCastingState(maid, data);
            return;
        }
        int castUp = chargingSpell.castUp(maid, staff);
        if (castUp > 0 && data.getCastingTime() < castUp) {
            return;
        }

        data.incrementCoolCounter();
        int requiredCooldown = chargingSpell.Cooldown(maid, staff, data.getShotsFired());

        // 蓄力完成后按 Goety 的 Cooldown 节奏触发每一段 SpellResult。
        if (data.getCoolCounter() >= requiredCooldown) {
            data.setCoolCounter(0);
            executeChargingShot(maid, chargingSpell);
            int shootCount = chargingSpell.shotsNumber(maid, staff);
            if (shootCount > 0 && data.getShotsFired() >= shootCount) {
                completeCasting(maid);
            }
        }
    }

    private void executeChargingShot(EntityMaid maid, IChargingSpell chargingSpell) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;

        LOGGER.debug("执行蓄力法术射击: {}, 女仆: {}", chargingSpell.getClass().getSimpleName(), maid.getName().getString());
        ItemStack staff = getCurrentStaff(data);
        if (!isUsableStaff(staff)) {
            resetCastingState(maid, data);
            return;
        }
        // 与 DarkWand.increaseShots 保持一致，多段法术需要递增后才能按 shotsNumber 收束。
        if (chargingSpell.shotsNumber(maid, staff) > 0) {
            data.incrementShotsFired();
        }
        if (maid.level() instanceof ServerLevel serverLevel) {
            chargingSpell.SpellResult(serverLevel, maid, staff, getSpellStats(maid, chargingSpell));
            flushFocus(staff);
        }

    }

    
    private void completeCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null || data.getCurrentSpell() == null) {
            return;
        }

        if (maid.level() instanceof ServerLevel serverLevel) {
            ISpell spell = data.getCurrentSpell();
            ItemStack focus = getCurrentFocus(data);
            ItemStack staff = getCurrentStaff(data);
            if (!isUsableStaff(staff)) {
                resetCastingState(maid, data);
                return;
            }
            // castTime 对齐 DarkWand.releaseUsing 的语义：从开始施法到停止时已经经过的 tick。
            spell.stopSpell(serverLevel, maid, staff, focus, data.getCastingTime(), getSpellStats(maid, spell));
            flushFocus(staff);
            // 蓄力法术已在 Cooldown 节奏中触发 SpellResult，完成时不再额外补打一发。
            if (!(spell instanceof IChargingSpell)) {
                spell.SpellResult(serverLevel, maid, staff,
                    getSpellStats(maid, spell));
                flushFocus(staff);
            }
            SpringBloomReturnBauble.onSpellCast(
                maid,
                "goety",
                getSpellId(data.getCurrentSpell()),
                data.getTarget()
            );
        }

        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);
    }

    
    private void resetCastingState(EntityMaid maid, MaidGoetySpellData data) {
        if (data != null) {
            VirtualWandManager.release(data.getVirtualWandId());
            data.setVirtualWandId(null);
            data.resetCastingState();
        }
        maid.stopUsingItem();
    }
    
    private String getSpellId(ISpell spell) {
        return spell.getClass().getSimpleName();
    }
    
    private void setCooldown(EntityMaid maid, ISpell spell) {
        MaidGoetySpellData data = getData(maid);
        if (data != null && spell != null) {
            String spellId = getSpellId(spell);
            int cooldown = spell.spellCooldown(maid);
            if (spell instanceof IChargingSpell chargingSpell) {
                cooldown = getChargingCooldown(maid, data, chargingSpell, cooldown);
            }
            data.setSpellCooldown(spellId, cooldown, maid);
        }
    }

    private int getChargingCooldown(EntityMaid maid, MaidGoetySpellData data, IChargingSpell spell, int fullCooldown) {
        ItemStack staff = getCurrentStaff(data);
        if (!isUsableStaff(staff)) {
            return fullCooldown;
        }
        int shotCount = spell.shotsNumber(maid, staff);
        if (shotCount <= 0) {
            return fullCooldown;
        }
        float coolPercent = Math.min(1.0F, (float) data.getShotsFired() / (float) shotCount);
        // Goety 原版多段蓄力按已发射段数折算冷却，未发射时至少保持 0。
        return Math.max(0, Math.round(fullCooldown * coolPercent));
    }

    private SpellStat getSpellStats(EntityMaid maid, ISpell spell) {
        // 使用 Goety 的属性汇总入口，保留施法者属性、饰品和附魔等加成。
        return WandUtil.getStats(maid, spell);
    }

    @Override
    public void removeData(UUID maidId) {
        VirtualWandManager.releaseMaid(maidId);
        super.removeData(maidId);
    }

    @Override
    public void clearAllData() {
        VirtualWandManager.clearAll();
        super.clearAllData();
    }

    @Override
    protected void releaseProviderRuntimeReferences(EntityMaid maid, MaidGoetySpellData data) {
        if (data != null) {
            VirtualWandManager.release(data.getVirtualWandId());
            data.setVirtualWandId(null);
        }
        super.releaseProviderRuntimeReferences(maid, data);
    }


    private ItemStack getCurrentFocus(MaidGoetySpellData data) {
        return data != null && data.getCurrentFocus() != null ? data.getCurrentFocus() : ItemStack.EMPTY;
    }

    /**
     * Only a virtual wand needs this; for a real wand it is a no-op lookup.
     * Keeps focus NBT changes made by a spell inside the FocusBag slot.
     */
    private void flushFocus(ItemStack staff) {
        VirtualWandManager.flush(staff);
    }

    private ItemStack getCurrentStaff(MaidGoetySpellData data) {
        if (data != null && isUsableStaff(data.getCurrentStaff())) {
            return data.getCurrentStaff();
        }
        // 聚晶包不是法杖。无杖时必须返回空栈，不能把 FocusBag 传给 Goety。
        return ItemStack.EMPTY;
    }

    private ItemStack findHeldStaff(EntityMaid maid) {
        // 手部法杖优先：即使聚晶与当前选中的聚晶不同，也使用女仆
        // 实际拿着的真实法杖，让法杖提供其学派加成。
        ItemStack mainHand = maid.getMainHandItem();
        if (isRealWand(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = maid.getOffhandItem();
        if (isRealWand(offHand)) {
            return offHand;
        }

        // 背包里的法杖不参与选择；没有手部法杖时一律使用虚拟法杖。
        return ItemStack.EMPTY;
    }

    private boolean isRealWand(ItemStack stack) {
        return isUsableStaff(stack) && !(stack.getItem() instanceof VirtualGoetyWandItem);
    }

    private boolean isUsableStaff(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IWand;
    }


} 
