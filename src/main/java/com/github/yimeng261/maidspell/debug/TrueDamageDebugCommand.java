package com.github.yimeng261.maidspell.debug;

import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * 真实伤害调试命令
 * 用于测试和调试真实伤害功能
 */
@EventBusSubscriber
public class TrueDamageDebugCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("truedamage")
            .requires(source -> source.hasPermission(2)) // 需要OP权限
            .then(Commands.literal("deal")
                .then(Commands.argument("target", EntityArgument.entity())
                    .then(Commands.argument("damage", FloatArgumentType.floatArg(0.0f))
                        .executes(context -> {
                            try {
                                var entity = EntityArgument.getEntity(context, "target");
                                float damage = FloatArgumentType.getFloat(context, "damage");

                                if (!(entity instanceof LivingEntity target)) {
                                    context.getSource().sendFailure(Component.literal("目标必须是生物实体"));
                                    return 0;
                                }

                                float oldHealth = target.getHealth();
                                TrueDamageUtil.dealTrueDamage(target, damage);
                                float newHealth = target.getHealth();

                                context.getSource().sendSuccess(() -> Component.literal(
                                    String.format("对 %s 造成 %.1f 真实伤害，健康值从 %.1f 变为 %.1f",
                                        target.getName().getString(), damage, oldHealth, newHealth)), true);

                                return 1;
                            } catch (Exception e) {
                                LOGGER.error("执行真实伤害命令时出错", e);
                                context.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
                                return 0;
                            }
                        })
                    )
                )
            )
            .then(Commands.literal("debug")
                .then(Commands.literal("entitydata")
                    .then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> {
                            try {
                                var entity = EntityArgument.getEntity(context, "target");

                                if (!(entity instanceof LivingEntity target)) {
                                    context.getSource().sendFailure(Component.literal("目标必须是生物实体"));
                                    return 0;
                                }

                                String info = TrueDamageUtil.getEntityDataInfo(target);
                                context.getSource().sendSuccess(() -> Component.literal(info), false);
                                return 1;
                            } catch (Exception e) {
                                LOGGER.error("获取实体数据信息时出错", e);
                                context.getSource().sendFailure(Component.literal("获取信息失败: " + e.getMessage()));
                                return 0;
                            }
                        })
                    )
                )
            )
        );
    }
}