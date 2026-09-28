package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SpellbreakingEchoEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("removal")
@AutoSpellConfig
public class SpellbreakingEchoSpell extends AbstractSpell {
    public static final ResourceLocation SPELL_ID = new ResourceLocation(MaidSpellMod.MOD_ID, "spellbreaking_echo");

    private static final int CAST_TIME_TICKS = 16;
    private static final int BASE_DURATION_TICKS = 80;
    private static final int DURATION_PER_LEVEL_TICKS = 40;
    private static final float BASE_RADIUS = 3.0F;
    private static final float RADIUS_PER_LEVEL = 0.5F;

    /** 作用范围加成只用于判定，不改变球体与碰撞盒大小。 */
    private static final float RADIUS_BONUS = 5.0F;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    public SpellbreakingEchoSpell() {
        castTime = CAST_TIME_TICKS;
        baseManaCost = 30;
        manaCostPerLevel = 8;
        baseSpellPower = 6;
        spellPowerPerLevel = 1;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.BLACK_HOLE_CHARGE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.BLACK_HOLE_CAST.get());
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.TOUCH_GROUND_ANIMATION;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("spell.touhou_little_maid_spell.spellbreaking_echo.radius",
                        Utils.stringTruncation(getRadius(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.duration", getDurationTicks(spellLevel) / 20));
    }

    /**
     * 先设置球体半径再计算落点，之后由实体跟随施法者。
     * 球体半径与效果范围分开设置，避免把碰撞盒一起放大。
     */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide) {
            SpellbreakingEchoEntity echo = new SpellbreakingEchoEntity(level, caster);
            float ballRadius = getBallRadius(spellLevel);
            echo.setRadius(ballRadius);
            echo.setEffectRadius(getRadius(spellLevel));
            Vec3 eyes = caster.getEyePosition();
            echo.setPos(eyes.x, SpellbreakingEchoEntity.placementY(eyes.y, ballRadius), eyes.z);
            echo.setDurationTicks(getDurationTicks(spellLevel));
            level.addFreshEntity(echo);
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /**
     * 作用范围：一级 8 格，每级 +0.5，五级 10 格。球体尺寸仍由 getBallRadius 控制。
     */
    public float getRadius(int spellLevel) {
        return BASE_RADIUS + RADIUS_BONUS + RADIUS_PER_LEVEL * (Math.max(1, spellLevel) - 1);
    }

    /**
     * 球体本身的半径（也是它的碰撞盒半边）：保持原来的 3 格 + 每级 0.5，不跟着作用范围涨。
     *
     * <p>分开的理由见 {@link #onCast}：这个数一变，球的碰撞盒就是它的两倍宽。
     */
    public float getBallRadius(int spellLevel) {
        return BASE_RADIUS + RADIUS_PER_LEVEL * (Math.max(1, spellLevel) - 1);
    }

    public int getDurationTicks(int spellLevel) {
        return BASE_DURATION_TICKS + DURATION_PER_LEVEL_TICKS * (Math.max(1, spellLevel) - 1);
    }
}
