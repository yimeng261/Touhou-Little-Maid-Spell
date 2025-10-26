package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.utils.VersionUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.slf4j.Logger;

import java.util.*;
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
    private static final List<ISpellBookProvider> instanceProviders = new ArrayList<>();

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
        // registerProviderFactoryByClass("slashblade","SlashBladeProvider", com.github.yimeng261.maidspell.spell.providers.SlashBladeProvider.class);

        // 注册统一的Goety提供者（内部自动处理版本兼容性）
        // registerProviderFactoryByClass("goety", "GoetyProvider", com.github.yimeng261.maidspell.spell.providers.GoetyProvider.class);

        // 注册Youkai-Homecoming弹幕物品提供者
        // registerProviderFactoryByClass("youkaishomecoming", "YoukaiHomecomingProvider", com.github.yimeng261.maidspell.spell.providers.YoukaiHomecomingProvider.class);

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
                instanceProviders.add((ISpellBookProvider) providerClass.getConstructor().newInstance());
                loadedMods.add(modId);
                LOGGER.debug("Mod {} loaded, finished {} registration", modId, providerName);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to register provider factory for mod {}: {}", modId, e.getMessage());
        }
    }

    /**
     * 通用的版本检测Provider注册方法
     * 根据模组版本决定使用哪个Provider实现
     *
     * @param modId 模组ID
     * @param minVersionForNewProvider 使用新Provider的最低版本要求
     * @param defaultProviderClass 默认Provider类
     * @param newProviderClass 新Provider类的完整类名（可选）
     * @param providerName Provider名称（用于日志）
     */
    private static void registerProviderWithVersionCheck(String modId, String minVersionForNewProvider,
            Class<?> defaultProviderClass, Class<?> newProviderClass, String providerName) {
        try {
            // 检查模组是否加载
            if (!ModList.get().isLoaded(modId)) {
                LOGGER.debug("Mod {} not loaded, skipping provider registration", modId);
                return;
            }

            // 获取当前版本
            String currentVersion = VersionUtil.getModVersion(modId);
            if (currentVersion == null) {
                LOGGER.warn("Could not determine {} mod version, using default provider", modId);
                registerProviderFactoryByClass(modId, providerName, defaultProviderClass);
                return;
            }

            // 检查版本是否满足要求使用新Provider
            boolean useNewProvider = minVersionForNewProvider != null &&
                    VersionUtil.isVersionGreaterOrEqual(currentVersion, minVersionForNewProvider);

            if (useNewProvider && newProviderClass != null) {
                LOGGER.info("{} version {} >= {}, attempting to load enhanced provider",
                        modId, currentVersion, minVersionForNewProvider);

                // 尝试加载新版本的Provider（如果存在）
                instanceProviders.add((ISpellBookProvider) newProviderClass.getConstructor().newInstance());
                loadedMods.add(modId);
                LOGGER.info("Successfully loaded enhanced {} for {} version {}",
                        newProviderClass.getSimpleName(), modId, currentVersion);
            } else {
                if (minVersionForNewProvider != null) {
                    LOGGER.info("{} version {} < {}, using standard provider",
                            modId, currentVersion, minVersionForNewProvider);
                } else {
                    LOGGER.info("Using standard provider for {} version {}", modId, currentVersion);
                }
                registerProviderFactoryByClass(modId, providerName, defaultProviderClass);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to register {} provider with version check: {}", modId, e.getMessage());
            // 发生错误时回退到标准Provider
            registerProviderFactoryByClass(modId, providerName, defaultProviderClass);
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
        for (ISpellBookProvider provider : instanceProviders) {
            provider.castSpell(maid);
        }
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