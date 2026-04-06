package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.SpellSyncMessage;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 法术同步API
 * 功能：将单个女仆的法术信息广播到所有客户端进行全局记录
 * 数据格式：UUID + Map<模组ID, 法术名称>
 * 
 * 使用方式：在女仆施法逻辑中调用 syncMaidSpellsToAllClients(maid)
 * 数据读取：客户端通过 SpellSyncClientHandler 访问全局记录
 */
public class SpellSyncAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 同步女仆当前使用的法术信息到所有客户端
     * 在女仆施法时调用（ISpellBookProvider.castSpell中）
     * @param maid 女仆实体
     */
    public static void syncMaidSpellsToAllClients(EntityMaid maid) {
        if (maid == null) {
            LOGGER.warn("尝试同步空女仆的法术");
            return;
        }
        
        Map<String, String> spellMap = collectSpellsFromMaid(maid);
        
        SpellSyncMessage message = new SpellSyncMessage(maid.getUUID(), spellMap);
        
        //LOGGER.debug("广播女仆 {} (UUID: {}) 的 {} 个法术到所有客户端", maid.getName().getString(), maid.getUUID(), spellMap.size());
        
        // 广播到所有客户端
        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                message
        );
    }
    
    /**
     * 从女仆收集当前使用的法术信息
     * 只收集当前正在施放的法术（通过currentSpellId字段）
     * @param maid 女仆实体
     * @return 法术映射 <模组ID, 法术名称>
     */
    public static Map<String, String> collectSpellsFromMaid(EntityMaid maid) {
        Map<String, String> spellMap = new HashMap<>();
        
        if (maid == null) {
            return spellMap;
        }

        for(String modId : SpellBookManager.getLoadedMods()){
            ISpellBookProvider<?, ?> provider = SpellBookManager.getProvider(modId);
            String spellName = collectSpellFromProvider(provider, maid, modId);
            if (spellName != null && !spellName.isEmpty()) {
                spellMap.put(modId, spellName);
            }
        }

        //LOGGER.debug("女仆 {} 当前使用 {} 个模组的法术", maid.getName().getString(), spellMap.size());
        
        return spellMap;
    }
    
    /**
     * 从提供者收集当前使用的法术
     * @param provider 法术提供者
     * @param maid 女仆实体
     * @param modId 模组ID
     * @return 法术名称，如果没有返回null
     */
    private static String collectSpellFromProvider(
            ISpellBookProvider<?, ?> provider, 
            EntityMaid maid,
            String modId) {
        
        try {
            // 获取法术数据
            Object spellData = provider.getData(maid);
            if (spellData == null) {
                return null;
            }
            
            // 只收集当前正在使用的法术
            if (spellData instanceof IMaidSpellData maidSpellData) {
                String currentSpellId = maidSpellData.getCurrentSpellId();
                
                if (currentSpellId != null && !currentSpellId.isEmpty()) {
                    //LOGGER.debug("收集到当前法术: [{}] {}", modId, currentSpellId);
                    return currentSpellId;
                }
            }
        } catch (Exception e) {
            LOGGER.error("从提供者收集法术时出错 (模组: {}): {}", modId, e.getMessage());
        }
        
        return null;
    }
}
