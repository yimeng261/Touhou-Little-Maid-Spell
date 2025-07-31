package com.github.yimeng261.maidspell.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

// SlashBlade相关导入

import java.util.List;

public class MaidCommand {
    private static final String ROOT_NAME = "maid";
    private static final double DEFAULT_SEARCH_RADIUS = 10.0;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT_NAME)
                .requires(source -> source.hasPermission(2) || source.isPlayer());

        // /maid use [radius] - 让附近女仆使用主手物品
        root.then(Commands.literal("use")
                .executes(context -> executeUse(context, DEFAULT_SEARCH_RADIUS))
                .then(Commands.argument("radius", FloatArgumentType.floatArg(1.0f, 50.0f))
                        .executes(context -> executeUse(context, FloatArgumentType.getFloat(context, "radius")))));

        // /maid release [radius] - 让附近女仆释放主手物品
        root.then(Commands.literal("release")
                .executes(context -> executeRelease(context, DEFAULT_SEARCH_RADIUS))
                .then(Commands.argument("radius", FloatArgumentType.floatArg(1.0f, 50.0f))
                        .executes(context -> executeRelease(context, FloatArgumentType.getFloat(context, "radius")))));

        // /maid testblade [radius] - 测试拔刀剑功能
        root.then(Commands.literal("testblade")
                .executes(context -> executeTestBlade(context, DEFAULT_SEARCH_RADIUS))
                .then(Commands.argument("radius", FloatArgumentType.floatArg(1.0f, 50.0f))
                        .executes(context -> executeTestBlade(context, FloatArgumentType.getFloat(context, "radius")))));

        dispatcher.register(root);
    }

    private static int executeUse(CommandContext<CommandSourceStack> context, double radius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable("commands.maid.player_only"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        
        List<EntityMaid> nearbyMaids = findNearbyMaids(player, level, radius);
        
        if (nearbyMaids.isEmpty()) {
            source.sendFailure(Component.translatable("commands.maid.no_maids_found", radius));
            return 0;
        }

        int successCount = 0;
        for (EntityMaid maid : nearbyMaids) {
            if (maid.isOwnedBy(player) && maid.isAlive() && !maid.isSleeping()) {
                ItemStack mainHandItem = maid.getMainHandItem();
                if (!mainHandItem.isEmpty()) {
                    // 特别检查拔刀剑
                    if (mainHandItem.getItem().getClass().getName().contains("ItemSlashBlade")) {
                        source.sendSuccess(() -> Component.literal("§6[DEBUG] 女仆 " + maid.getName().getString() + 
                            " 正在尝试使用拔刀剑: " + mainHandItem.getDisplayName().getString()), false);
                        
                        // 检查INPUT_STATE能力
                        boolean hasInputState = mainHandItem.getCapability(
                            mods.flammpfeil.slashblade.item.ItemSlashBlade.INPUT_STATE).isPresent();
                        
                        // 如果没有，检查我们自己添加的能力
                        if (!hasInputState) {
                            hasInputState = maid.getCapability(
                                mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).isPresent();
                        }

                        boolean finalHasInputState = hasInputState;
                        source.sendSuccess(() -> Component.literal("§6[DEBUG] 女仆INPUT_STATE能力: " +
                            (finalHasInputState ? "§a存在" : "§c缺失")), false);
                            
                        // 检查拔刀剑状态
                        mainHandItem.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                            .ifPresent(state -> {
                                source.sendSuccess(() -> Component.literal("§6[DEBUG] 拔刀剑状态 - 损坏: " + 
                                    state.isBroken() + ", 封印: " + state.isSealed() + 
                                    ", 类型: " + mods.flammpfeil.slashblade.item.SwordType.from(mainHandItem)), false);
                                
                                if (state.getSlashArts() != null) {
                                    source.sendSuccess(() -> Component.literal("§6[DEBUG] 技能: " + 
                                        state.getSlashArts().getDescription().getString() + 
                                        ", 消耗: " + state.getSlashArts().getProudSoulCost() + 
                                        ", 当前魂: " + state.getProudSoulCount()), false);
                                }
                            });
                    }
                    
                    // 模拟女仆使用物品
                    maid.startUsingItem(InteractionHand.MAIN_HAND);
                    successCount++;
                    
                    source.sendSuccess(() -> Component.literal("§a女仆 " + maid.getName().getString() + 
                        " 开始使用物品: " + mainHandItem.getDisplayName().getString()), false);
                }
            }
        }

        if (successCount > 0) {
            final int finalSuccessCount = successCount;
            source.sendSuccess(() -> Component.translatable("commands.maid.use_success", finalSuccessCount), true);
            return successCount;
        } else {
            source.sendFailure(Component.translatable("commands.maid.use_no_items"));
            return 0;
        }
    }

    private static int executeRelease(CommandContext<CommandSourceStack> context, double radius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable("commands.maid.player_only"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        
        List<EntityMaid> nearbyMaids = findNearbyMaids(player, level, radius);
        
        if (nearbyMaids.isEmpty()) {
            source.sendFailure(Component.translatable("commands.maid.no_maids_found", radius));
            return 0;
        }

        int successCount = 0;
        for (EntityMaid maid : nearbyMaids) {
            if (maid.isOwnedBy(player) && maid.isAlive() && !maid.isSleeping()) {
                ItemStack mainHandItem = maid.getMainHandItem();
                if (!mainHandItem.isEmpty() && maid.isUsingItem()) {
                    // 调用物品的 releaseUsing 方法
                    int timeLeft = maid.getUseItemRemainingTicks();
                    mainHandItem.releaseUsing(level, maid, timeLeft);
                    maid.stopUsingItem();
                    successCount++;
                }
            }
        }

        if (successCount > 0) {
            final int finalSuccessCount = successCount;
            source.sendSuccess(() -> Component.translatable("commands.maid.release_success", finalSuccessCount), true);
            return successCount;
        } else {
            source.sendFailure(Component.translatable("commands.maid.release_no_items"));
            return 0;
        }
    }

    private static int executeTestBlade(CommandContext<CommandSourceStack> context, double radius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable("commands.maid.player_only"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        
        List<EntityMaid> nearbyMaids = findNearbyMaids(player, level, radius);
        
        if (nearbyMaids.isEmpty()) {
            source.sendFailure(Component.translatable("commands.maid.no_maids_found", radius));
            return 0;
        }

        int successCount = 0;
        for (EntityMaid maid : nearbyMaids) {
            if (maid.isOwnedBy(player) && maid.isAlive() && !maid.isSleeping()) {
                ItemStack mainHandItem = maid.getMainHandItem();
                if (!mainHandItem.isEmpty()) {
                    // 特别检查拔刀剑
                    if (mainHandItem.getItem().getClass().getName().contains("ItemSlashBlade")) {
                        source.sendSuccess(() -> Component.literal("§6[DEBUG] 女仆 " + maid.getName().getString() + 
                            " 正在尝试使用拔刀剑: " + mainHandItem.getDisplayName().getString()), false);
                        
                        // 检查INPUT_STATE能力
                        boolean hasInputState = mainHandItem.getCapability(
                            mods.flammpfeil.slashblade.item.ItemSlashBlade.INPUT_STATE).isPresent();
                        
                        // 如果没有，检查我们自己添加的能力
                        if (!hasInputState) {
                            hasInputState = maid.getCapability(
                                mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).isPresent();
                        }

                        boolean finalHasInputState = hasInputState;
                        source.sendSuccess(() -> Component.literal("§6[DEBUG] 女仆INPUT_STATE能力: " +
                            (finalHasInputState ? "§a存在" : "§c缺失")), false);
                            
                        // 检查拔刀剑状态
                        mainHandItem.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                            .ifPresent(state -> {
                                source.sendSuccess(() -> Component.literal("§6[DEBUG] 拔刀剑状态 - 损坏: " + 
                                    state.isBroken() + ", 封印: " + state.isSealed() + 
                                    ", 类型: " + mods.flammpfeil.slashblade.item.SwordType.from(mainHandItem)), false);
                                
                                if (state.getSlashArts() != null) {
                                    source.sendSuccess(() -> Component.literal("§6[DEBUG] 技能: " + 
                                        state.getSlashArts().getDescription().getString() + 
                                        ", 消耗: " + state.getSlashArts().getProudSoulCost() + 
                                        ", 当前魂: " + state.getProudSoulCount()), false);
                                }
                            });
                        
                        // === 使用SlashBladeProvider进行完整测试 ===
                        try {
                            // 获取SpellBookManager
                            com.github.yimeng261.maidspell.spell.manager.SpellBookManager manager =
                                com.github.yimeng261.maidspell.spell.manager.SpellBookManager.getOrCreateManager(maid);
                            
                            if (manager != null) {
                                // 找到SlashBladeProvider
                                com.github.yimeng261.maidspell.spell.providers.SlashBladeProvider slashBladeProvider = null;
                                for (com.github.yimeng261.maidspell.api.ISpellBookProvider provider : manager.getProviders()) {
                                    if (provider instanceof com.github.yimeng261.maidspell.spell.providers.SlashBladeProvider) {
                                        slashBladeProvider = (com.github.yimeng261.maidspell.spell.providers.SlashBladeProvider) provider;
                                        break;
                                    }
                                }
                                
                                if (slashBladeProvider != null) {
                                    // 设置拔刀剑
                                    slashBladeProvider.setSpellBook(maid, mainHandItem);
                                    
                                    // 设置一个虚拟目标（玩家自己作为目标进行测试）
                                    slashBladeProvider.setTarget(maid, player);
                                    
                                    // 开始施法
                                    boolean castResult = slashBladeProvider.initiateCasting(maid);
                                    
                                    if (castResult) {
                                        source.sendSuccess(() -> Component.literal("§a[SUCCESS] 女仆 " + maid.getName().getString() + 
                                            " 成功开始拔刀剑施法流程！"), false);
                                        successCount++;
                                    } else {
                                        source.sendSuccess(() -> Component.literal("§c[FAILED] 女仆 " + maid.getName().getString() + 
                                            " 无法开始拔刀剑施法流程"), false);
                                    }
                                } else {
                                    source.sendSuccess(() -> Component.literal("§c[ERROR] 找不到SlashBladeProvider"), false);
                                }
                            } else {
                                source.sendSuccess(() -> Component.literal("§c[ERROR] 找不到SpellBookManager"), false);
                            }
                        } catch (Exception e) {
                            source.sendSuccess(() -> Component.literal("§c[ERROR] 测试过程中出现异常: " + e.getMessage()), false);
                        }
                    } else {
                        source.sendSuccess(() -> Component.literal("§c女仆 " + maid.getName().getString() + 
                            " 主手没有拔刀剑"), false);
                    }
                }
            }
        }

        if (successCount > 0) {
            final int finalSuccessCount = successCount;
            source.sendSuccess(() -> Component.translatable("commands.maid.testblade_success", finalSuccessCount), true);
            return successCount;
        } else {
            source.sendFailure(Component.translatable("commands.maid.testblade_no_items"));
            return 0;
        }
    }

    private static List<EntityMaid> findNearbyMaids(Player player, ServerLevel level, double radius) {
        AABB searchArea = new AABB(
                player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                player.getX() + radius, player.getY() + radius, player.getZ() + radius
        );
        
        return level.getEntitiesOfClass(EntityMaid.class, searchArea, 
                maid -> maid.isAlive() && !maid.isSleeping() && maid.distanceTo(player) <= radius);
    }
} 