package com.github.yimeng261.maidspell.utils;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Optional;

/**
 * 版本比较工具类
 * 用于检测模组版本并进行版本比较
 */
public class VersionUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 获取指定模组的版本号
     * @param modId 模组ID
     * @return 版本号字符串，如果模组未加载则返回null
     */
    public static String getModVersion(String modId) {
        Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById(modId);
        if (modContainer.isPresent()) {
            String version = modContainer.get().getModInfo().getVersion().toString();
            LOGGER.debug("Found mod {} with version: {}", modId, version);
            return version;
        }
        LOGGER.debug("Mod {} not found or not loaded", modId);
        return null;
    }
    
    /**
     * 比较两个版本号
     * 
     * @param version1 第一个版本号
     * @param version2 第二个版本号
     * @return 如果version1 >= version2返回true，否则返回false
     */
    public static boolean isVersionGreaterOrEqual(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return false;
        }
        
        try {
            int[] v1Parts = parseVersion(version1);
            int[] v2Parts = parseVersion(version2);
            
            // 比较版本号的每个部分
            for (int i = 0; i < Math.max(v1Parts.length, v2Parts.length); i++) {
                int v1Part = i < v1Parts.length ? v1Parts[i] : 0;
                int v2Part = i < v2Parts.length ? v2Parts[i] : 0;
                
                if (v1Part > v2Part) {
                    return true;
                } else if (v1Part < v2Part) {
                    return false;
                }
            }
            
            // 所有部分都相等，返回true（等于也满足大于等于的条件）
            return true;
            
        } catch (Exception e) {
            LOGGER.warn("Failed to compare versions {} and {}: {}", version1, version2, e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析版本号字符串为整数数组
     * 
     * @param version 版本号字符串
     * @return 版本号的各个部分组成的整数数组
     */
    private static int[] parseVersion(String version) {
        // 清理版本号字符串，移除可能的前缀和后缀
        String cleanVersion = cleanVersionString(version);
        
        // 按点分割版本号
        String[] parts = cleanVersion.split("\\.");
        int[] versionParts = new int[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            try {
                // 提取数字部分，忽略非数字字符
                String numericPart = parts[i].replaceAll("\\D", "");
                versionParts[i] = numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
            } catch (NumberFormatException e) {
                versionParts[i] = 0;
            }
        }
        
        return versionParts;
    }
    
    /**
     * 清理版本号字符串，移除常见的前缀和后缀
     * 
     * @param version 原始版本号字符串
     * @return 清理后的版本号字符串
     */
    private static String cleanVersionString(String version) {
        if (version == null) {
            return "0.0.0";
        }
        
        // 移除常见的前缀（如 "v", "version-", "mc1.20.1-"等）
        String cleaned = version.toLowerCase()
            .replaceAll("^(v|version-?)", "")
            .replaceAll("^mc\\d+\\.\\d+\\.\\d+-?", "")
            .replaceAll("^forge-?", "")
            .replaceAll("^fabric-?", "");
        
        // 移除常见的后缀（如 "-forge", "-fabric", "-SNAPSHOT"等）
        cleaned = cleaned.replaceAll("-(forge|fabric|snapshot|alpha|beta|rc\\d*).*$", "");
        
        // 如果清理后为空，返回默认版本
        if (cleaned.isEmpty()) {
            return "0.0.0";
        }
        
        return cleaned;
    }
    
    /**
     * 检查指定模组是否已加载且版本满足要求
     * 
     * @param modId 模组ID
     * @param minVersion 最低版本要求
     * @return 如果模组已加载且版本满足要求返回true，否则返回false
     */
    public static boolean isModVersionSatisfied(String modId, String minVersion) {
        String currentVersion = getModVersion(modId);
        if (currentVersion == null) {
            return false;
        }
        
        boolean satisfied = isVersionGreaterOrEqual(currentVersion, minVersion);
        LOGGER.debug("Mod {} version check: current={}, required={}, satisfied={}", 
            modId, currentVersion, minVersion, satisfied);
        
        return satisfied;
    }
    
    /**
     * 检查Goety模组的版本是否大于等于指定版本
     * 
     * @param minVersion 最低版本要求
     * @return 如果Goety模组版本满足要求返回true，否则返回false
     */
    public static boolean isGoetyVersionSatisfied(String minVersion) {
        return isModVersionSatisfied("goety", minVersion);
    }
}
