package com.github.yimeng261.maidspell.spell.providers;


import com.github.tartaricacid.touhoulittlemaid.inventory.container.backpack.BaubleContainer;
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
public class GoetyProvider extends ISpellBookProvider<MaidGoetySpellData> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_INFINITE_CASTING_TIME = 45;
    private static final boolean DEBUG = true;
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
        super(MaidGoetySpellData::getOrCreate);
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

    // === 核心方法（接受EntityMaid参数） ===
    
    /**
     * 检查物品是否为法术书 - 将FocusBag视为法术书
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
        ISpell spell = selectSpell(maid);
        if (spell == null) {
            return;
        }

        updateMaidOrientation(maid,getData(maid));
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
            int remainingTime = data.getMaxCastingTime() - data.getCastingTime();
            stopSpellUnified(data.getCurrentSpell(), serverLevel, maid, data.getSpellBook(), remainingTime);
        }
        
        // 设置部分冷却
        setCooldown(maid, data.getCurrentSpell());
        resetCastingState(maid, data);

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


    private boolean isValidTarget(MaidGoetySpellData data) {
        LivingEntity target = data.getTarget();
        return target != null && target.isAlive();
    }
    
    private ISpell selectSpell(EntityMaid maid) {
        MaidGoetySpellData data = getData(maid);
        List<IFocus> availableFoci = collectAllAvailableFoci(maid, data, data.getSpellBook());
        
        if (availableFoci.isEmpty()) {
            return null;
        }
        
        // 随机选择一个可用的聚晶
        int randomIndex = (int) (Math.random() * availableFoci.size());
        return availableFoci.get(randomIndex).getSpell();
    }
    
    private List<IFocus> collectAllAvailableFoci(EntityMaid maid, MaidGoetySpellData data, ItemStack focusBag) {
        List<IFocus> availableFoci = new ArrayList<>();


        try {
            FocusBagItemHandler bagHandler = FocusBagItemHandler.get(focusBag);
            // 遍历FocusBag中所有槽位的聚晶
            for (int i = 0; i < bagHandler.getSlots(); i++) {
                ItemStack focusStack = bagHandler.getStackInSlot(i);
                if (!focusStack.isEmpty() && focusStack.getItem() instanceof IFocus focus) {
                    ISpell spell = focus.getSpell();
                    if (spell != null && !data.isSpellOnCooldown(getSpellId(spell))) {
                        availableFoci.add(focus);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to access focus bag handler for maid: {}", maid.getUUID(), e);
        }
        
        return availableFoci;
    }

    
    private void startSpellCasting(EntityMaid maid, ISpell spell) {
        if (spell.CastingSound() != null) {
            playSpellSound(maid, spell);
        }
        
        if (spell instanceof IChargingSpell chargingSpell) {
            startChargingSpell(maid, chargingSpell);
        } else {
            startNormalSpell(maid, spell);
        }
    }
    
    private void startNormalSpell(EntityMaid maid, ISpell spell) {
        int castDuration = spell.defaultCastDuration();
        if (castDuration <= 0) {
            executeInstantSpell(maid, spell);
            setCooldown(maid, spell);
        } else {        
            initiateCastingState(maid, spell, castDuration);
            startSpellExecution(maid, spell);
        }
    }
    
    private void startChargingSpell(EntityMaid maid, IChargingSpell chargingSpell) {
        int maxDuration = chargingSpell.defaultCastDuration();
        
        if (chargingSpell.everCharge()) {
            maxDuration = Math.min(maxDuration, MAX_INFINITE_CASTING_TIME);
        }
        
        initiateCastingState(maid, chargingSpell, maxDuration);
        resetChargingCounters(maid);
        startSpellExecution(maid, chargingSpell);

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
            try {
                // FocusBag本身作为spellBook,focus参数传入空ItemStack
                ItemStack focus = FocusBagItemHandler.get(focusBag).getSlot();
                spell.stopSpell(serverLevel, maid, focusBag, focus, remainingTime, spell.defaultStats());
                //LOGGER.debug("Successfully called new version stopSpell method for spell: {}", spell.getClass().getSimpleName());
            } catch (Exception e) {
                LOGGER.error("Error calling new version stopSpell method for spell {}: {}", spell.getClass().getSimpleName(), e.getMessage());
                // 如果新版本API调用失败，尝试回退到老版本API
                stopSpellOldAPI(spell, serverLevel, maid, focusBag, remainingTime);
            }
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
                    if (!stopSpellMethodInitialized) {
                        try {
                            // 获取老版本的stopSpell方法签名
                            cachedStopSpellMethod = ISpell.class.getMethod("stopSpell", 
                                ServerLevel.class, LivingEntity.class, ItemStack.class, int.class);
                            LOGGER.debug("Successfully cached old version stopSpell method");
                        } catch (NoSuchMethodException e) {
                            LOGGER.warn("Could not find old version stopSpell method, this may cause issues with old Goety versions");
                            cachedStopSpellMethod = null;
                        }
                        stopSpellMethodInitialized = true;
                    }
                }
            }
            
            // 使用缓存的Method调用
            if (cachedStopSpellMethod != null) {
                cachedStopSpellMethod.invoke(spell, serverLevel, maid, spellBook, remainingTime);
                //LOGGER.debug("Successfully called old version stopSpell method for spell: {}", spell.getClass().getSimpleName());
            } else {
                LOGGER.warn("stopSpell method not available for spell: {}", spell.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Error calling old version stopSpell method for spell {}: {}", spell.getClass().getSimpleName(), e.getMessage());
        }
    }

} 