package com.github.yimeng261.maidspell.spell.providers;


import org.slf4j.Logger;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.data.MaidGoetySpellData;
import com.github.yimeng261.maidspell.utils.VersionUtil;
import com.mojang.logging.LogUtils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.IChargingSpell;
import com.Polarice3.Goety.common.items.magic.FocusBag;
import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.lang.reflect.Method;

/**
 * Goety模组法术书提供者 - 统一版本
 * 支持诡恶巫法的聚晶袋(FocusBag)系统
 * 将FocusBag视为法术书,直接从袋中读取聚晶法术
 * 全局只有一个实例，通过MaidGoetySpellData管理各女仆的数据
 * 自动检测Goety版本并使用对应的API调用方式
 */
public class GoetyProvider extends ISpellBookProvider<MaidGoetySpellData,ItemStack> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_INFINITE_CASTING_TIME = 45;
    private static final String NEW_API_MIN_VERSION = "2.5.37.0";
    
    // 缓存老版本API的Method，减少反射开销
    private static Method cachedStopSpellMethod = null;
    private static volatile boolean stopSpellMethodInitialized = false;
    
    // 缓存版本检测结果
    private static volatile Boolean useNewAPI = null;
    

    /**
     * 构造函数，绑定 MaidGoetySpellData 数据类型
     */
    public GoetyProvider() {
        super(MaidGoetySpellData::getOrCreate,ItemStack.class);
    }

    /**
     * 检测是否应该使用新版本API
     */
    private static boolean shouldUseNewAPI() {
        if (useNewAPI == null) {
            synchronized (GoetyProvider.class) {
                if (useNewAPI == null) {
                    useNewAPI = VersionUtil.isGoetyVersionSatisfied(NEW_API_MIN_VERSION);
                    LOGGER.info("Goety API version detection: using {} API", useNewAPI ? "new" : "old");
                }
            }
        }
        return useNewAPI;
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
        if (target == null || !target.isAlive()) {
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
        
        // 调用法术停止逻辑
        if (maid.level() instanceof ServerLevel serverLevel) {
            int remainingTime = data.getMaxCastingTime() - data.getCastingTime();
            stopSpellUnified(data.getCurrentSpell(), serverLevel, maid, data.getSpellBook(), remainingTime);
        }
        
        // 设置部分冷却
        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);

    }

    
    private ISpell selectSpell(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        
        // 调试：检查 spellBook
        if (data.getSpellBook() == null || data.getSpellBook().isEmpty()) {
            return null;
        }
        
        List<ItemStack> availableFoci = collectSpellFromAvailableSpellBooks(maid);

        if (availableFoci.isEmpty()) {
            return null;
        }
        
        // 随机选择一个可用的聚晶
        int randomIndex = (int) (Math.random() * availableFoci.size());
        ItemStack focusStack = availableFoci.get(randomIndex);
        if(focusStack.getItem() instanceof IFocus focus) {
            getData(maid).setCurrentFocus(focusStack);
            return focus.getSpell();
        }
        return null;
    }

    
    private void startSpellCasting(EntityMaid maid, ISpell spell) {
        if (spell.CastingSound() != null) {
            playSpellSound(maid, spell);
        }

        int castDuration = spell.defaultCastDuration();

        // 即时法术特殊处理（只有非蓄力法术才可能是即时的）
        if (castDuration <= 0 && !(spell instanceof IChargingSpell)) {
            executeInstantSpell(maid, spell);
            setCooldown(maid, spell);
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
        LivingEntity target = data.getTarget();
        
        // 只有当目标存在且存活时才更新朝向
        if (target != null && target.isAlive()) {
            BehaviorUtils.lookAtEntity(maid, target);
            
            if (data.getCurrentSpell() instanceof IChargingSpell) {
                updatePreciseOrientation(maid, data);
            }
        }
        // 如果目标不存在，保持当前朝向继续施法
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
    
    private void processSpellCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null) return;
        
        ISpell spell = data.getCurrentSpell();
        if (spell == null) return;
        
        // 通用部分：首次调用 useSpell
        if (maid.level() instanceof ServerLevel serverLevel && !data.spellUsed()) {
            spell.useSpell(serverLevel, maid, data.getSpellBook(), 
                data.getCastingTime(), spell.defaultStats());
            data.setSpellUsed(true);
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

        data.incrementCoolCounter();
        int requiredCooldown = chargingSpell.Cooldown(maid, data.getSpellBook(), data.getShotsFired());
        
        
        if (data.getCoolCounter() >= requiredCooldown) {
            data.setCoolCounter(0);
            executeChargingShot(maid, chargingSpell);
            int shootCount = chargingSpell.shotsNumber(maid, data.getSpellBook());
            if(shootCount > 0 && shootCount <= data.getShotsFired()){
                completeCasting(maid);
            }
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

    
    private void completeCasting(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        if (data == null || data.getCurrentSpell() == null) {
            return;
        }
        
        if (maid.level() instanceof ServerLevel serverLevel) {
            stopSpellUnified(data.getCurrentSpell(), serverLevel, maid, data.getSpellBook(), 0);
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
     * 统一的stopSpell方法调用，根据版本自动选择合适的API
     * 支持新旧两种Goety版本的API调用方式
     * 注意: FocusBag作为spellBook传入,focus参数使用空ItemStack
     */
    private void stopSpellUnified(ISpell spell, ServerLevel serverLevel, EntityMaid maid, ItemStack focusBag, int remainingTime) {
        if (shouldUseNewAPI()) {
            // 使用新版本API
            ItemStack focus = FocusBagItemHandler.get(focusBag).getSlot();
            spell.stopSpell(serverLevel, maid, focusBag, focus, remainingTime, spell.defaultStats());
            //LOGGER.debug("Successfully called new version stopSpell method for spell: {}", spell.getClass().getSimpleName());
        } else {
            // 使用老版本API
            stopSpellOldAPI(spell, serverLevel, maid, focusBag, remainingTime);
        }
    }
    
    /**
     * 调用老版本的stopSpell方法，使用缓存的Method减少反射开销
     */
    private void stopSpellOldAPI(ISpell spell, ServerLevel serverLevel, EntityMaid maid, ItemStack spellBook, int remainingTime) {
        try {
            // 初始化缓存的Method（只在第一次调用时执行）
            if (!stopSpellMethodInitialized) {
                synchronized (GoetyProvider.class) {
                    // 获取老版本的stopSpell方法签名
                    cachedStopSpellMethod = ISpell.class.getMethod("stopSpell",
                        ServerLevel.class, LivingEntity.class, ItemStack.class, int.class);
                    LOGGER.debug("Successfully cached old version stopSpell method");
                    stopSpellMethodInitialized = true;
                }
            }

            cachedStopSpellMethod.invoke(spell, serverLevel, maid, spellBook, remainingTime);
        } catch (Exception e) {
            LOGGER.error("Error calling old version stopSpell method for spell {}: {}", spell.getClass().getSimpleName(), e.getMessage());
        }
    }

} 