package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-to-client Ender Pocket list data. */
public final class EnderPocketDataMessage {
    public static final int MAX_MAID_INFOS = 64;
    public static final int MAX_MAID_NAME_LENGTH = 64;
    public static final int MAX_RESOURCE_ID_LENGTH = 256;

    public enum Type {
        RESPONSE_MAID_LIST,
        SERVER_PUSH_UPDATE,
        HUD_UPDATE
    }

    private final Type type;
    private final List<EnderPocketService.EnderPocketMaidInfo> maidInfos;
    private final boolean fromMaidBackpack;

    private EnderPocketDataMessage(Type type,
                                   List<EnderPocketService.EnderPocketMaidInfo> maidInfos,
                                   boolean fromMaidBackpack) {
        this.type = type;
        this.maidInfos = List.copyOf(maidInfos.subList(0, Math.min(maidInfos.size(), MAX_MAID_INFOS)));
        this.fromMaidBackpack = fromMaidBackpack;
    }

    public static EnderPocketDataMessage response(
            List<EnderPocketService.EnderPocketMaidInfo> maidInfos,
            boolean fromMaidBackpack) {
        return new EnderPocketDataMessage(Type.RESPONSE_MAID_LIST, maidInfos, fromMaidBackpack);
    }

    public static EnderPocketDataMessage serverPushUpdate(
            List<EnderPocketService.EnderPocketMaidInfo> maidInfos) {
        return new EnderPocketDataMessage(Type.SERVER_PUSH_UPDATE, maidInfos, true);
    }

    public static EnderPocketDataMessage hudUpdate(
            List<EnderPocketService.EnderPocketMaidInfo> maidInfos) {
        return new EnderPocketDataMessage(Type.HUD_UPDATE, maidInfos, false);
    }

    public static void encode(EnderPocketDataMessage message, FriendlyByteBuf buf) {
        buf.writeEnum(message.type);
        buf.writeBoolean(message.fromMaidBackpack);
        buf.writeVarInt(message.maidInfos.size());
        for (EnderPocketService.EnderPocketMaidInfo info : message.maidInfos) {
            buf.writeUUID(info.maidUUID());
            buf.writeUtf(truncate(info.maidName(), MAX_MAID_NAME_LENGTH), MAX_MAID_NAME_LENGTH);
            buf.writeVarInt(info.maidEntityId());
            buf.writeFloat(info.health());
            buf.writeFloat(info.maxHealth());
            buf.writeVarInt(info.armor());
            buf.writeDouble(info.x());
            buf.writeDouble(info.y());
            buf.writeDouble(info.z());
            buf.writeUtf(truncate(info.dimension(), MAX_RESOURCE_ID_LENGTH), MAX_RESOURCE_ID_LENGTH);
            buf.writeBoolean(info.hasAnchorCore());
            buf.writeUtf(truncate(info.modelId(), MAX_RESOURCE_ID_LENGTH), MAX_RESOURCE_ID_LENGTH);
        }
    }

    public static EnderPocketDataMessage decode(FriendlyByteBuf buf) {
        Type type = buf.readEnum(Type.class);
        boolean fromMaidBackpack = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_MAID_INFOS) {
            throw new DecoderException("Ender Pocket maid count exceeds limit: " + size);
        }

        List<EnderPocketService.EnderPocketMaidInfo> maidInfos = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID maidUuid = buf.readUUID();
            String maidName = buf.readUtf(MAX_MAID_NAME_LENGTH);
            int entityId = buf.readVarInt();
            float health = buf.readFloat();
            float maxHealth = buf.readFloat();
            int armor = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            String dimension = buf.readUtf(MAX_RESOURCE_ID_LENGTH);
            boolean hasAnchorCore = buf.readBoolean();
            String modelId = buf.readUtf(MAX_RESOURCE_ID_LENGTH);
            maidInfos.add(new EnderPocketService.EnderPocketMaidInfo(
                    maidUuid, maidName, entityId, health, maxHealth, armor,
                    x, y, z, dimension, hasAnchorCore, modelId));
        }
        return new EnderPocketDataMessage(type, maidInfos, fromMaidBackpack);
    }

    public static void handle(EnderPocketDataMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> EnderPocketClientHandler.handleClientMessage(
                        message.type,
                        message.maidInfos,
                        message.fromMaidBackpack
                )
        ));
        context.setPacketHandled(true);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
