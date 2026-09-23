package com.github.yimeng261.maidspell.compat.curios;

import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.List;

/** 清除不走原版物品冷却的第三方玩家冷却。 */
public final class DreamCrystalPlayerCooldowns {
    private DreamCrystalPlayerCooldowns() {
    }

    public static void clear(ServerPlayer player) {
        if (ModList.get().isLoaded("goety")) {
            Goety.clear(player);
        }
    }

    private static final class Goety {
        private static void clear(ServerPlayer player) {
            var cooldowns = SEHelper.getFocusCoolDown(player);
            for (var item : List.copyOf(cooldowns.getCooldowns().keySet())) {
                cooldowns.removeCooldown(player, player.level(), item);
            }
            for (String key : List.copyOf(cooldowns.getSpecificCooldowns().keySet())) {
                cooldowns.removeSpecificCooldown(player, player.level(), key);
            }
        }
    }
}
