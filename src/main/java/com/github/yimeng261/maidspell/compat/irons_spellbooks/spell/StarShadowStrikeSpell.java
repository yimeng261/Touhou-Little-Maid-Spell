package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.StarShadowStrikeEntity;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("removal")
@AutoSpellConfig
public class StarShadowStrikeSpell extends AbstractSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "star_shadow_strike");

    /** 刃光的判定半径（以施法者为球心）。 */
    private static final float SLASH_RADIUS = 5.25F;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(15)
            .build();

    public StarShadowStrikeSpell() {
        manaCostPerLevel = 15;
        baseSpellPower = 5;
        spellPowerPerLevel = 2;
        castTime = 10;
        baseManaCost = 30;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage",
                getDamageText(spellLevel, caster)));
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(MaidSpellSounds.STAR_SHADOW_STRIKE.get());
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public boolean canBeInterrupted(@Nullable Player player) {
        return false;
    }

    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        return getCastTime(spellLevel);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide) {
            boolean mirrored = SpellSelectionManager.OFFHAND.equals(magicData.getCastingEquipmentSlot());
            performSlash(level, spellLevel, caster, mirrored);
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    public void performSlash(Level level, int spellLevel, LivingEntity caster, boolean mirrored) {
        if (level.isClientSide) {
            return;
        }
        float radius = SLASH_RADIUS;
        Vec3 forward = caster.getForward();
        Vec3 hitLocation = caster.position()
                .add(0.0D, caster.getBbHeight() * 0.3D, 0.0D)
                .add(forward.scale(1.9D));
        AABB searchArea = AABB.ofSize(hitLocation, radius * 2.0F, radius, radius * 2.0F);

        for (LivingEntity livingTarget : level.getEntitiesOfClass(LivingEntity.class, searchArea,
                target -> target != caster && target.isAlive() && target.isPickable())) {
            if (MaidSpellAllyResolver.areFriendly(caster, livingTarget)
                    || caster.distanceToSqr(livingTarget) >= radius * radius
                    || livingTarget.position().subtract(caster.getEyePosition()).dot(forward) < 0.0D
                    || !Utils.hasLineOfSight(level, caster.getEyePosition(),
                            livingTarget.getBoundingBox().getCenter(), true)) {
                continue;
            }

            Vec3 offset = livingTarget.getBoundingBox().getCenter().subtract(caster.getEyePosition());
            DamageSource voidDamage = new DamageSource(
                    livingTarget.damageSources().fellOutOfWorld().typeHolder(), caster);
            if (offset.dot(forward) >= 0.0D && livingTarget.hurt(voidDamage, getDamage(spellLevel, caster))) {
                MagicManager.spawnParticles(level, ParticleHelper.UNSTABLE_ENDER,
                        livingTarget.getX(), livingTarget.getY() + livingTarget.getBbHeight() * 0.5D,
                        livingTarget.getZ(), 50, livingTarget.getBbWidth() * 0.5D,
                        livingTarget.getBbHeight() * 0.5D, livingTarget.getBbWidth() * 0.5D,
                        0.03D, false);
                EnchantmentHelper.doPostDamageEffects(caster, livingTarget);
            }
        }

        StarShadowStrikeEntity visual = new StarShadowStrikeEntity(level, mirrored);
        visual.moveTo(hitLocation);
        visual.setYRot(caster.getYRot());
        visual.setXRot(caster.getXRot());
        level.addFreshEntity(visual);
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) + Utils.getWeaponDamage(caster, MobType.UNDEFINED);
    }

    private String getDamageText(int spellLevel, LivingEntity caster) {
        String spellDamage = Utils.stringTruncation(getSpellPower(spellLevel, caster), 1);
        if (caster == null) {
            return spellDamage;
        }
        float weaponDamage = Utils.getWeaponDamage(caster, MobType.UNDEFINED);
        return weaponDamage > 0.0F
                ? spellDamage + String.format(" (+%s)", Utils.stringTruncation(weaponDamage, 1))
                : spellDamage;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.pass();
    }
}
