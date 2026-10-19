package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.common.items.handler.FocusBagItemHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the per-cast virtual wand contexts. A context is kept alive only for
 * the duration of a cast; the virtual item stack carries only its id.
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
        stack.getOrCreateTag().putUUID(CONTEXT_TAG, id);
        CONTEXTS.put(id, new VirtualWandContext(id, maid, handler, focusSlot, focus));
        return new Handle(id, stack);
    }

    @Nullable
    static VirtualWandContext get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(CONTEXT_TAG)) {
            return null;
        }
        UUID id = tag.getUUID(CONTEXT_TAG);
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
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(CONTEXT_TAG)) {
            release(tag.getUUID(CONTEXT_TAG));
        }
    }

    /**
     * Push in-place focus mutations made by a Goety spell callback back into
     * the FocusBag. The bag slot remains the only authoritative storage.
     */
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

    /** Visible to the Goety game test; production code has no use for it. */
    static int activeContextCount() {
        return CONTEXTS.size();
    }
}
