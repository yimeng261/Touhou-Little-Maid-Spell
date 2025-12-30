package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;

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
    
    // 女仆管理器实例缓存 - 使用ConcurrentHashMap保证线程安全
    private static final Map<UUID, SpellBookManager> MAID_MANAGERS = new ConcurrentHashMap<>();

    
    // 实例相关的提供者列表 - 每个管理器可以有自己的提供者实例
    private static final List<ISpellBookProvider<?, ?>> instanceProviders = new ArrayList<>();
    
    // 提供者列表的不可变视图，避免每次调用 getProviders 时创建新列表
    private static volatile List<ISpellBookProvider<?, ?>> immutableProviders = null;

    public static final List<String> loadedMods = new ArrayList<>();
    
    // 女仆实体存储为每个管理器的上下文
    private EntityMaid maid;

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

        // 使用传统方式注册（无版本检测）
        registerProviderFactoryByClass("irons_spellbooks", "IronsSpellbooksProvider", com.github.yimeng261.maidspell.spell.providers.IronsSpellbooksProvider.class);
        registerProviderFactoryByClass("ars_nouveau", "ArsNouveauProvider", com.github.yimeng261.maidspell.spell.providers.ArsNouveauProvider.class);
        registerProviderFactoryByClass("psi","PsiProvider", com.github.yimeng261.maidspell.spell.providers.PsiProvider.class);
        registerProviderFactoryByClass("slashblade","SlashBladeProvider", com.github.yimeng261.maidspell.spell.providers.SlashBladeProvider.class);
        registerProviderFactoryByClass("goety", "GoetyProvider", com.github.yimeng261.maidspell.spell.providers.GoetyProvider.class);
        registerProviderFactoryByClass("youkaishomecoming", "YoukaiHomecomingProvider", com.github.yimeng261.maidspell.spell.providers.YoukaiHomecomingProvider.class);
        
    }
    
    /**
     * 通过类名安全注册提供者工厂
     * 这个方法避免了在静态初始化时直接引用可能不存在的类
     * 
     * @param modId 模组ID
     * @param providerName 提供者名称（用于日志）
     * @param providerClass 提供者类
     */
    private static void registerProviderFactoryByClass(String modId, String providerName, Class<?> providerClass) {
        try {
            // 检查模组是否加载
            if (ModList.get().isLoaded(modId)) {
                instanceProviders.add((ISpellBookProvider<?, ?>) providerClass.getConstructor().newInstance());
                loadedMods.add(modId);
                LOGGER.debug("Mod {} loaded, finished {} registration", modId, providerName);
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to register provider factory for mod {}: {}", modId, e.getMessage());
        }
    }
    
    
    /**
     * 为特定女仆创建管理器实例（私有构造函数）
     */
    private SpellBookManager(EntityMaid maid) {
        this.maid = maid;
    }

    
    /**
     * 获取或创建女仆的管理器实例
     * 如果该女仆已有管理器实例，则返回现有的；否则创建新的
     * 
     * @param maid 女仆实体
     * @return 该女仆对应的SpellBookManager实例
     */
    @NotNull
    public static SpellBookManager getOrCreateManager(EntityMaid maid) {
        UUID maidUUID = maid.getUUID();
        
        // 使用computeIfAbsent确保线程安全且避免重复创建
        return MAID_MANAGERS.computeIfAbsent(maidUUID, uuid -> {
            LOGGER.debug("Creating new SpellBookManager for maid {}", uuid);
            return new SpellBookManager(maid);
        });
    }
    
    /**
     * 移除女仆的管理器实例（当女仆被移除时调用）
     *
     * @param maid 女仆实体
     */
    public static void removeManager(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        
        UUID maidUUID = maid.getUUID();
        SpellBookManager removed = MAID_MANAGERS.remove(maidUUID);
        
        if (removed != null) {
            LOGGER.debug("Removed SpellBookManager for maid {}", maidUUID);
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
                    immutableProviders = Collections.unmodifiableList(new ArrayList<>(instanceProviders));
                }
            }
        }
        return immutableProviders;
    }


    public void stopAllCasting() {
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            if (provider.isCasting(maid)) {
                provider.stopCasting(maid);
            }
        }
    }
    
    /**
     * 获取关联的女仆实体
     */
    public EntityMaid getMaid() {
        return maid;
    }

    public void setMaid(EntityMaid maid) {
        this.maid = maid;
    }


    /**
     * 更新法术冷却：每次一秒
     */
    public void updateCooldown(){
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            provider.updateCooldown(maid);
        }
    }


    public void tick(){
        // 处理持续性施法
        
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            if(provider.getTarget(maid) != null){
                provider.getTarget(maid).invulnerableTime = 0;
            }
            provider.processContinuousCasting(maid);
        }

        // 每秒更新法术冷却
        if(maid.tickCount % 20 == 0){
            updateCooldown();
        }
    }

    public void initSpellBooks(){
        // 先清理所有法术容器
        for (ISpellBookProvider<?, ?> provider : getProviders()) {
            provider.clearSpellItems(maid);
        }
        
        CombinedInvWrapper wrapper = maid.getAvailableInv(true);

        for(int i=0;i< wrapper.getSlots();i++){
            ItemStack itemStack = wrapper.getStackInSlot(i);
            // 更新每个提供者的法术书
            for (ISpellBookProvider<?, ?> provider : getProviders()) {
                provider.handleItemStack(maid, itemStack, true);
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