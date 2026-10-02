package com.github.yimeng261.maidspell.winefox;

import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import java.util.List;

/** Server-authoritative menu shell for the Starglint Dagger settings screen. */
public class WinefoxChallengeConfigMenu extends AbstractContainerMenu {
    public static final MenuType<WinefoxChallengeConfigMenu> TYPE =
            IMenuTypeExtension.create(WinefoxChallengeConfigMenu::createClientSide);

    private final int itemSlot;
    private final WinefoxChallengeConfig config;
    private final WinefoxChallengeConfig defaults;
    private final List<WinefoxChallengeConfig> presets;
    private final Inventory inventory;

    public static WinefoxChallengeConfigMenu createClientSide(int id, Inventory inventory, FriendlyByteBuf data) {
        int itemSlot = data.readInt();
        return new WinefoxChallengeConfigMenu(id, inventory, itemSlot,
                WinefoxChallengeConfig.readRawFromBuffer(data),
                WinefoxChallengeConfig.readRawFromBuffer(data),
                WinefoxChallengePresets.readRawBuffer(data));
    }

    public WinefoxChallengeConfigMenu(int id, Inventory inventory, int itemSlot) {
        this(id, inventory, itemSlot, WinefoxChallengeConfig.fromItem(stackAt(inventory, itemSlot)),
                WinefoxChallengeConfig.defaults(), WinefoxChallengePresets.read(stackAt(inventory, itemSlot)));
    }

    private WinefoxChallengeConfigMenu(int id, Inventory inventory, int itemSlot,
                                       WinefoxChallengeConfig config, WinefoxChallengeConfig defaults,
                                       List<WinefoxChallengeConfig> presets) {
        super(TYPE, id);
        this.itemSlot = itemSlot;
        this.config = config;
        this.defaults = defaults;
        this.presets = presets;
        this.inventory = inventory;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 9 + column * 18, 132 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 9 + column * 18, 190));
        }
    }

    /** Client-side slot positions follow the available height and keep the inventory at the bottom. */
    public void positionInventory(int inventoryY) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = row * 9 + column;
                replaceSlot(index, column + row * 9 + 9,
                        9 + column * 18, inventoryY + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            replaceSlot(27 + column, column, 9 + column * 18, inventoryY + 58);
        }
    }

    /** 替换进来的 Slot 不经过 addSlot，需要补上菜单下标，点击包才会指向这一格。 */
    private void replaceSlot(int index, int inventorySlot, int x, int y) {
        Slot slot = new Slot(inventory, inventorySlot, x, y);
        slot.index = index;
        slots.set(index, slot);
    }

    public int getItemSlot() {
        return itemSlot;
    }

    public WinefoxChallengeConfig getConfig() {
        return config;
    }

    public WinefoxChallengeConfig getDefaults() {
        return defaults;
    }

    public List<WinefoxChallengeConfig> getPresets() {
        return presets;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = stackAt(player.getInventory(), itemSlot);
        return !stack.isEmpty() && stack.is(MaidSpellItems.STARGLINT_DAGGER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static ItemStack stackAt(Inventory inventory, int slot) {
        if (slot == 40) {
            return inventory.offhand.get(0);
        }
        return slot >= 0 && slot < inventory.items.size() ? inventory.getItem(slot) : ItemStack.EMPTY;
    }
}
