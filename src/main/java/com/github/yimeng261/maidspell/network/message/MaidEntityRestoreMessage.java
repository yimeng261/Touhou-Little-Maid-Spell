package com.github.yimeng261.maidspell.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
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
    public static final int MAX_ENTITY_TYPE_ID_LENGTH = 256;
    public static final int MAX_ENTITY_DATA_VALUES = 255;
    public static final Type<MaidEntityRestoreMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid_entity_restore"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidEntityRestoreMessage> STREAM_CODEC =
            StreamCodec.of(MaidEntityRestoreMessage::encode, MaidEntityRestoreMessage::decode);

    /** 实体同步数据：先写条数（不超过 {@link #MAX_ENTITY_DATA_VALUES}），再逐条按原版格式读写 */
    public static final StreamCodec<RegistryFriendlyByteBuf, List<SynchedEntityData.DataValue<?>>> DATA_VALUES_CODEC =
            StreamCodec.of(MaidEntityRestoreMessage::encodeDataValues, MaidEntityRestoreMessage::decodeDataValues);

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
        return of(maid, entityTag);
    }

    /**
     * 用女仆当前的同步数据与位置，和已序列化的实体 NBT 组成恢复消息
     */
    public static MaidEntityRestoreMessage of(EntityMaid maid, CompoundTag entityTag) {
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

    @Override
    public Type<MaidEntityRestoreMessage> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, MaidEntityRestoreMessage message) {
        buf.writeVarInt(message.entityId);
        buf.writeUUID(message.uuid);
        buf.writeUtf(message.entityTypeId.toString(), MAX_ENTITY_TYPE_ID_LENGTH);
        buf.writeNbt(message.entityTag);
        DATA_VALUES_CODEC.encode(buf, message.entityData);
        buf.writeDouble(message.x);
        buf.writeDouble(message.y);
        buf.writeDouble(message.z);
        buf.writeFloat(message.yRot);
        buf.writeFloat(message.xRot);
    }

    public static MaidEntityRestoreMessage decode(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        UUID uuid = buf.readUUID();
        String entityTypeIdString = buf.readUtf(MAX_ENTITY_TYPE_ID_LENGTH);
        ResourceLocation entityTypeId = ResourceLocation.tryParse(entityTypeIdString);
        if (entityTypeId == null) {
            throw new DecoderException("Invalid entity type id: " + entityTypeIdString);
        }
        CompoundTag entityTag = buf.readNbt();
        List<SynchedEntityData.DataValue<?>> entityData = DATA_VALUES_CODEC.decode(buf);
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

    private static void encodeDataValues(RegistryFriendlyByteBuf buf, List<SynchedEntityData.DataValue<?>> values) {
        if (values.size() > MAX_ENTITY_DATA_VALUES) {
            throw new IllegalArgumentException("Entity data count exceeds limit: " + values.size());
        }
        buf.writeVarInt(values.size());
        for (SynchedEntityData.DataValue<?> dataValue : values) {
            dataValue.write(buf);
        }
    }

    private static List<SynchedEntityData.DataValue<?>> decodeDataValues(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTITY_DATA_VALUES) {
            throw new DecoderException("Entity data count exceeds limit: " + size);
        }
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int dataId = buf.readUnsignedByte();
            values.add(SynchedEntityData.DataValue.read(buf, dataId));
        }
        return values;
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
