package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Holds the detached client maid used while a remote Ender Pocket menu is open. */
public final class EnderPocketMaidProxyCache {
    private static Level level;
    private static EntityMaid maid;

    private EnderPocketMaidProxyCache() {
    }

    public static synchronized void store(Level currentLevel, EntityMaid proxy) {
        level = currentLevel;
        maid = proxy;
    }

    @Nullable
    public static synchronized EntityMaid find(Level currentLevel, int entityId) {
        if (level != currentLevel) {
            clear();
            return null;
        }
        if (maid == null || maid.getId() != entityId || maid.isRemoved()) {
            return null;
        }
        return maid;
    }

    public static synchronized void clear() {
        level = null;
        maid = null;
    }
}
