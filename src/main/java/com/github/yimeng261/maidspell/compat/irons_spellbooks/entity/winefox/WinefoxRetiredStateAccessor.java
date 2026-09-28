package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/** 战败坐姿的客户端同步位接口；服务端 ForgeData 标记仍是权威状态。 */
public interface WinefoxRetiredStateAccessor {
    boolean maidspell$isWinefoxRetired();

    void maidspell$setWinefoxRetired(boolean retired);
}
