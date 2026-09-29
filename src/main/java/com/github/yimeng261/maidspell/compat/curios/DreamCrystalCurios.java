package com.github.yimeng261.maidspell.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.UUID;

/** Loaded only after Curios has been detected. */
public final class DreamCrystalCurios {
    private static final UUID SLOT_MODIFIER = UUID.fromString("dc000001-0000-0000-0000-000000000004");

    private DreamCrystalCurios() {}

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
}
