package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
}
