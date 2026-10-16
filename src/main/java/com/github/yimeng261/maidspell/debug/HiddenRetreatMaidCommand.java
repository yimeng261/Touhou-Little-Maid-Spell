package com.github.yimeng261.maidspell.debug;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.HiddenRetreatMaidRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class HiddenRetreatMaidCommand {
    private HiddenRetreatMaidCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var command = Commands.literal("maidspell_retreat_maid").requires(source -> source.hasPermission(2));
        for (String action : new String[]{"status", "reset"}) {
            command.then(Commands.literal(action).then(Commands.argument("player_uuid", UuidArgument.uuid())
                    .executes(context -> {
                        var player = UuidArgument.getUuid(context, "player_uuid");
                        var data = HiddenRetreatMaidRegistry.get(context.getSource().getServer());
                        boolean value = "reset".equals(action) ? data.reset(player) : data.hasClaim(player);
                        context.getSource().sendSuccess(() -> Component.translatable(
                                "commands.touhou_little_maid_spell.retreat_maid." + action, player.toString(), value),
                                "reset".equals(action));
                        return value ? 1 : 0;
                    })));
        }
        event.getDispatcher().register(command);
    }
}
