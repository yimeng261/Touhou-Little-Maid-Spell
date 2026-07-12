package com.github.yimeng261.maidspell.network;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.message.EnderPocketDataMessage;
import com.github.yimeng261.maidspell.network.message.EnderPocketRequestMessage;
import com.github.yimeng261.maidspell.network.message.MaidClientRemovalGuardMessage;
import com.github.yimeng261.maidspell.network.message.MaidEntityRestoreMessage;
import com.github.yimeng261.maidspell.network.message.TransmogNecklaceMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 网络消息处理器
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2";
    
    @SuppressWarnings("removal")
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MaidSpellMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    
    private static int id = 0;
    
    public static void registerMessages() {
        CHANNEL.registerMessage(
                id++,
                EnderPocketRequestMessage.class,
                EnderPocketRequestMessage::encode,
                EnderPocketRequestMessage::decode,
                EnderPocketRequestMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                id++,
                EnderPocketDataMessage.class,
                EnderPocketDataMessage::encode,
                EnderPocketDataMessage::decode,
                EnderPocketDataMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                id++,
                TransmogNecklaceMessage.class,
                TransmogNecklaceMessage::encode,
                TransmogNecklaceMessage::decode,
                TransmogNecklaceMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                id++,
                MaidEntityRestoreMessage.class,
                MaidEntityRestoreMessage::encode,
                MaidEntityRestoreMessage::decode,
                MaidEntityRestoreMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                id++,
                MaidClientRemovalGuardMessage.class,
                MaidClientRemovalGuardMessage::encode,
                MaidClientRemovalGuardMessage::decode,
                MaidClientRemovalGuardMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

    }
}
