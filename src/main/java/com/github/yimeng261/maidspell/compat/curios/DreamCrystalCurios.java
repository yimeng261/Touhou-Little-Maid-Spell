package com.github.yimeng261.maidspell.compat.curios;

import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.UUID;

/** Loaded only after Curios has been detected. */
public final class DreamCrystalCurios {
    private static final UUID SLOT_MODIFIER = UUID.fromString("dc000001-0000-0000-0000-000000000004");

    private DreamCrystalCurios() {}

    public static Item createItem() {
        return new DreamCrystalCurioItem();
    }

    public static ItemStack findCrystal(LivingEntity wearer) {
        return CuriosApi.getCuriosInventory(wearer).resolve()
            .flatMap(handler -> handler.findFirstCurio(MaidSpellItems.DREAM_CAT_CRYSTAL.get()))
            .map(result -> result.stack()).orElse(ItemStack.EMPTY);
    }

    public static boolean hasItem(LivingEntity wearer, Item item) {
        return CuriosApi.getCuriosInventory(wearer).resolve()
            .map(handler -> handler.isEquipped(item)).orElse(false);
    }

    public static void setExtraSlots(LivingEntity wearer, boolean enabled) {
        CuriosApi.getCuriosInventory(wearer).ifPresent(handler -> {
            for (var entry : java.util.List.copyOf(handler.getCurios().entrySet())) {
                boolean present = entry.getValue().getModifiers().containsKey(SLOT_MODIFIER);
                if (enabled && !present) {
                    handler.addTransientSlotModifier(entry.getKey(), SLOT_MODIFIER,
                        "dream_crystal_slot", 1.0D, AttributeModifier.Operation.ADDITION);
                } else if (!enabled && present) {
                    handler.removeSlotModifier(entry.getKey(), SLOT_MODIFIER);
                }
            }
        });
    }

    public static void repairEquipment(LivingEntity wearer) {
        CuriosApi.getCuriosInventory(wearer).ifPresent(handler -> {
            var inventory = handler.getEquippedCurios();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isDamaged()) stack.setDamageValue(stack.getDamageValue() - 1);
            }
        });
    }
}
