package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.backpack.IBackpackContainerScreen;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.event.MaidBackpackEnderPocketIntegration;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen;
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
 * 响应女仆列表
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-10-25 20:30
 */
public record S2CEnderPocketMaidList(List<EnderPocketService.EnderPocketMaidInfo> maidInfos, boolean fromMaidBackpack) implements CustomPacketPayload {
    public static final Type<S2CEnderPocketMaidList> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_response_maid_list"));

    public static final StreamCodec<ByteBuf, S2CEnderPocketMaidList> STREAM_CODEC = StreamCodec.composite(
            EnderPocketService.EnderPocketMaidInfo.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)),
            S2CEnderPocketMaidList::maidInfos,
            ByteBufCodecs.BOOL,
            S2CEnderPocketMaidList::fromMaidBackpack,
            S2CEnderPocketMaidList::new
    );

    @Override
    public Type<S2CEnderPocketMaidList> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        Minecraft mc = Minecraft.getInstance();
        // 更新女仆背包集成的数据
        MaidBackpackEnderPocketIntegration.updateEnderPocketData(maidInfos());

        // 根据请求来源和当前界面决定显示方式
        if (fromMaidBackpack()) {
            // 来自女仆背包界面的请求
            if (mc.screen instanceof IBackpackContainerScreen) {
                // 重新初始化界面以更新按钮
                mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
        } else {
            mc.setScreen(new EnderPocketScreen(maidInfos()));
        }
    }
}
