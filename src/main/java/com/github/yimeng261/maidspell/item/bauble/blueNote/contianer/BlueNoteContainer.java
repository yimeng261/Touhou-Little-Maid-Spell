package com.github.yimeng261.maidspell.item.bauble.blueNote.contianer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;

public class BlueNoteContainer extends AbstractContainerMenu {
    public static final MenuType<BlueNoteContainer> TYPE = IForgeMenuType.create(BlueNoteContainer::createContainerClientSide);
    
    private final ItemStackHandler spellScrolls;
    private final ItemStack blueNoteStack;
    private final int blueNoteSlot;
    
    // 客户端构造函数
    public static BlueNoteContainer createContainerClientSide(int id, Inventory playerInventory, FriendlyByteBuf data) {
        int slot = data.readInt();
        ItemStack blueNoteStack = data.readItem();
        ItemStackHandler scrollHandler = new ItemStackHandler(27);
        // 客户端从NBT加载数据
        BlueNoteSpellManager.loadScrollsFromItem(blueNoteStack, scrollHandler);
        return new BlueNoteContainer(id, playerInventory, scrollHandler, blueNoteStack, slot);
    }
    
    // 通用构造函数
    public BlueNoteContainer(int id, Inventory playerInventory, ItemStackHandler spellScrolls, ItemStack blueNoteStack, int blueNoteSlot) {
        super(TYPE, id);
        this.spellScrolls = spellScrolls;
        this.blueNoteStack = blueNoteStack;
        this.blueNoteSlot = blueNoteSlot;
        
        // 添加法术卷轴槽位（3x9布局）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new ScrollSlot(spellScrolls, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        
        // 添加玩家背包槽位
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        
        // 添加玩家快捷栏槽位
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }
    
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // 处理法术卷轴槽位的特殊点击逻辑
        if (slotId >= 0 && slotId < 27) {
            Slot slot = this.slots.get(slotId);
            ItemStack carriedItem = this.getCarried();
            
            if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                if (button == 0) { // 左键点击
                    if (!carriedItem.isEmpty() && ISpellContainer.isSpellContainer(carriedItem)) {
                        // 放入法术卷轴（不消耗原物品）
                        if (!slot.hasItem()) {
                            ItemStack copy = carriedItem.copy();
                            copy.setCount(1);
                            slot.set(copy);
                            slot.setChanged();
                            // 不修改carriedItem，保持原物品不变
                            return;
                        }
                    } else if (carriedItem.isEmpty()) {
                        // 点击已放入的物品使其消失
                        if (slot.hasItem()) {
                            slot.set(ItemStack.EMPTY);
                            slot.setChanged();
                            return;
                        }
                    }
                }
            }
            
            // 禁止其他所有操作（如右键、拖拽等）
            return;
        }
        
        // 禁止与蓝色音符物品本身的交互，防止刷物品bug
        if (blueNoteSlot >= 0 && slotId == 27 + blueNoteSlot) {
            return;
        }
        
        // 禁止交换操作
        if (clickType == ClickType.SWAP) {
            return;
        }
        
        // 对于其他槽位，使用默认处理
        super.clicked(slotId, button, clickType, player);
    }
    
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack itemstack = slot.getItem();
            if (index < 27) {
                // 从法术卷轴槽位移动到玩家背包 - 禁止移出
                return ItemStack.EMPTY;
            } else {
                // 从玩家背包移动到法术卷轴槽位
                if (ISpellContainer.isSpellContainer(itemstack)) {
                    // 找到第一个空槽位
                    for (int i = 0; i < 27; i++) {
                        Slot targetSlot = this.slots.get(i);
                        if (!targetSlot.hasItem()) {
                            // 创建副本放入槽位，原物品不消耗
                            ItemStack copy = itemstack.copy();
                            copy.setCount(1);
                            targetSlot.set(copy);
                            targetSlot.setChanged();
                            return ItemStack.EMPTY; // 返回空表示原物品不消耗
                        }
                    }
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blueNoteSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(blueNoteSlot);
            return ItemStack.isSameItem(stack, blueNoteStack);
        }
        return true;
    }
    
    public ItemStackHandler getSpellScrolls() {
        return spellScrolls;
    }
    
    @Override
    public void removed(Player player) {
        super.removed(player);
        // 保存卷轴到BlueNote的NBT，包括法术列表
        if (!blueNoteStack.isEmpty()) {
            BlueNoteSpellManager.saveScrollsToItem(blueNoteStack, spellScrolls);
        }
    }
    
    // 自定义槽位类，只允许放入铁魔法卷轴
    private static class ScrollSlot extends SlotItemHandler {
        public ScrollSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }
        
        @Override
        public boolean mayPlace(ItemStack stack) {
            // 只允许放入铁魔法法术容器（卷轴）
            return ISpellContainer.isSpellContainer(stack);
        }
        
        @Override
        public int getMaxStackSize() {
            return 1; // 每个槽位只能放一个卷轴
        }
    }
} 