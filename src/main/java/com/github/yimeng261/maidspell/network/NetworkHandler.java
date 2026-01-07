package com.github.yimeng261.maidspell.network;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.message.EnderPocketMessage;
import com.github.yimeng261.maidspell.network.message.SpellSyncMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络消息处理器
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    
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
                EnderPocketMessage.class,
                EnderPocketMessage::encode,
                EnderPocketMessage::decode,
                EnderPocketMessage::handle
        );
        
        CHANNEL.registerMessage(
                id++,
                SpellSyncMessage.class,
                SpellSyncMessage::encode,
                SpellSyncMessage::decode,
                SpellSyncMessage::handle
        );
    }
}
