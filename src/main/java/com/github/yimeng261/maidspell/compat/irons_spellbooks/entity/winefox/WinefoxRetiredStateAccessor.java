package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/**
 * 「战败坐下」状态的同步位读写口，由 {@code EntityMaidWinefoxRetiredStateMixin} 实现在女仆身上。
 *
 * <p>服务端的权威标记是女仆 ForgeData 里的 {@code MaidSpellWinefoxRetired}，它只随存档走 ——
 * 实体同步给客户端走的 {@code ClientboundAddEntityPacket} 不带 NBT，所以客户端读不到它。
 * 这一位是给客户端看的那份镜像：酒狐劝退 / 维持 / 恢复女仆时跟着置位、清位，
 * 客户端才拦得住 TLM 魂符（{@code SlabClickEvent}）的本地预测。
 *
 * <p>拿不到实现（Mixin 没应用）时不要硬转，用 {@code instanceof} 判断，
 * 那种情况下退化成只认 ForgeData 标记，和以前一样。
 */
public interface WinefoxRetiredStateAccessor {
    boolean maidspell$isWinefoxRetired();

    void maidspell$setWinefoxRetired(boolean retired);
}
