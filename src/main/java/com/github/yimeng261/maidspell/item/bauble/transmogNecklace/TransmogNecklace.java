package com.github.yimeng261.maidspell.item.bauble.transmogNecklace;

import com.github.yimeng261.maidspell.client.gui.TransmogNecklaceScreen;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class TransmogNecklace extends Item {
    public TransmogNecklace() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int inventorySlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        if (level.isClientSide()) {
            openClientScreen(inventorySlot, getSelectedStyle(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClientScreen(int inventorySlot, TransmogHaloStyle currentStyle) {
        Minecraft.getInstance().setScreen(new TransmogNecklaceScreen(inventorySlot, currentStyle));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TransmogHaloStyle style = getSelectedStyle(stack);
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.transmog_necklace.current_style", style.getDisplayName())
                .withStyle(ChatFormatting.AQUA));
        TooltipHelper.addShiftTooltip(tooltip,
                List.of(Component.translatable("item.touhou_little_maid_spell.transmog_necklace.desc1").withStyle(ChatFormatting.LIGHT_PURPLE)),
                List.of(
                        Component.translatable("item.touhou_little_maid_spell.transmog_necklace.desc2").withStyle(ChatFormatting.GRAY),
                        Component.translatable("item.touhou_little_maid_spell.transmog_necklace.desc3").withStyle(ChatFormatting.BLUE),
                        Component.translatable("item.touhou_little_maid_spell.transmog_necklace.desc4").withStyle(ChatFormatting.YELLOW)
                ));
    }

    public static TransmogHaloStyle getSelectedStyle(ItemStack stack) {
        return TransmogHaloStyle.byId(stack.getOrDefault(MaidSpellDataComponents.TRANSMOG_HALO_STYLE_TAG, TransmogHaloStyle.DEFAULT.id()));
    }

    public static void setSelectedStyle(ItemStack stack, TransmogHaloStyle style) {
        stack.set(MaidSpellDataComponents.TRANSMOG_HALO_STYLE_TAG, style.id());
    }

    public static void setSelectedStyle(ItemStack stack, int styleIndex) {
        setSelectedStyle(stack, TransmogHaloStyle.byIndex(styleIndex));
    }
}
