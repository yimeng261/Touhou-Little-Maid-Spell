package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen;
import com.github.yimeng261.maidspell.client.overlay.EnderPocketHudOverlay;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 末影腰包女仆状态栏数据
 */
public record S2CEnderPocketHudUpdate(List<EnderPocketService.EnderPocketMaidInfo> maidInfos) implements CustomPacketPayload {
    public S2CEnderPocketHudUpdate {
        maidInfos = EnderPocketService.EnderPocketMaidInfo.copyLimited(maidInfos);
    }

    public static final Type<S2CEnderPocketHudUpdate> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_hud_update"));

    public static final StreamCodec<ByteBuf, S2CEnderPocketHudUpdate> STREAM_CODEC = StreamCodec.composite(
            EnderPocketService.EnderPocketMaidInfo.LIST_STREAM_CODEC,
            S2CEnderPocketHudUpdate::maidInfos,
            S2CEnderPocketHudUpdate::new
    );

    @Override
    public Type<S2CEnderPocketHudUpdate> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        EnderPocketHudOverlay.update(maidInfos());
        if (Minecraft.getInstance().screen instanceof EnderPocketScreen screen) {
            screen.updateMaidInfos(maidInfos());
        }
    }
}
