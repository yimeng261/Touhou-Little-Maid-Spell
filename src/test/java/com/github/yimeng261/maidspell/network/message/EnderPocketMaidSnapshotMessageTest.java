package com.github.yimeng261.maidspell.network.message;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderPocketMaidSnapshotMessageTest {
    @Test
    void snapshotRoundTripsWithHandshakeMetadata() {
        UUID sessionId = UUID.randomUUID();
        UUID maidId = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("ModelId", "touhou_little_maid:test");
        MaidEntityRestoreMessage snapshot = new MaidEntityRestoreMessage(
                42,
                maidId,
                new ResourceLocation("touhou_little_maid", "maid"),
                tag,
                List.of(),
                1.25D,
                64.0D,
                -2.5D,
                90.0F,
                15.0F
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EnderPocketMaidSnapshotMessage.encode(
                    new EnderPocketMaidSnapshotMessage(sessionId, true, snapshot), buffer);
            EnderPocketMaidSnapshotMessage decoded = EnderPocketMaidSnapshotMessage.decode(buffer);

            assertEquals(sessionId, decoded.sessionId());
            assertTrue(decoded.acknowledge());
            assertEquals(42, decoded.snapshot().entityId());
            assertEquals(maidId, decoded.snapshot().uuid());
            assertEquals(snapshot.entityTypeId(), decoded.snapshot().entityTypeId());
            assertEquals("touhou_little_maid:test",
                    decoded.snapshot().entityTag().getString("ModelId"));
            assertEquals(1.25D, decoded.snapshot().x());
            assertEquals(64.0D, decoded.snapshot().y());
            assertEquals(-2.5D, decoded.snapshot().z());
        } finally {
            buffer.release();
        }
    }
}
