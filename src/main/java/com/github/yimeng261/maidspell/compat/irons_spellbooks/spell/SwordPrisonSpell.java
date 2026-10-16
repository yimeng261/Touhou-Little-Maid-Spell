package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SwordArrayEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SwordArrayLayout;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
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
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public class SwordPrisonSpell extends AbstractSpell implements BossExclusiveSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "sword_prison");

    private static final int TARGET_RANGE = 32;

    /** 拿不到施法者尺寸时（比如查看法术书 tooltip）假定的半宽。 */
    private static final double DEFAULT_CASTER_HALF_WIDTH = 0.3D;

    /**
     * 单手持剑横斩，直接借铁魔法自带的 {@code SLASH_ANIMATION}（{@code irons_spellbooks:instant_slash}）。
     *
     * <p>一份 ResourceLocation 同时喂两条路，这是有意的：玩家侧由 PlayerAnimator 按全名取
     * {@code assets/irons_spellbooks/player_animation/casting_animations.json} 里的 {@code instant_slash}；
     * Mob 侧走 TLM，{@code AnimationHolder} 只取 path、命名空间被丢掉，再由
     * {@code ISSCastingAnimationProvider} 补 {@code iss:} 前缀，落到模型包里的 {@code iss:instant_slash}。
     * 即同一名字在两侧各自解析，不需要为本法术单独做资源。
     *
     * <p>铁魔法给 INSTANT 的默认动画 {@code ANIMATION_INSTANT_CAST} 是一个 0.1875 秒的抬手，
     * 而这里要的是"挥剑召阵"，所以换成本模组其它剑系法术也在用的 SLASH 这一条。
     * 模型包缺轨道时由 provider 回退到 {@code iss:instant_projectile}。
     */
    private static final AnimationHolder CAST_START_ANIMATION = SpellAnimations.SLASH_ANIMATION;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(40)
            .build();

    public SwordPrisonSpell() {
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
    public AnimationHolder getCastStartAnimation() {
        return CAST_START_ANIMATION;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        return Utils.preCastTargetHelper(level, caster, magicData, this, TARGET_RANGE, 0.2F,
                true, target -> target != caster && !MaidSpellAllyResolver.areFriendly(caster, target));
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getSwordDamage(spellLevel, caster), 2)),
                Component.translatable("ui.irons_spellbooks.projectile_count",
                        getTotalSwordCount(spellLevel, getTargetHalfWidth(caster))));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide
                && magicData.getAdditionalCastData() instanceof TargetEntityCastData targetData) {
            LivingEntity target = targetData.getTarget((ServerLevel) level);
            if (IAuthoritativeHealth.combatAlive(target)
                    && !MaidSpellAllyResolver.areFriendly(caster, target)) {
                summonSwordRings((ServerLevel) level, spellLevel, caster, target);
                if (caster instanceof MagicalWinefoxBossEntity boss) {
                    boss.recoverAfterSwordPrison(target);
                }
            }
            magicData.resetAdditionalCastData();
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /** A single controller keeps each cast's ring timing independent of other scheduled spells. */
    private void summonSwordRings(ServerLevel level, int spellLevel,
                                  LivingEntity caster, LivingEntity target) {
        level.addFreshEntity(new SwordArrayEntity(level, caster, target,
                spellLevel, getSwordDamage(spellLevel, caster)));
    }

    /** 一级两圈，此后每一级多一圈。 */
    public static int getRingCount(int spellLevel) {
        return SwordArrayLayout.ringCount(spellLevel);
    }

    private static double getTargetHalfWidth(LivingEntity target) {
        return target != null ? target.getBbWidth() * 0.5D : DEFAULT_CASTER_HALF_WIDTH;
    }

    /** Same visual density as the controller's deterministic layout. */
    public static int getSwordsForRing(double radius) {
        return SwordArrayLayout.swordsForRadius(radius);
    }

    /** 所有圆环上的剑，加上阵心的一柄剑。 */
    public static int getTotalSwordCount(int spellLevel, double targetHalfWidth) {
        int ringCount = getRingCount(spellLevel);
        double innerRadius = SwordArrayLayout.innerRadius(targetHalfWidth);
        int total = 1;
        for (int ring = 0; ring < ringCount; ring++) {
            total += getSwordsForRing(innerRadius + ring * SwordArrayLayout.RADIUS_STEP);
        }
        return total;
    }

    public float getSwordDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(Math.max(1, spellLevel), caster) * 0.5F;
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
