package com.github.yimeng261.maidspell.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber
public class HurtDebugCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SuggestionProvider<CommandSourceStack> MAID_UUID_SUGGESTIONS = (context, builder) ->
        SharedSuggestionProvider.suggest(
            Global.activeMaids.stream()
                .filter(maid -> maid != null && maid.isAlive())
                .map(maid -> maid.getUUID().toString())
                .sorted(Comparator.naturalOrder())
                .toList(),
            builder
        );

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("hurt")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("maid_uuid", StringArgumentType.word())
                .suggests(MAID_UUID_SUGGESTIONS)
                .then(Commands.argument("damage", FloatArgumentType.floatArg(0.0f))
                    .executes(HurtDebugCommand::execute)
                )
            )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        String maidUuidText = StringArgumentType.getString(context, "maid_uuid");
        float damage = FloatArgumentType.getFloat(context, "damage");

        final UUID maidUuid;
        try {
            maidUuid = UUID.fromString(maidUuidText);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("无效的女仆 UUID: " + maidUuidText));
            return 0;
        }

        Optional<EntityMaid> maidOptional = Global.activeMaids.stream()
            .filter(maid -> maid != null && maid.isAlive() && maid.getUUID().equals(maidUuid))
            .findFirst();

        if (maidOptional.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未找到该 UUID 对应的活跃女仆: " + maidUuid));
            return 0;
        }

        EntityMaid maid = maidOptional.get();
        float oldHealth = maid.getHealth();

        DamageSource damageSource = createDamageSource(maid);
        boolean success = maid.hurt(damageSource, damage);
        float newHealth = maid.getHealth();

        context.getSource().sendSuccess(() -> Component.literal(
            String.format(
                "已对女仆 %s 造成 %.1f 伤害，结果=%s，生命值 %.1f -> %.1f",
                maid.getUUID(),
                damage,
                success,
                oldHealth,
                newHealth
            )
        ), true);

        return success ? 1 : 0;
    }

    private static DamageSource createDamageSource(EntityMaid maid) {
        return maid.damageSources().generic();
    }
}
