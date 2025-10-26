package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.backpack.IBackpackContainerScreen;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.event.MaidBackpackEnderPocketIntegration;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务器主动推送数据更新
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-10-25 21:03
 */
public record S2CEnderPocketPushUpdate(List<EnderPocketService.EnderPocketMaidInfo> maidInfos, boolean fromMaidBackpack) implements CustomPacketPayload {
    public static final Type<S2CEnderPocketPushUpdate> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_server_push_update"));

    public static final StreamCodec<ByteBuf, S2CEnderPocketPushUpdate> STREAM_CODEC = StreamCodec.composite(
            EnderPocketService.EnderPocketMaidInfo.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)),
            S2CEnderPocketPushUpdate::maidInfos,
            ByteBufCodecs.BOOL,
            S2CEnderPocketPushUpdate::fromMaidBackpack,
            S2CEnderPocketPushUpdate::new
    );

    @Override
    public Type<S2CEnderPocketPushUpdate> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        Minecraft mc = Minecraft.getInstance();
        // 服务器主动推送的数据更新
        MaidBackpackEnderPocketIntegration.updateEnderPocketData(maidInfos());

        // 如果当前在女仆背包界面，刷新界面
        if (mc.screen instanceof IBackpackContainerScreen) {
            mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }
}
