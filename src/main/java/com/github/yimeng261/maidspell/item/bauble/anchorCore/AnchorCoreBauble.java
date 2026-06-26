package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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

    public static boolean isCallerAllowed(String className) {
        return className.startsWith("net.minecraft") ||
                className.startsWith("net.minecraftforge") ||
                className.startsWith("java") ||
                className.startsWith("jdk.") ||
                className.startsWith("sun.reflect") ||
                className.startsWith("it.unimi.dsi") ||
                className.startsWith("com.github.tartaricacid") ||
                className.startsWith("com.github.yimeng261") ||
                className.startsWith("com.google") ||
                className.startsWith("com.mojang") ||
                className.startsWith("io.redspace.ironsspellbooks") ||
                className.startsWith("whocraft.tardis_refined") ||
                className.startsWith("top.theillusivec4.curios") ||
                className.startsWith("tschipp.carryon") ||
                className.contains("backup") ||
                className.contains("maid") ||
                className.contains("c2me");
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
