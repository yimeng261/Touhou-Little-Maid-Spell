package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class MaidEntityRestoreMessage implements CustomPacketPayload {
    public static final Type<MaidEntityRestoreMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid_entity_restore"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidEntityRestoreMessage> STREAM_CODEC =
            StreamCodec.of(MaidEntityRestoreMessage::encode, MaidEntityRestoreMessage::decode);

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

    @Override
    public Type<MaidEntityRestoreMessage> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, MaidEntityRestoreMessage message) {
        buf.writeVarInt(message.entityId);
        buf.writeUUID(message.uuid);
        buf.writeResourceLocation(message.entityTypeId);
        buf.writeNbt(message.entityTag);
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

    public static MaidEntityRestoreMessage decode(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        UUID uuid = buf.readUUID();
        ResourceLocation entityTypeId = buf.readResourceLocation();
        CompoundTag entityTag = buf.readNbt();
        int entityDataSize = buf.readVarInt();
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

    public int entityId() {
        return entityId;
    }

    public UUID uuid() {
        return uuid;
    }

    public ResourceLocation entityTypeId() {
        return entityTypeId;
    }

    public CompoundTag entityTag() {
        return entityTag;
    }

    public List<SynchedEntityData.DataValue<?>> entityData() {
        return entityData;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yRot() {
        return yRot;
    }

    public float xRot() {
        return xRot;
    }
}
