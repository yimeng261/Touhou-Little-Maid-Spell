package com.github.yimeng261.maidspell.api;

import com.github.yimeng261.maidspell.manager.SpellBookManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.function.Supplier;

/**
 * 法术书提供者注册表
 * 为外部模组提供注册自定义法术书提供者的API
 */
public class SpellBookProviderRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 注册法术书提供者工厂
     * 
     * @param modId 模组ID（必须与实际的模组ID匹配）
     * @param providerFactory 提供者工厂函数，接收EntityMaid参数并返回ISpellBookProvider实例
     * @return 如果注册成功返回true，否则返回false
     * 
     * @apiNote 建议在模组的FMLCommonSetupEvent或更早的阶段调用此方法
     * 
     * @example
     * <pre>{@code
     * // 在你的模组初始化代码中：
     * SpellBookProviderRegistry.registerProvider("your_mod_id", 
     *     maid -> new YourCustomSpellBookProvider(maid));
     * }</pre>
     */
    public static boolean registerProvider(String modId, Supplier<ISpellBookProvider> providerFactory) {
        if (modId == null || modId.trim().isEmpty()) {
            LOGGER.error("Cannot register provider: modId cannot be null or empty");
            return false;
        }
        
        if (providerFactory == null) {
            LOGGER.error("Cannot register provider for mod {}: factory cannot be null", modId);
            return false;
        }
        
        return SpellBookManager.registerExternalProviderFactory(modId, providerFactory);
    }
    
    /**
     * 注册法术书提供者（使用类构造函数）
     * 
     * @param modId 模组ID
     * @param providerClass 提供者类，必须有一个接收EntityMaid参数的构造函数
     * @return 如果注册成功返回true，否则返回false
     * 
     * @example
     * <pre>{@code
     * // 在你的模组初始化代码中：
     * SpellBookProviderRegistry.registerProvider("your_mod_id", YourCustomSpellBookProvider.class);
     * }</pre>
     */
    public static boolean registerProvider(String modId, Class<? extends ISpellBookProvider> providerClass) {
        if (modId == null || modId.trim().isEmpty()) {
            LOGGER.error("Cannot register provider: modId cannot be null or empty");
            return false;
        }
        
        if (providerClass == null) {
            LOGGER.error("Cannot register provider for mod {}: providerClass cannot be null", modId);
            return false;
        }
        
        // 创建工厂函数，使用反射调用构造函数
        Supplier<ISpellBookProvider> factory = () -> {
            try {
                var constructor = providerClass.getConstructor();
                return constructor.newInstance();
            } catch (Exception e) {
                LOGGER.error("Failed to create provider instance for class {}: {}", 
                           providerClass.getName(), e.getMessage());
                return null;
            }
        };
        
        return SpellBookManager.registerExternalProviderFactory(modId, factory);
    }
    
    /**
     * 获取所有已注册的提供者模组ID列表
     * 
     * @return 已注册的模组ID列表
     */
    public static List<String> getRegisteredMods() {
        return SpellBookManager.getRegisteredProviderMods();
    }
    
    /**
     * 检查指定模组是否已注册提供者
     * 
     * @param modId 模组ID
     * @return 如果已注册返回true，否则返回false
     */
    public static boolean isProviderRegistered(String modId) {
        return SpellBookManager.getRegisteredProviderMods().contains(modId);
    }
    
    /**
     * 获取当前已注册的提供者数量
     * 
     * @return 提供者数量
     */
    public static int getProviderCount() {
        return SpellBookManager.getRegisteredProviderMods().size();
    }
} 