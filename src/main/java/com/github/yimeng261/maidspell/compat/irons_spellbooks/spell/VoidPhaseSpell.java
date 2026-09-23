package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public class VoidPhaseSpell extends AbstractSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "void_phase");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    public VoidPhaseSpell() {
        baseManaCost = 35;
        manaCostPerLevel = 8;
        baseSpellPower = 6;
        spellPowerPerLevel = 1;
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
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getBonusDamage(spellLevel, caster), 2)),
                Component.translatable("ui.irons_spellbooks.duration",
                        Utils.stringTruncation(getDurationTicks(spellLevel) / 20.0F, 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide) {
            // visible=false：关掉原版水泡粒子，虚空法术粒子由 VoidPhaseEffect 自己播。
            caster.addEffect(new MobEffectInstance(IronsSpellbooksCompatEffects.VOID_PHASE.get(),
                    getDurationTicks(spellLevel), spellLevel - 1, false, false, true));
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    public int getDurationTicks(int spellLevel) {
        return 15 * 20 + Math.max(0, spellLevel - 1) * 50;
    }

    public float getBonusDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.35F;
    }
}
