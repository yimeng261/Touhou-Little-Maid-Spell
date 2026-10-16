package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.Optional;

public class PalmBlackHoleSpell extends AbstractSpell implements BossExclusiveSpell {
    public static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "palm_black_hole");
    private static final AnimationHolder ANIMATION = new AnimationHolder("palm_black_hole", false, true);
    private final DefaultConfig config = new DefaultConfig().setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE).setMaxLevel(1).setCooldownSeconds(60).build();

    public PalmBlackHoleSpell() {
        castTime = PalmBlackHoleTimeline.IMPACT_TICK;
        baseManaCost = 100;
        baseSpellPower = (int) PalmBlackHoleTimeline.EXPLOSION_BASE_DAMAGE;
    }

    @Override public DefaultConfig getDefaultConfig() { return config; }
    @Override public CastType getCastType() { return CastType.LONG; }
    @Override public ResourceLocation getSpellResource() { return SPELL_ID; }
    @Override public AnimationHolder getCastStartAnimation() { return ANIMATION; }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.none(); }
    @Override public int getEffectiveCastTime(int level, LivingEntity caster) { return castTime; }
    @Override public boolean allowCrafting() { return false; }
    @Override public boolean allowLooting() { return false; }
    @Override public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.BEACON_POWER_SELECT); }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData data) {
        return caster instanceof MagicalWinefoxBossEntity boss && boss.isPhaseTwo()
                && boss.isBattleActive() && !boss.isTransitioning();
    }

    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity caster, MagicData data) {
        super.onServerPreCast(level, spellLevel, caster, data);
        if (caster instanceof MagicalWinefoxBossEntity boss) {
            boss.beginPalmBlackHoleCast();
            boss.getNavigation().stop();
            boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(new PalmBlackHoleEntity(level, boss));
        }
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity caster, MagicData data,
                                     boolean cancelled) {
        if (cancelled) {
            for (PalmBlackHoleEntity ritual : level.getEntitiesOfClass(PalmBlackHoleEntity.class,
                    caster.getBoundingBox().inflate(32), entity -> entity.belongsTo(caster))) {
                ritual.discard();
            }
        }
        if (caster instanceof MagicalWinefoxBossEntity boss) {
            boss.endPalmBlackHoleCast();
        }
        super.onServerCastComplete(level, spellLevel, caster, data, cancelled);
    }

}
