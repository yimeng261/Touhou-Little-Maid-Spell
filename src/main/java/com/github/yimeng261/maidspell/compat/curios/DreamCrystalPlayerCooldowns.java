package com.github.yimeng261.maidspell.compat.curios;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import java.util.List;

/** Optional player cooldown stores that do not use vanilla item cooldowns. */
public final class DreamCrystalPlayerCooldowns {
    private DreamCrystalPlayerCooldowns() {}

    public static void clear(ServerPlayer player) {
        if (ModList.get().isLoaded("goety")) Goety.clear(player);
        if (ModList.get().isLoaded("ebwizardry")) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                Wizardry.clear(player.getInventory().getItem(slot));
            }
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var items = handler.getEquippedCurios();
                for (int slot = 0; slot < items.getSlots(); slot++) Wizardry.clear(items.getStackInSlot(slot));
            });
        }
    }

    private static final class Goety {
        private static void clear(ServerPlayer player) {
            var cooldowns = com.Polarice3.Goety.utils.SEHelper.getFocusCoolDown(player);
            for (var item : List.copyOf(cooldowns.getCooldowns().keySet())) {
                cooldowns.removeCooldown(player, player.level(), item);
            }
            for (String key : List.copyOf(cooldowns.getSpecificCooldowns().keySet())) {
                cooldowns.removeSpecificCooldown(player, player.level(), key);
            }
        }
    }

    private static final class Wizardry {
        private static void clear(ItemStack stack) {
            long[] times = com.binaris.wizardry.api.content.util.CastItemDataHelper.getCooldownEndTimes(stack);
            if (java.util.Arrays.stream(times).anyMatch(time -> time != 0)) {
                com.binaris.wizardry.api.content.util.CastItemDataHelper.setCooldownEndTimes(stack, new long[times.length]);
            }
        }
    }
}
