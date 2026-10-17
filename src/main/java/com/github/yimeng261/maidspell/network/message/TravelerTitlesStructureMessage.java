package com.github.yimeng261.maidspell.network.message;

import com.github.yimeng261.maidspell.MaidSpellMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

/** 同步本地玩家当前所在的 Traveler's Titles 结构，不在结构内时为 null。 */
public record TravelerTitlesStructureMessage(@Nullable ResourceLocation structureId) implements CustomPacketPayload {
    public static final Type<TravelerTitlesStructureMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "traveler_titles_structure"));

    public static final StreamCodec<ByteBuf, TravelerTitlesStructureMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            message -> Optional.ofNullable(message.structureId()),
            structureId -> new TravelerTitlesStructureMessage(structureId.orElse(null))
    );

    @Override
    public Type<TravelerTitlesStructureMessage> type() {
        return TYPE;
    }
}
