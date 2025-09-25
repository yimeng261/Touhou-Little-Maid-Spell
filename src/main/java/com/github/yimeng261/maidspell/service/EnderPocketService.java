package com.github.yimeng261.maidspell.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 末影腰包服务类 - 统一管理所有enderPocket相关逻辑
 */
public class EnderPocketService {
    
    /**
     * 末影腰包女仆信息
     */
    public static class EnderPocketMaidInfo {
        public final UUID maidUUID;
        public final String maidName;
        public final int maidEntityId;

        public EnderPocketMaidInfo(UUID maidUUID, String maidName, int maidEntityId) {
            this.maidUUID = maidUUID;
            this.maidName = maidName;
            this.maidEntityId = maidEntityId;
        }
    }
    
    /**
     * 检查女仆是否装备末影腰包
     */
    public static boolean hasMaidEnderPocket(EntityMaid maid) {
        if (maid == null) return false;
        
        List<ItemStack> baubles = BaubleStateManager.getBaubles(maid);
        String enderPocketId = MaidSpellItems.itemDesc(MaidSpellItems.ENDER_POCKET);
        
        return baubles.stream()
                .anyMatch(stack -> stack.getDescriptionId().equals(enderPocketId));
    }
    
    /**
     * 获取玩家所有装备末影腰包的女仆信息
     */
    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(ServerPlayer player) {
        HashMap<UUID, EntityMaid> maids = Global.maidInfos.get(player.getUUID());
        if (maids == null || maids.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnderPocketMaidInfo> enderPocketMaids = new ArrayList<>();
        
        for (EntityMaid maid : maids.values()) {
            if (hasMaidEnderPocket(maid)) {
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
     * 检查特定女仆是否装备末影腰包
     */
    public static boolean checkSpecificMaidEnderPocket(ServerPlayer player, int maidEntityId) {
        Entity entity = player.level().getEntity(maidEntityId);
        if (!(entity instanceof EntityMaid maid)) {
            return false;
        }
        
        // 检查权限
        if (!maid.isOwnedBy(player) || maid.isSleeping() || !maid.isAlive()) {
            return false;
        }
        
        return hasMaidEnderPocket(maid);
    }
    
    /**
     * 打开女仆背包
     */
    public static boolean openMaidInventory(ServerPlayer player, int maidEntityId) {
        Entity entity = player.level().getEntity(maidEntityId);
        if (!(entity instanceof EntityMaid maid)) {
            return false;
        }
        
        // 检查权限
        if (!maid.isOwnedBy(player) || maid.isSleeping() || !maid.isAlive()) {
            return false;
        }
        
        // 使用车万女仆本体的GUI打开方法
        maid.openMaidGui(player, com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex.MAIN);
        return true;
    }
}
