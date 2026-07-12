package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法术书管理器
 * 用于管理和调用不同模组的法术书系统
 */
public class SpellBookManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int UNLOAD_RETENTION_TICKS = 100;
    private static final SpellBookManager INSTANCE = new SpellBookManager();
    private static final Map<UUID, Long> pendingRemovalDeadlines = new ConcurrentHashMap<>();
    
    // modid 和 provider 的映射关系 - 使用 LinkedHashMap 保持注册顺序
    private static final Map<String, ISpellBookProvider<?, ?>> providerMap = new ConcurrentHashMap<>();
    
    // 提供者列表的不可变视图，避免每次调用 getProviders 时创建新列表
    private static volatile List<ISpellBookProvider<?, ?>> immutableProviders = null;
    
    static {
        // 静态初始化：注册所有已知的提供者
        initializeProviderFactories();
    }

    /**
     * 初始化提供者工厂注册表
     * 这里注册所有已知的ISpellBookProvider实现
     */
    private static void initializeProviderFactories() {
        LOGGER.info("Initializing spell book provider factories...");

        // 仅在对应模组存在时通过反射加载 provider，避免可选依赖缺失时触发 NoClassDefFoundError
        registerProviderFactory("irons_spellbooks", "IronsSpellbooksProvider", "com.github.yimeng261.maidspell.spell.providers.IronsSpellbooksProvider");
        registerProviderFactory("ars_nouveau", "ArsNouveauProvider", "com.github.yimeng261.maidspell.spell.providers.ArsNouveauProvider");
        registerProviderFactory("psi", "PsiProvider", "com.github.yimeng261.maidspell.spell.providers.PsiProvider");
        registerProviderFactory("slashblade", "SlashBladeProvider", "com.github.yimeng261.maidspell.spell.providers.SlashBladeProvider");
        registerProviderFactory("goety", "GoetyProvider", "com.github.yimeng261.maidspell.spell.providers.GoetyProvider");
        registerProviderFactory("youkaishomecoming", "YoukaiHomecomingProvider", "com.github.yimeng261.maidspell.spell.providers.YoukaiHomecomingProvider");
        registerProviderFactory("ebwizardry", "WizardryProvider", "com.github.yimeng261.maidspell.spell.providers.WizardryProvider");
        registerProviderFactory("mna", "ManaAndArtificeProvider", "com.github.yimeng261.maidspell.spell.providers.ManaAndArtificeProvider");
        
    }
    
    /**
     * 通过类名安全注册提供者工厂
     * 这个方法避免了在静态初始化时直接引用可能不存在的类
     * 
     * @param modId 模组ID
     * @param providerName 提供者名称（用于日志）
     * @param providerClassName 提供者类全名
     */
    private static void registerProviderFactory(String modId, String providerName, String providerClassName) {
        try {
            // 检查模组是否加载
            if (ModList.get().isLoaded(modId)) {
                Class<?> providerClass = Class.forName(providerClassName);
                ISpellBookProvider<?, ?> provider = (ISpellBookProvider<?, ?>) providerClass.getConstructor().newInstance();
                providerMap.put(modId, provider);
                LOGGER.debug("Mod {} loaded, finished {} registration", modId, providerName);
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to register provider factory for mod {}: {}", modId, e.getMessage());
        }
    }
    
    
    private SpellBookManager() {
    }

    
    /**
     * Compatibility entry point. The manager is stateless with respect to individual maid entities.
     * 
     * @param maid 女仆实体
     * @return 该女仆对应的SpellBookManager实例
     */
    @NotNull
    public static SpellBookManager getOrCreateManager(EntityMaid maid) {
        return INSTANCE;
    }

    public static void clearAll() {
        pendingRemovalDeadlines.clear();
        for (ISpellBookProvider<?, ?> provider : INSTANCE.getProviders()) {
            try {
                provider.clearAllData();
            } catch (Exception e) {
                LOGGER.warn("Failed to clear spell provider {}", provider.getClass().getSimpleName(), e);
            }
        }
    }

    public void onMaidJoin(EntityMaid maid) {
        pendingRemovalDeadlines.remove(maid.getUUID());
        initSpellBooks(maid);
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            try {
                provider.onMaidJoin(maid);
            } catch (Exception e) {
                LOGGER.warn("Failed to bind spell provider {} for maid {}",
                        provider.getClass().getSimpleName(), maid.getUUID(), e);
            }
        }
    }

    public void onMaidLeave(EntityMaid maid, MinecraftServer server) {
        releaseMaidRuntimeReferences(maid);
        UUID maidId = maid.getUUID();
        if (hasMaidData(maidId)) {
            pendingRemovalDeadlines.put(maidId, (long) server.getTickCount() + UNLOAD_RETENTION_TICKS);
        }
    }

    public void removeMaidData(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        pendingRemovalDeadlines.remove(maidId);
        releaseMaidRuntimeReferences(maid);
        removeMaidData(maidId);
    }

    public static void tickPendingRemovals(MinecraftServer server) {
        long currentTick = server.getTickCount();
        pendingRemovalDeadlines.forEach((maidId, deadline) -> {
            if (deadline <= currentTick && pendingRemovalDeadlines.remove(maidId, deadline)) {
                INSTANCE.removeMaidData(maidId);
            }
        });
    }

    public void releaseMaidRuntimeReferences(EntityMaid maid) {
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            try {
                provider.releaseRuntimeReferences(maid);
            } catch (Exception e) {
                LOGGER.warn("Failed to release runtime state for provider {} and maid {}",
                        provider.getClass().getSimpleName(), maid.getUUID(), e);
            }
        }
    }

    private boolean hasMaidData(UUID maidId) {
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            try {
                if (provider.hasData(maidId)) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to inspect spell provider {} for maid {}",
                        provider.getClass().getSimpleName(), maidId, e);
            }
        }
        return false;
    }

    private void removeMaidData(UUID maidId) {
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            try {
                provider.removeData(maidId);
            } catch (Exception e) {
                LOGGER.warn("Failed to remove spell provider {} data for maid {}",
                        provider.getClass().getSimpleName(), maidId, e);
            }
        }
    }


    /**
     * 执行法术
     */
    public void castSpell(EntityMaid maid) {
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            provider.castSpell(maid);
        }
    }
    
    /**
     * 获取当前实例的提供者列表
     * 返回不可变列表，避免每次调用都创建新的 ArrayList
     */
    public List<ISpellBookProvider<?, ?>> getProviders() {
        if (immutableProviders == null) {
            synchronized (SpellBookManager.class) {
                if (immutableProviders == null) {
                    immutableProviders = Collections.unmodifiableList(new ArrayList<>(providerMap.values()));
                }
            }
        }
        return immutableProviders;
    }
    
    /**
     * 通过 modId 获取对应的 provider
     * 
     * @param modId 模组ID
     * @return 对应的 provider，如果不存在则返回 null
     */
    public static ISpellBookProvider<?, ?> getProvider(String modId) {
        return providerMap.get(modId);
    }
    
    /**
     * 检查是否存在指定 modId 的 provider
     * 
     * @param modId 模组ID
     * @return 是否存在
     */
    public static boolean hasProvider(String modId) {
        return providerMap.containsKey(modId);
    }
    
    /**
     * 获取所有已加载的 mod ID
     * 
     * @return 已加载的 mod ID 列表
     */
    public static List<String> getLoadedMods() {
        return new ArrayList<>(providerMap.keySet());
    }


    public void stopAllCasting(EntityMaid maid) {
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            try {
                if (provider.isCasting(maid)) {
                    provider.stopCasting(maid);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to stop provider {} for maid {}",
                        provider.getClass().getSimpleName(), maid.getUUID(), e);
            }
        }
    }


    /**
     * 更新法术冷却：每次一秒
     */
    public void updateCooldown(EntityMaid maid){
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            provider.updateCooldown(maid);
        }
    }

    public void refundCooldowns(EntityMaid maid, double refundRatio) {
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            provider.refundCooldowns(maid, refundRatio);
        }
    }


    public void tick(EntityMaid maid){
        // Clear stale attack targets before providers tick; otherwise long SlashBlade combos can keep swinging after a kill.
        LivingEntity attackTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (attackTarget != null && !isValidTarget(attackTarget)) {
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            stopAllCasting(maid);
            return;
        }

        // 处理持续性施法
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            LivingEntity providerTarget = provider.getTarget(maid);
            if(providerTarget != null){
                if (isValidTarget(providerTarget)) {
                    providerTarget.invulnerableTime = 0;
                } else {
                    // 即使 provider 没在施法，也必须清理旧 target；否则每 tick 都会重复看到同一个死亡实体。
                    provider.setTarget(maid, null);
                    if (provider.isCasting(maid)) {
                        if (provider.shouldStopWhenTargetInvalid(maid)) {
                            LOGGER.debug("[MaidSpell][Manager] stopping provider after target invalid maid={} tick={} provider={}",
                                    maid.getId(), maid.tickCount, provider.getClass().getSimpleName());
                            provider.stopCasting(maid);
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
            }
            provider.processContinuousCasting(maid);
        }

        // 每秒更新法术冷却
        if(maid.tickCount % 20 == 0){
            updateCooldown(maid);
        }
    }

    private static boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isDeadOrDying() && !target.isRemoved();
    }

    private static String describeTarget(LivingEntity target) {
        if (target == null) {
            return "null";
        }
        return target.getType() + "#" + target.getId() + " alive=" + target.isAlive()
                + " dying=" + target.isDeadOrDying() + " removed=" + target.isRemoved()
                + " hp=" + target.getHealth() + "/" + target.getMaxHealth();
    }

    public void initSpellBooks(EntityMaid maid){
        CombinedInvWrapper wrapper = maid.getAvailableInv(true);
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            try {
                provider.clearSpellItems(maid);
                for (int slot = 0; slot < wrapper.getSlots(); slot++) {
                    ItemStack itemStack = wrapper.getStackInSlot(slot);
                    provider.handleItemStack(maid, itemStack, true);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to rebuild spell books for provider {} and maid {}",
                        provider.getClass().getSimpleName(), maid.getUUID(), e);
            }
        }
    }

    public void removeSpellItem(EntityMaid maid, ItemStack itemStack) {
        LOGGER.debug("Removing spell item for maid {}", maid.getUUID());
        for(ISpellBookProvider<?,?> provider : getProviders()) {
            LOGGER.debug("Removing spell item for provider: {}", provider);
            provider.handleItemStack(maid, itemStack, false);
        }
    }

    public void addSpellItem(EntityMaid maid, ItemStack itemStack) {
        for(ISpellBookProvider<?,?> provider : getProviders()) {
            provider.handleItemStack(maid, itemStack, true);
        }
    }

}
