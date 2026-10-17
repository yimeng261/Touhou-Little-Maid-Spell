package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 远程会话期间把女仆的同步数据推给客户端代理。
 * <p>
 * 主人不在女仆附近时收不到原版的实体数据同步，代理上的开关（拾物、骑乘等）会停在打开界面时的状态，
 * TLM 界面再按代理上的旧值整包发回，就会把刚改过的设置改回去。
 */
public record S2CEnderPocketMaidData(int entityId, List<SynchedEntityData.DataValue<?>> values)
        implements CustomPacketPayload {
    public static final Type<S2CEnderPocketMaidData> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "ender_pocket_maid_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CEnderPocketMaidData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, S2CEnderPocketMaidData::entityId,
            MaidEntityRestoreMessage.DATA_VALUES_CODEC, S2CEnderPocketMaidData::values,
            S2CEnderPocketMaidData::new);

    @Override
    public Type<S2CEnderPocketMaidData> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        EnderPocketMaidSnapshotClientHandler.handleData(this);
    }
}
