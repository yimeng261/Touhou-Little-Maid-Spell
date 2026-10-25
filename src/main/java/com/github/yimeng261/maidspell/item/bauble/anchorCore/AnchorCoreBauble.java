package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.AnchorCoreWhitelist;
import com.github.yimeng261.maidspell.utils.ChunkLoadingManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 锚定核心饰品逻辑
 * 为女仆提供全面保护，防止被其他模组影响
 */
public class AnchorCoreBauble implements IMaidBauble {

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        ChunkLoadingManager.enableChunkLoading(maid);
    }

    /** 判断类名是否为可信调用者（内置白名单 + {@link AnchorCoreWhitelist} 注册项）。 */
    public static boolean isCallerAllowed(String className) {
        return AnchorCoreWhitelist.isCallerAllowed(className);
    }

    public static void clearCompound(CompoundTag compound) {
        for (String key : new java.util.ArrayList<>(compound.getAllKeys())) {
            compound.remove(key);
        }
    }

    /**
     * 检查调用栈，返回第一个不在白名单中的类名，若全部合法则返回 null。
     */
    public static String findIllegalCaller() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (!isCallerAllowed(e.getClassName())) {
                return e.getClassName();
            }
        }
        return null;
    }
}
