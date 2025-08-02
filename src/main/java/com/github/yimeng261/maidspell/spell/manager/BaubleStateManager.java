package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager.getBauble;

/**
 * 饰品状态管理器
 * 定期检测女仆饰品的变化，特别是法术增强饰品的卸下
 */
public class BaubleStateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 存储每个女仆的饰品状态快照 UUID -> List<ItemStack>
    private static final Map<UUID, List<ItemStack>> maidBaublePrevious = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ItemStack>> maidBaubleCurrent = new ConcurrentHashMap<>();

    /**
     * 更新指定女仆的饰品状态并检测变化
     * @param maid 女仆实体
     */
    public static void updateAndCheckBaubleState(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        
        UUID maidUUID = maid.getUUID();
        List<ItemStack> currentBaubles = getCurrentBaubles(maid);
        List<ItemStack> previousBaubles = getPreviousBaubles(maidUUID);

        maidBaubleCurrent.put(maid.getUUID(), currentBaubles);
        
        if (previousBaubles != null) {
            List<ItemStack> removedBaubles = previousBaubles.stream().filter(item->!currentBaubles.contains(item)).toList();;
            for (ItemStack removedBauble : removedBaubles) {
                IMaidBauble bauble = BaubleManager.getBauble(removedBauble);
                if(bauble instanceof IExtendBauble) {
                    ((IExtendBauble) bauble).onRemove(maid);
                }
            }
        }

        maidBaublePrevious.put(maidUUID, new ArrayList<>(currentBaubles));
    }

    private static List<ItemStack> getPreviousBaubles(UUID maidUUID) {
        return maidBaublePrevious.computeIfAbsent(maidUUID, k -> new ArrayList<>());
    }
    
    /**
     * 获取女仆当前的饰品列表
     */
    private static List<ItemStack> getCurrentBaubles(EntityMaid maid) {

        List<ItemStack> baubles = new ArrayList<>();
        BaubleItemHandler handler = maid.getMaidBauble();

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                baubles.add(stack.copy()); // 创建副本避免引用问题
            }
        }

        return baubles;
    }

    public static List<ItemStack> getBaubles(EntityMaid maid) {
        return maidBaubleCurrent.computeIfAbsent(maid.getUUID(), k -> new ArrayList<>());
    }


} 