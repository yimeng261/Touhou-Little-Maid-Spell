package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.effect.StaranchorStunEffect;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;

/** 只由铁魔法联动加载，负责在眩晕时打断施法并拦截新的施法。 */
public final class StaranchorStunSpellInterrupt {
    private StaranchorStunSpellInterrupt() {
    }

    public static void interrupt(LivingEntity entity) {
        if (entity instanceof MagicalWinefoxBossEntity boss) {
            boss.startStaranchorStun();
        } else if (entity instanceof IMagicEntity magicEntity && magicEntity.isCasting()) {
            magicEntity.cancelCast();
        }
        if (entity instanceof ServerPlayer player && MagicData.getPlayerMagicData(player).isCasting()) {
            Utils.serverSideCancelCast(player);
        }
    }

    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (StaranchorStunEffect.isStunned(event.getEntity())) event.setCanceled(true);
    }
}
