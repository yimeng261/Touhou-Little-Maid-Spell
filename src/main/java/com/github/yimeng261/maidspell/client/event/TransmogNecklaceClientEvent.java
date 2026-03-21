package com.github.yimeng261.maidspell.client.event;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogHaloStyle;
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogNecklace;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.TransmogNecklaceMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 幻化项链客户端快捷切换
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TransmogNecklaceClientEvent {
    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Screen.hasShiftDown()) {
            return;
        }

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null || hoveredSlot.container != mc.player.getInventory()) {
            return;
        }

        ItemStack stack = hoveredSlot.getItem();
        if (!stack.is(MaidSpellItems.TRANSMOG_NECKLACE.get())) {
            return;
        }

        int inventorySlot = hoveredSlot.getContainerSlot();
        if (inventorySlot < 0) {
            return;
        }

        TransmogHaloStyle currentStyle = TransmogNecklace.getSelectedStyle(stack);
        TransmogHaloStyle nextStyle = event.getScrollDelta() > 0 ? currentStyle.previous() : currentStyle.next();

        TransmogNecklace.setSelectedStyle(stack, nextStyle);
        NetworkHandler.CHANNEL.sendToServer(new TransmogNecklaceMessage(inventorySlot, nextStyle.getSerializedIndex()));

        mc.player.displayClientMessage(
            Component.translatable("item.touhou_little_maid_spell.transmog_necklace.current_style", nextStyle.getDisplayName()),
            true
        );
        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.45F, 1.1F);
        event.setCanceled(true);
    }
}
