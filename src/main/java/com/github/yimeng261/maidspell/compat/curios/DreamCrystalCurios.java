package com.github.yimeng261.maidspell.compat.curios;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;

/** 梦云水晶的 curios 桥接，只在 curios 加载后使用。 */
public final class DreamCrystalCurios {
    private static final ResourceLocation SLOT_MODIFIER =
        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_slot");

    private DreamCrystalCurios() {
    }

    public static Item createItem() {
        return new DreamCrystalCurioItem();
    }

    public static ItemStack findCrystal(LivingEntity wearer) {
        return CuriosApi.getCuriosInventory(wearer)
            .flatMap(handler -> handler.findFirstCurio(MaidSpellItems.DREAM_CAT_CRYSTAL.get()))
            .map(SlotResult::stack).orElse(ItemStack.EMPTY);
    }

    public static boolean hasItem(LivingEntity wearer, Item item) {
        return CuriosApi.getCuriosInventory(wearer)
            .map(handler -> handler.isEquipped(item)).orElse(false);
    }

    /** 为每种 curios 槽位各加（或移除）1 格；已存在时不重复添加，避免每秒重建槽位。 */
    public static void setExtraSlots(LivingEntity wearer, boolean enabled) {
        CuriosApi.getCuriosInventory(wearer).ifPresent(handler -> {
            for (var entry : List.copyOf(handler.getCurios().entrySet())) {
                boolean present = entry.getValue().getModifiers().containsKey(SLOT_MODIFIER);
                if (enabled && !present) {
                    handler.addTransientSlotModifier(entry.getKey(), SLOT_MODIFIER,
                        1.0D, AttributeModifier.Operation.ADD_VALUE);
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
                if (stack.isDamaged()) {
                    stack.setDamageValue(stack.getDamageValue() - 1);
                }
            }
        });
    }
}
