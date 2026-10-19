package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理每次施法的虚拟法杖上下文。上下文只在一次施法期间存活，
 * 虚拟法杖物品栈上只带它的编号。
 */
public final class VirtualWandManager {
    private static final String CONTEXT_TAG = "MaidSpellVirtualWand";
    private static final Map<UUID, VirtualWandContext> CONTEXTS = new ConcurrentHashMap<>();

    private VirtualWandManager() {
    }

    public record Handle(UUID id, ItemStack stack) {
    }

    @Nullable
    public static Handle create(EntityMaid maid, ItemStack bag, int focusSlot, ItemStack focus) {
        if (maid == null || bag == null || bag.isEmpty()
                || focus == null || focus.isEmpty() || focusSlot < 0) {
            return null;
        }
        FocusBagItemHandler handler;
        try {
            handler = FocusBagItemHandler.get(bag);
        } catch (RuntimeException exception) {
            return null;
        }
        if (focusSlot >= handler.getSlots()) {
            return null;
        }
        Item item = GoetyCompatItems.VIRTUAL_GOETY_WAND.get();
        UUID id = UUID.randomUUID();
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(CONTEXT_TAG, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        CONTEXTS.put(id, new VirtualWandContext(id, maid, handler, focusSlot, focus));
        return new Handle(id, stack);
    }

    /** 虚拟法杖物品栈上记着的上下文编号，不是虚拟法杖时为 null。 */
    @Nullable
    private static UUID contextId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        // 先按键名判断，别的物品栈不必为此深拷贝 NBT
        if (data == null || !data.contains(CONTEXT_TAG)) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(CONTEXT_TAG) ? tag.getUUID(CONTEXT_TAG) : null;
    }

    @Nullable
    static VirtualWandContext get(ItemStack stack) {
        UUID id = contextId(stack);
        if (id == null) {
            return null;
        }
        VirtualWandContext context = CONTEXTS.get(id);
        if (context == null) {
            return null;
        }
        if (!context.isValid()) {
            CONTEXTS.remove(id, context);
            context.release();
            return null;
        }
        return context;
    }

    public static void release(@Nullable UUID id) {
        if (id == null) {
            return;
        }
        VirtualWandContext context = CONTEXTS.remove(id);
        if (context != null) {
            context.release();
        }
    }

    public static void release(@Nullable ItemStack stack) {
        release(contextId(stack));
    }

    /** 把 Goety 法术回调里直接改动的聚晶数据写回聚晶包，聚晶包槽位始终是唯一的权威存储。 */
    public static void flush(@Nullable ItemStack stack) {
        VirtualWandContext context = get(stack);
        if (context != null) {
            context.flush();
        }
    }

    public static void releaseMaid(UUID maidId) {
        if (maidId == null) {
            return;
        }
        CONTEXTS.entrySet().removeIf(entry -> {
            VirtualWandContext context = entry.getValue();
            if (!maidId.equals(context.maidId())) {
                return false;
            }
            context.release();
            return true;
        });
    }

    public static void clearAll() {
        for (VirtualWandContext context : CONTEXTS.values()) {
            context.release();
        }
        CONTEXTS.clear();
    }

    /** 仅供测试读取当前存活的上下文数。 */
    static int activeContextCount() {
        return CONTEXTS.size();
    }
}
