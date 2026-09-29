package com.github.yimeng261.maidspell.compat.curios;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** 梦云水晶的 curios 桥接，只在 curios 加载后使用。 */
public final class DreamCrystalCurios {
    private static final ResourceLocation SLOT_MODIFIER =
        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "dream_crystal_slot");

    private DreamCrystalCurios() {
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
}
