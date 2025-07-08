package com.github.yimeng261.maidspell.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 法术书管理器
 * 用于管理和调用不同模组的法术书系统
 */
public class SpellBookManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 女仆管理器实例缓存 - 使用ConcurrentHashMap保证线程安全
    private static final Map<UUID, SpellBookManager> MAID_MANAGERS = new ConcurrentHashMap<>();
    
    // 全局提供者工厂注册表 - 存储提供者类的构造函数
    private static final Map<String, Supplier<ISpellBookProvider>> PROVIDER_FACTORIES = new HashMap<>();
    
    // 实例相关的提供者列表 - 每个管理器可以有自己的提供者实例
    private static final List<ISpellBookProvider> instanceProviders = new ArrayList<>();
    
    // 女仆实体存储为每个管理器的上下文
    private EntityMaid maid;

    static {
        // 静态初始化：注册所有已知的提供者
        initializeProviderFactories();
        createProviderInstances();
    }

    /**
     * 初始化提供者工厂注册表
     * 这里注册所有已知的ISpellBookProvider实现
     */
    private static void initializeProviderFactories() {
        LOGGER.info("Initializing spell book provider factories...");

        registerProviderFactoryByClassName("irons_spellbooks", "IronsSpellbooksProvider", 
            "com.github.yimeng261.maidspell.providers.IronsSpellbooksProvider");

        registerProviderFactoryByClassName("ars_nouveau", "ArsNouveauProvider",
                "com.github.yimeng261.maidspell.providers.ArsNouveauProvider");

        registerProviderFactoryByClassName("goety", "GoetyProvider",
                "com.github.yimeng261.maidspell.providers.GoetyProvider");

        registerProviderFactoryByClassName("psi","PsiProvider",
                "com.github.yimeng261.maidspell.providers.PsiProvider");

        registerProviderFactoryByClassName("slashblade","SlashBladeProvider",
                "com.github.yimeng261.maidspell.providers.SlashBladeProvider");

        LOGGER.info("Registered {} spell book provider factories", PROVIDER_FACTORIES.size());
    }
    
    /**
     * 通过类名安全注册提供者工厂
     * 这个方法避免了在静态初始化时直接引用可能不存在的类
     * 
     * @param modId 模组ID
     * @param providerName 提供者名称（用于日志）
     * @param className 提供者类的完整类名
     */
    private static void registerProviderFactoryByClassName(String modId, String providerName, String className) {
        try {
            // 检查模组是否加载
            if (!ModList.get().isLoaded(modId)) {
                LOGGER.debug("Mod {} not loaded, skipping {} registration", modId, providerName);
                return;
            }
            
            // 检查类是否可用 - 使用更安全的方式
            Class<?> providerClass;
            try {
                providerClass = Class.forName(className);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                LOGGER.warn("Provider class {} or its dependencies not found for mod {}, skipping registration: {}", 
                           className, modId, e.getMessage());
                return;
            }


            Supplier<ISpellBookProvider> factory = () -> {
                try {
                    return (ISpellBookProvider) providerClass.getConstructor().newInstance();
                } catch (Exception e) {
                    LOGGER.error("Failed to create instance of {}: {}", className, e.getMessage());
                    return null;
                }
            };
            
            // 注册工厂
            PROVIDER_FACTORIES.put(modId, factory);
            LOGGER.info("Registered spell book provider factory: {} for mod {}", providerName, modId);
            
        } catch (Exception e) {
            LOGGER.error("Failed to register provider factory for mod {}: {}", modId, e.getMessage());
        }
    }
    
    
    /**
     * 动态注册外部提供者工厂
     * 允许其他模组在运行时注册自己的提供者
     * 
     * @param modId 模组ID
     * @param factory 提供者工厂函数
     * @return 如果注册成功返回true，否则返回false
     */
    public static boolean registerExternalProviderFactory(String modId, Supplier<ISpellBookProvider> factory) {
        if (modId == null || factory == null) {
            LOGGER.warn("Cannot register external provider factory: modId or factory is null");
            return false;
        }
        
        if (PROVIDER_FACTORIES.containsKey(modId)) {
            LOGGER.warn("Provider factory for mod {} already exists, overriding", modId);
        }
        
        PROVIDER_FACTORIES.put(modId, factory);
        LOGGER.info("Registered external spell book provider factory for mod {}", modId);
        return true;
    }
    
    /**
     * 获取所有注册的提供者模组ID列表
     */
    public static List<String> getRegisteredProviderMods() {
        return new ArrayList<>(PROVIDER_FACTORIES.keySet());
    }

    /**
     * 为特定女仆创建管理器实例（私有构造函数）
     */
    private SpellBookManager(EntityMaid maid) {
        this.maid = maid;
    }
    
    /**
     * 创建所有可用的提供者实例
     */
    private static void createProviderInstances() {
        for (Map.Entry<String, Supplier<ISpellBookProvider>> entry : PROVIDER_FACTORIES.entrySet()) {
            String modId = entry.getKey();
            Supplier<ISpellBookProvider> factory = entry.getValue();
            
            try {
                // 再次检查模组是否加载（防止运行时模组被卸载）
                if (!ModList.get().isLoaded(modId)) {
                    LOGGER.debug("Mod {} not loaded, skipping provider creation", modId);
                    continue;
                }
                
                // 创建提供者实例
                ISpellBookProvider provider = factory.get();
                if (provider != null) {
                    instanceProviders.add(provider);
                    LOGGER.debug("Created provider instance: {} for mod {}", 
                               provider.getClass().getSimpleName(), modId);
                } else {
                    LOGGER.warn("Provider factory for mod {} returned null", modId);
                }
                
            } catch (NoClassDefFoundError e) {
                LOGGER.warn("Failed to create provider instance for mod {} due to missing class dependencies: {}", 
                           modId, e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Failed to create provider instance for mod {}: {}", modId, e.getMessage(), e);
            }
        }
        
        LOGGER.info("Created {} provider instances", instanceProviders.size());
    }
    
    /**
     * 获取或创建女仆的管理器实例
     * 如果该女仆已有管理器实例，则返回现有的；否则创建新的
     * 
     * @param maid 女仆实体
     * @return 该女仆对应的SpellBookManager实例
     */
    public static SpellBookManager getOrCreateManager(EntityMaid maid) {
        if (maid == null) {
            LOGGER.warn("Attempted to get manager for null maid entity");
            return null;
        }
        
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
     * @return 如果存在并成功移除则返回true，否则返回false
     */
    public static boolean removeManager(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        
        UUID maidUUID = maid.getUUID();
        SpellBookManager removed = MAID_MANAGERS.remove(maidUUID);
        
        if (removed != null) {
            LOGGER.debug("Removed SpellBookManager for maid {}", maidUUID);
            return true;
        }
        
        return false;
    }
    
    /**
     * 移除女仆的管理器实例（通过UUID）
     * 
     * @param maidUUID 女仆UUID
     * @return 如果存在并成功移除则返回true，否则返回false
     */
    public static boolean removeManager(UUID maidUUID) {
        if (maidUUID == null) {
            return false;
        }
        
        SpellBookManager removed = MAID_MANAGERS.remove(maidUUID);
        
        if (removed != null) {
            LOGGER.debug("Removed SpellBookManager for maid {}", maidUUID);
            return true;
        }
        
        return false;
    }
    

    /**
     * 获取物品对应的法术书提供者
     */
    public ISpellBookProvider getProvider(ItemStack itemStack) {
        for (ISpellBookProvider provider : instanceProviders) {
            if (provider.isSpellBook(itemStack)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 执行法术
     */
    public boolean castSpell(EntityMaid maid) {
        boolean success = false;
        for (ISpellBookProvider provider : instanceProviders) {
            if (provider.castSpell(maid)) {
                success = true;
            }
        }
        return success;
    }
    
    /**
     * 获取当前实例的提供者列表
     */
    public List<ISpellBookProvider> getProviders() {
        return new ArrayList<>(instanceProviders);
    }


    public void stopAllCasting() {
        for (ISpellBookProvider provider : getProviders()) {
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
        for (ISpellBookProvider provider : instanceProviders) {
            provider.updateCooldown(maid);
        }
    }

    /**
     * 在女仆的背包中搜索各模组的法术书
     * 搜索顺序：副手 -> 主手 -> 背包
     * 对于IronsSpellbooksProvider，会收集所有类型的法术容器（法术书、魔剑、法杖）
     * @return 找到的法术书映射，每个提供者对应一本法术书
     */
    public Map<ISpellBookProvider, ItemStack> findSpellBooksInInventory() {
        Map<ISpellBookProvider, ItemStack> foundBooks = new HashMap<>();
        
        // 创建搜索队列：副手 -> 主手 -> 背包
        CombinedInvWrapper availableInv = maid.getAvailableInv(false);
        ItemStack[] searchOrder = new ItemStack[availableInv.getSlots()];
        
        for (int i = 0; i < availableInv.getSlots(); i++) {
            searchOrder[i] = availableInv.getStackInSlot(i);
        }
        
        // 遍历所有物品，为每个提供者找到对应的法术书
        for (ItemStack stack : searchOrder) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            for (ISpellBookProvider provider : instanceProviders) {
                if (provider.isSpellBook(stack)) {
                    // 对于IronsSpellbooksProvider，我们需要特殊处理以支持多种法术容器
                    if (provider.getClass().getSimpleName().equals("IronsSpellbooksProvider")) {
                        // 总是调用setSpellBook来让IronsSpellbooksProvider自己分类存储
                        provider.setSpellBook(maid, stack);
                    } else {
                        // 对于其他提供者，保持原有逻辑：每个提供者只找第一本对应的法术书
                        if (!foundBooks.containsKey(provider)) {
                            foundBooks.put(provider, stack);
                        }
                    }
                }
            }
        }
        
        return foundBooks;
    }

    public void tick(){
        // 处理持续性施法
        
        for (ISpellBookProvider provider : getProviders()) {
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

    public void updateSpellBooks(){
        // 先清理所有IronsSpellbooksProvider的法术容器
        for (ISpellBookProvider provider : getProviders()) {
            if (provider.getClass().getSimpleName().equals("IronsSpellbooksProvider")) {
                provider.setSpellBook(maid, null); // 清空所有法术容器
                break;
            }
        }
        
        var spellBooks = findSpellBooksInInventory();
        // 更新每个提供者的法术书
        for (ISpellBookProvider provider : getProviders()) {
            // 对于IronsSpellbooksProvider，findSpellBooksInInventory已经处理了所有法术容器
            if (provider.getClass().getSimpleName().equals("IronsSpellbooksProvider")) {
                // IronsSpellbooksProvider在findSpellBooksInInventory中已经被更新了
                continue;
            }
            
            // 对于其他提供者，使用原有逻辑
            ItemStack providerSpellBook = spellBooks.get(provider);
            if (providerSpellBook != null && !providerSpellBook.isEmpty()) {
                provider.setSpellBook(maid,providerSpellBook);
            } else {
                provider.setSpellBook(maid,null);
            }
        }
    }

}