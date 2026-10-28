package com.github.yimeng261.maidspell.compat.usefulmagic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** 实用魔法落地时按施法者及其主人链判断女仆的地形保护。 */
public final class UsefulMagicTerrainProtection {
    private UsefulMagicTerrainProtection() {
    }

    public static boolean blocksTerrainDamage(@Nullable Entity source) {
        return Config.usefulMagicMaidTerrainProtection
                && MaidSpellAllyResolver.isOwnedBy(source, EntityMaid.class);
    }
}
