package com.github.yimeng261.maidspell.item.common;

import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfigProvider;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeProgress;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 星芒短剑：递给星之魔女后开启切磋。
 */
public class StarglintDaggerItem extends Item {
    public StarglintDaggerItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        openConfig(level, player, hand, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return super.useOn(context);
        }
        openConfig(context.getLevel(), player, context.getHand(), context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private static void openConfig(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openChallengeConfig(serverPlayer, hand, stack);
        }
    }

    private static boolean canOpenConfig(ServerPlayer player) {
        if (WinefoxChallengeProgress.hasActiveChallenge(player)) {
            player.displayClientMessage(Component.translatable(
                    "item.touhou_little_maid_spell.starglint_dagger.config_in_battle"), true);
            return false;
        }
        if (!Config.winefoxChallengeConfigEnabled) {
            player.displayClientMessage(Component.translatable(
                    "item.touhou_little_maid_spell.starglint_dagger.config_disabled"), true);
            return false;
        }
        if (!WinefoxChallengeProgress.hasDefeated(player)) {
            player.displayClientMessage(Component.translatable(
                    "item.touhou_little_maid_spell.starglint_dagger.config_locked"), true);
            return false;
        }
        return true;
    }

    public static void openChallengeConfig(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!canOpenConfig(player)) {
            return;
        }
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        NetworkHooks.openScreen(player, new WinefoxChallengeConfigProvider(slot), buffer -> {
            buffer.writeInt(slot);
            WinefoxChallengeConfig.fromItem(stack).writeToBuffer(buffer);
            WinefoxChallengeConfig.defaults().writeToBuffer(buffer);
            WinefoxChallengePresets.writeBuffer(buffer, WinefoxChallengePresets.read(stack));
        });
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.starglint_dagger.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.starglint_dagger.desc2")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.starglint_dagger.desc3")
                .withStyle(ChatFormatting.GRAY));
    }
}
