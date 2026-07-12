package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 末影腰包服务类 - 统一管理所有enderPocket相关逻辑
 */
public class EnderPocketService {

    /**
     * 末影腰包女仆信息
     */
    public record EnderPocketMaidInfo(UUID maidUUID, String maidName, int maidEntityId) {}

    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(ServerPlayer player){
        return getPlayerEnderPocketMaids(player.getUUID());
    }
    
    /**
     * 获取玩家所有装备末影腰包的女仆信息
     */
    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(UUID playerUUID) {
        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(playerUUID);
        if (maids == null || maids.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnderPocketMaidInfo> enderPocketMaids = new ArrayList<>();
        
        for (EntityMaid maid : maids.values()) {
            if (maid != null && maid.isAlive() && !maid.isRemoved()
                    && playerUUID.equals(maid.getOwnerUUID())
                    && BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)) {
                enderPocketMaids.add(new EnderPocketMaidInfo(
                        maid.getUUID(),
                        maid.getName().getString(),
                        maid.getId()
                ));
            }
        }
        
        return enderPocketMaids;
    }
    
    /**
     * 打开女仆背包
     */
    public static boolean openMaidInventory(ServerPlayer player, UUID maidUuid) {
        if (maidUuid == null) {
            return false;
        }

        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(player.getUUID());
        EntityMaid maid = maids == null ? null : maids.get(maidUuid);
        if (maid == null
                || maid.isRemoved()
                || !maid.isAlive()
                || maid.isSleeping()
                || !player.getUUID().equals(maid.getOwnerUUID())
                || !maid.isOwnedBy(player)
                || !BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)) {
            return false;
        }
        
        // 使用车万女仆本体的GUI打开方法
        maid.openMaidGui(player, TabIndex.MAIN);
        return true;
    }
}
