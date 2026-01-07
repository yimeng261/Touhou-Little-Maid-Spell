package com.github.yimeng261.maidspell.network.message;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法术同步客户端处理器
 * 处理从服务器接收的单个女仆法术信息
 * 数据格式：UUID + Map<模组ID, 法术名称>
 * 
 * 这是一个客户端全局记录系统，记录所有女仆的法术使用情况
 */
@OnlyIn(Dist.CLIENT)
public class SpellSyncClientHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 客户端全局女仆法术记录 <女仆UUID, <模组ID, 法术名称>>
    private static final Map<UUID, Map<String, String>> GLOBAL_MAID_SPELL_RECORD = new ConcurrentHashMap<>();
    
    /**
     * 处理法术同步消息（客户端全局记录）
     * @param maidUUID 女仆UUID
     * @param spellMap 法术映射 <模组ID, 法术名称>
     */
    public static void handleSpellSync(UUID maidUUID, Map<String, String> spellMap) {
        //LOGGER.debug("[客户端全局] 接收到服务器法术信息同步，女仆 UUID: {}, 法术数量: {}", maidUUID, spellMap.size());
        
        // 更新该女仆的法术记录
        if (spellMap.isEmpty()) {
            // 如果法术为空，移除该女仆的记录
            GLOBAL_MAID_SPELL_RECORD.remove(maidUUID);
            //LOGGER.debug("[客户端全局] 已移除女仆 {} 的法术记录", maidUUID);
        } else {
            // 更新该女仆的法术
            GLOBAL_MAID_SPELL_RECORD.put(maidUUID, new HashMap<>(spellMap));
        }

//        GLOBAL_MAID_SPELL_RECORD.forEach((uuid, map) -> {
//            LOGGER.debug("[客户端全局调试] 女仆 {} 的法术: {}", uuid, map);
//        });
    }
    
    /**
     * 获取客户端全局女仆法术记录
     * @return 女仆法术映射 <女仆UUID, <模组ID, 法术名称>>
     */
    public static Map<UUID, Map<String, String>> getGlobalMaidSpellRecord() {
        return GLOBAL_MAID_SPELL_RECORD;
    }
    
    /**
     * 获取指定女仆的法术映射
     * @param maidUUID 女仆UUID
     * @return 该女仆的法术映射，如果不存在返回空Map
     */
    public static Map<String, String> getMaidSpells(UUID maidUUID) {
        Map<String, String> spells = GLOBAL_MAID_SPELL_RECORD.get(maidUUID);
        return spells != null ? Map.copyOf(spells) : new HashMap<>();
    }
    
    /**
     * 获取指定女仆指定模组的法术
     * @param maidUUID 女仆UUID
     * @param modId 模组ID
     * @return 法术名称，如果不存在返回null
     */
    public static String getMaidSpellForMod(UUID maidUUID, String modId) {
        Map<String, String> spells = GLOBAL_MAID_SPELL_RECORD.get(maidUUID);
        return spells != null ? spells.get(modId) : null;
    }
    
    /**
     * 检查指定女仆是否正在使用法术
     * @param maidUUID 女仆UUID
     * @return 如果该女仆有法术记录返回true
     */
    public static boolean isMaidCasting(UUID maidUUID) {
        Map<String, String> spells = GLOBAL_MAID_SPELL_RECORD.get(maidUUID);
        return spells != null && !spells.isEmpty();
    }
    
    /**
     * 获取所有正在使用法术的女仆UUID列表
     * @return 女仆UUID集合
     */
    public static java.util.Set<UUID> getActiveMaidUUIDs() {
        return GLOBAL_MAID_SPELL_RECORD.keySet();
    }
    
    /**
     * 清空客户端全局法术记录
     */
    public static void clearGlobalSpellRecord() {
        GLOBAL_MAID_SPELL_RECORD.clear();
        LOGGER.info("[客户端全局] 已清空女仆法术记录");
    }
    
    /**
     * 检查是否有法术记录
     */
    public static boolean hasSpellData() {
        return !GLOBAL_MAID_SPELL_RECORD.isEmpty();
    }
    
    /**
     * 获取当前记录的女仆数量
     * @return 女仆数量
     */
    public static int getMaidCount() {
        return GLOBAL_MAID_SPELL_RECORD.size();
    }
    
    /**
     * 获取总法术记录数量（所有女仆的所有法术）
     * @return 总法术数量
     */
    public static int getTotalSpellCount() {
        return GLOBAL_MAID_SPELL_RECORD.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
}
