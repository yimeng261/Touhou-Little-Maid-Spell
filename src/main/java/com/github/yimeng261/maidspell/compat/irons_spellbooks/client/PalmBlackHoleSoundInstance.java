package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** One positional loop per tracked ritual, stopped on impact or cancellation. */
public final class PalmBlackHoleSoundInstance extends AbstractTickableSoundInstance {
    private final PalmBlackHoleEntity ritual;

    private PalmBlackHoleSoundInstance(PalmBlackHoleEntity ritual) {
        super(MaidSpellSounds.PALM_BLACK_HOLE_LOOP.get(), SoundSource.HOSTILE,
                SoundInstance.createUnseededRandom());
        this.ritual = ritual;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.7F;
        updatePosition();
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof PalmBlackHoleEntity ritual) {
            Minecraft.getInstance().getSoundManager().play(new PalmBlackHoleSoundInstance(ritual));
        }
    }

    @Override
    public void tick() {
        if (ritual.isRemoved() || Minecraft.getInstance().level != ritual.level()
                || ritual.visualAge(0) >= PalmBlackHoleTimeline.IMPACT_TICK) {
            stop();
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        float age = ritual.visualAge(0);
        this.x = ritual.getX();
        this.y = ritual.getY() + (age >= PalmBlackHoleTimeline.TOP_TICK
                ? ritual.visualHeight(age) : 1.5D);
        this.z = ritual.getZ();
    }
}
