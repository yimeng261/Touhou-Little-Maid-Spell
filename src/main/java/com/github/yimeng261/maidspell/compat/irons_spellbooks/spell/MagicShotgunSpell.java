package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedMagicMissileEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MagicShotgunSpell extends AbstractSpell implements BossExclusiveSpell {
    public static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "magic_shotgun");
    private static final float HALF_SPREAD_RADIANS = 7.5F * Mth.DEG_TO_RAD;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(4)
            .build();

    public MagicShotgunSpell() {
        baseManaCost = 24;
        manaCostPerLevel = 6;
        baseSpellPower = 12;
        spellPowerPerLevel = 2;
        castTime = 0;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getProjectileDamage(spellLevel, caster), 2)),
                Component.translatable("ui.irons_spellbooks.projectile_count", getProjectileCount(spellLevel)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide) {
            int count = getProjectileCount(spellLevel);
            float damage = getProjectileDamage(spellLevel, caster);
            Vec3 forward = caster.getLookAngle().normalize();
            Vec3 spawn = caster.position().add(0.0D,
                    caster.getEyeHeight() - 0.15D, 0.0D);
            RandomSource random = caster.getRandom();
            double rotationOffset = random.nextDouble() * Mth.TWO_PI;

            for (int i = 0; i < count; i++) {
                Vec3 direction = sampleConeDirection(forward, i, count, random, rotationOffset);

                ModifiedMagicMissileEntity missile = new ModifiedMagicMissileEntity(level, caster, this);
                missile.setPos(spawn.add(direction.scale(0.25D)));
                missile.shoot(direction);
                missile.setDamage(damage);
                level.addFreshEntity(missile);
            }
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    private Vec3 sampleConeDirection(Vec3 forward, int index, int count,
                                     RandomSource random, double rotationOffset) {
        Vec3 referenceAxis = Math.abs(forward.y) < 0.99D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 tangentA = forward.cross(referenceAxis).normalize();
        Vec3 tangentB = forward.cross(tangentA).normalize();

        double stratum = (index + random.nextDouble()) / count;
        double maxCos = Math.cos(HALF_SPREAD_RADIANS);
        double cosTheta = 1.0D - stratum * (1.0D - maxCos);
        double sinTheta = Math.sqrt(Math.max(0.0D, 1.0D - cosTheta * cosTheta));
        double azimuthJitter = (random.nextDouble() - 0.5D) * Mth.TWO_PI / count * 0.35D;
        double azimuth = rotationOffset + index * GOLDEN_ANGLE + azimuthJitter;

        return forward.scale(cosTheta)
                .add(tangentA.scale(Math.cos(azimuth) * sinTheta))
                .add(tangentB.scale(Math.sin(azimuth) * sinTheta))
                .normalize();
    }

    public int getProjectileCount(int spellLevel) {
        return Mth.clamp(spellLevel + 3, 4, 8);
    }

    public float getProjectileDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.25F;
    }

    /** 超类的具体方法优先于接口默认方法，必须显式覆写。 */
    @Override
    public boolean allowCrafting() {
        return false;
    }

    @Override
    public boolean allowLooting() {
        return false;
    }
}
