package com.github.yimeng261.maidspell.debug;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public final class WinefoxLifecycleCommand {
    private WinefoxLifecycleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("maidspell_winefox")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("target", EntityArgument.entity())
                .then(Commands.literal("status").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntity(context, "target"), "status")))
                .then(Commands.literal("return").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntity(context, "target"), "return")))
                .then(Commands.literal("repair").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntity(context, "target"), "repair")))
                .then(Commands.literal("reward_confirm").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntity(context, "target"), "reward_confirm")))
                .then(Commands.literal("reward_reissue").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntity(context, "target"), "reward_reissue")))
                .then(Commands.literal("destroy").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntity(context, "target"), "destroy")))));
    }

    private static int execute(CommandSourceStack source, net.minecraft.world.entity.Entity entity, String action)
            throws CommandSyntaxException {
        if (!(entity instanceof MagicalWinefoxBossEntity boss)) {
            source.sendFailure(Component.literal("Target is not a stellar witch"));
            return 0;
        }
        switch (action) {
            case "return" -> boss.maidspell$returnAuthorized();
            case "repair" -> boss.maidspell$repairAuthorized();
            case "reward_confirm", "reward_reissue" -> {
                if (!boss.maidspell$resolvePendingReward(action.equals("reward_reissue"))) {
                    source.sendFailure(Component.literal("No pending reward or manual reissue failed; inspect logs"));
                    return 0;
                }
            }
            case "destroy" -> boss.maidspell$destroyAuthorized();
            default -> { }
        }
        source.sendSuccess(boss::maidspell$lifecycleStatus, true);
        return 1;
    }
}
