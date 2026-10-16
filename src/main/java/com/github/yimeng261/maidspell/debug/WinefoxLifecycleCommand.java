package com.github.yimeng261.maidspell.debug;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public final class WinefoxLifecycleCommand {
    private WinefoxLifecycleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("maidspell_winefox")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.literal("status").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntities(context, "targets"), "status")))
                .then(Commands.literal("return").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntities(context, "targets"), "return")))
                .then(Commands.literal("repair").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntities(context, "targets"), "repair")))
                .then(Commands.literal("reward_confirm").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntities(context, "targets"), "reward_confirm")))
                .then(Commands.literal("reward_reissue").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntities(context, "targets"), "reward_reissue")))
                .then(Commands.literal("palm_black_hole").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntities(context, "targets"), "palm_black_hole")))
                .then(Commands.literal("destroy").executes(context -> execute(context.getSource(),
                    EntityArgument.getEntities(context, "targets"), "destroy")))));
    }

    private static int execute(CommandSourceStack source, Collection<? extends Entity> entities, String action) {
        int selected = 0;
        int succeeded = 0;
        for (Entity entity : entities) {
            if (entity instanceof MagicalWinefoxBossEntity boss) {
                selected++;
                succeeded += executeBoss(source, boss, action);
            }
        }
        if (selected == 0) {
            source.sendFailure(Component.literal("No stellar witches selected"));
        } else if (entities.size() > 1) {
            int completed = succeeded;
            int bosses = selected;
            source.sendSuccess(() -> Component.literal("Winefox " + action + ": " + completed + "/" + bosses
                + " succeeded; skipped " + (entities.size() - bosses) + " non-stellar-witch entities"), true);
        }
        return succeeded;
    }

    private static int executeBoss(CommandSourceStack source, MagicalWinefoxBossEntity boss, String action) {
        switch (action) {
            case "return" -> boss.maidspell$returnAuthorized();
            case "repair" -> boss.maidspell$repairAuthorized();
            case "reward_confirm", "reward_reissue" -> {
                if (!boss.maidspell$resolvePendingReward(action.equals("reward_reissue"))) {
                    source.sendFailure(Component.literal("Winefox " + boss.getUUID()
                        + ": no pending reward or manual reissue failed; inspect logs"));
                    return 0;
                }
            }
            case "palm_black_hole" -> {
                if (!boss.maidspell$testPalmBlackHoleAuthorized()) {
                    source.sendFailure(Component.literal("Winefox " + boss.getUUID()
                        + ": could not start Palm Black Hole; check pending rewards and home position"));
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
