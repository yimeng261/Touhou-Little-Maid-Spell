package com.github.yimeng261.maidspell.item.bauble.spellWhiteList;

import com.github.yimeng261.maidspell.utils.TooltipHelper;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.SpellWhiteListContainerProvider;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.SpellWhiteListSpellManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

public class SpellWhiteList extends Item {
    public SpellWhiteList() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 发光效果
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            openGUI(serverPlayer, stack, slot);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void openGUI(ServerPlayer player, ItemStack spellWhiteListStack, int slot) {
        ItemStackHandler scrollHandler = new ItemStackHandler(27);
        SpellWhiteListSpellManager.loadScrollsFromItem(spellWhiteListStack, scrollHandler, player.level().registryAccess());

        player.openMenu(new SpellWhiteListContainerProvider(scrollHandler, spellWhiteListStack, slot),
                buf -> {
                    buf.writeInt(slot);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, spellWhiteListStack);
                });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        TooltipHelper.addShiftTooltip(tooltip,
                List.of(),
                List.of(
                        Component.translatable("item.maidspell.blue_note.desc1")
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("item.maidspell.blue_note.desc2")
                                .withStyle(ChatFormatting.BLUE),
                        Component.translatable("item.maidspell.blue_note.desc3")
                                .withStyle(ChatFormatting.YELLOW)
                ));
    }
}
