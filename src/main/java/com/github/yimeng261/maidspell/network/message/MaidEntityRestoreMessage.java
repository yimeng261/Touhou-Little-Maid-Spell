package com.github.yimeng261.maidspell.network.message;

import io.netty.handler.codec.DecoderException;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class MaidEntityRestoreMessage {
    public static final int MAX_ENTITY_TYPE_ID_LENGTH = 256;
    public static final int MAX_ENTITY_DATA_VALUES = 255;
    private final int entityId;
    private final UUID uuid;
    private final ResourceLocation entityTypeId;
    private final CompoundTag entityTag;
    private final List<SynchedEntityData.DataValue<?>> entityData;
    private final double x;
    private final double y;
    private final double z;
    private final float yRot;
    private final float xRot;

    public MaidEntityRestoreMessage(int entityId, UUID uuid, ResourceLocation entityTypeId, CompoundTag entityTag,
                                    List<SynchedEntityData.DataValue<?>> entityData,
                                    double x, double y, double z, float yRot, float xRot) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.entityTypeId = entityTypeId;
        this.entityTag = entityTag == null ? new CompoundTag() : entityTag;
        this.entityData = entityData == null ? Collections.emptyList() : List.copyOf(entityData);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public static MaidEntityRestoreMessage remoteSnapshot(EntityMaid maid) {
        CompoundTag entityTag = new CompoundTag();
        maid.saveWithoutId(entityTag);
        entityTag.remove("MaidHistoryChat");
        entityTag.remove("MaidHistorySummary");

        List<SynchedEntityData.DataValue<?>> entityData = maid.getEntityData().getNonDefaultValues();
        if (entityData == null) {
            entityData = Collections.emptyList();
        }
        return new MaidEntityRestoreMessage(
                maid.getId(),
                maid.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(maid.getType()),
                entityTag,
                entityData,
                maid.getX(),
                maid.getY(),
                maid.getZ(),
                maid.getYRot(),
                maid.getXRot()
        );
    }

    public static void encode(MaidEntityRestoreMessage message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
        buf.writeUUID(message.uuid);
        buf.writeUtf(message.entityTypeId.toString(), MAX_ENTITY_TYPE_ID_LENGTH);
        buf.writeNbt(message.entityTag);
        if (message.entityData.size() > MAX_ENTITY_DATA_VALUES) {
            throw new IllegalArgumentException("Entity data count exceeds limit: " + message.entityData.size());
        }
        buf.writeVarInt(message.entityData.size());
        for (SynchedEntityData.DataValue<?> dataValue : message.entityData) {
            dataValue.write(buf);
        }
        buf.writeDouble(message.x);
        buf.writeDouble(message.y);
        buf.writeDouble(message.z);
        buf.writeFloat(message.yRot);
        buf.writeFloat(message.xRot);
    }

    public static MaidEntityRestoreMessage decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        UUID uuid = buf.readUUID();
        String entityTypeIdString = buf.readUtf(MAX_ENTITY_TYPE_ID_LENGTH);
        ResourceLocation entityTypeId = ResourceLocation.tryParse(entityTypeIdString);
        if (entityTypeId == null) {
            throw new DecoderException("Invalid entity type id: " + entityTypeIdString);
        }
        CompoundTag entityTag = buf.readNbt();
        int entityDataSize = buf.readVarInt();
        if (entityDataSize < 0 || entityDataSize > MAX_ENTITY_DATA_VALUES) {
            throw new DecoderException("Entity data count exceeds limit: " + entityDataSize);
        }
        List<SynchedEntityData.DataValue<?>> entityData = new ArrayList<>(entityDataSize);
        for (int i = 0; i < entityDataSize; i++) {
            int dataId = buf.readUnsignedByte();
            entityData.add(SynchedEntityData.DataValue.read(buf, dataId));
        }
        return new MaidEntityRestoreMessage(
                entityId,
                uuid,
                entityTypeId,
                entityTag,
                entityData,
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public static void handle(MaidEntityRestoreMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MaidEntityRestoreClientHandler.handle(message));
            }
        });
        context.setPacketHandled(true);
    }

    int entityId() {
        return entityId;
    }

    UUID uuid() {
        return uuid;
    }

    ResourceLocation entityTypeId() {
        return entityTypeId;
    }

    CompoundTag entityTag() {
        return entityTag;
    }

    List<SynchedEntityData.DataValue<?>> entityData() {
        return entityData;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yRot() {
        return yRot;
    }

    float xRot() {
        return xRot;
    }
}
