package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedStarfallCloudEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.spells.ender.StarfallSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ModifiedStarfallSpell extends StarfallSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "starfall_modified");

    /**
     * 蓄力时长：8 秒。
     *
     * <p>原版星落是 160 tick 的 CONTINUOUS（按住一直下），本模组把它改成要蓄满才放的
     * LONG，时长就只剩这一个来源，所以直接写在这里。
     *
     * <p>对她和女仆都是同一个数：{@code getEffectiveCastTime} 会再乘一道施法者的
     * {@code cast_time_reduction}（LONG 走 {@code 2 - softCapFormula(x)}），
     * 而酒狐没加过那条属性、默认 1.0 恰好抵消，160 tick 就是 8 秒。
     */
    private static final int CAST_TIME_TICKS = 8 * 20;

    /** 冷却 45 秒。 */
    private static final int COOLDOWN_TICKS = 45 * 20;

    public ModifiedStarfallSpell() {
        this.castTime = CAST_TIME_TICKS;
        // 彗星伤害 = 法术强度 * 0.5，所以强度取 6 + 2/级：
        // 10 级强度 24 → 单发 12 伤害，每升一级单发伤害多 1 点。
        // 强度字段是 int，取不了 6.4；要小数得连 spellPowerPerLevel 一起放大十倍，
        // 那样 1 级只有 0.8 点伤害，手感反而更差。
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
    }

    /**
     * 铁魔法的冷却读的是 {@code ServerConfigs} 里按法术 id 生成的那张服务端配置表，
     * {@code getDefaultConfig().setCooldownSeconds()} 只是生成配置时的初值、运行时并不生效
     * （配置一旦生成也不会再跟着代码走）。所以这里直接覆盖，保证冷却恒定 45 秒。
     */
    @Override
    public int getSpellCooldown() {
        return COOLDOWN_TICKS;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return new DefaultConfig()
                .setMinRarity(SpellRarity.UNCOMMON)
                .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
                .setMaxLevel(10)
                .setCooldownSeconds(45)
                .build();
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_ANIMATION;
    }

    /**
     * 蓄力音效借用原版黑洞的 {@code spell.black_hole.charge}。
     *
     * <p>原版星落是 CONTINUOUS，靠一只不断重播的 {@code ender_cast} 撑场面；改成长吟唱之后
     * 那一段没有人声，八秒的蓄力就只剩动画在动。黑洞同样是 LONG 的蓄力法术
     * （它的 {@code castTime} 是 100t，比这里的 160t 短，但同一段素材本来就是按
     * 「越蓄越紧」铺的，拉长听不出接缝），所以直接调它那一对，而不是自己再录一份。
     */
    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.BLACK_HOLE_CHARGE.get());
    }

    /** 释放音效同理，用黑洞的 {@code spell.black_hole.cast}。 */
    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.BLACK_HOLE_CAST.get());
    }

    /**
     * 蓄力被打断时要把那条音效掐掉。
     *
     * <p>黑洞自己也是这么做的（{@code BlackHoleSpell.stopSoundOnCancel}）。八秒的蓄力音
     * 不是一声响，取消施法后它还会接着放完 —— 听起来就像法术已经放出去了。
     */
    @Override
    public boolean stopSoundOnCancel() {
        return true;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData magicData) {
        if (level.isClientSide) {
            return;
        }

        float cometDamage = getSpellPower(spellLevel, caster) * 0.5F;
        ModifiedStarfallCloudEntity cloud = new ModifiedStarfallCloudEntity(level, caster, cometDamage);
        cloud.setPos(caster.position());
        level.addFreshEntity(cloud);
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity caster, @Nullable MagicData magicData) {
        // The modified storm starts once the eight-second long cast completes.
    }
}
