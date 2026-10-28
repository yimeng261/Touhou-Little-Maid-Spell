package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import java.util.List;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SevenStarGuidanceEntity;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 固定在施法瞬间位置的北斗七星仪式。七次落击累计命中次数，最后由斗柄指向初始目标并必中。
 */
public class SevenStarGuidanceSpell extends AbstractSpell implements BossExclusiveSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "seven_star_guidance");

    private static final int TARGET_RANGE = 32;

    /** 收束一击在 0 次命中时的基础倍率（法强 × 该值即 {@link #getBaseFinalDamage}）。 */
    private static final float FINAL_DAMAGE_RATIO = 0.325F;

    /**
     * 收束一击每被前七次落击命中一次就提高这么多。
     *
     * <p>加成只能在实体侧结算 —— 只有它知道锁定目标吃了几发。所以这里给出的是唯一来源：
     * 实体收束时调 {@link #finalBeamDamage}，tooltip 报 0 次命中的基础值，两边不再各抄一份数字。
     */
    public static final float FINAL_HIT_BONUS = 0.20F;

    /** 碎裂补刀的基础倍率与每次命中的追加，见 {@link #shatterDamage}。 */
    public static final float SHATTER_BASE_RATIO = 0.75F;
    public static final float SHATTER_HIT_BONUS = 0.10F;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(30)
            .build();

    public SevenStarGuidanceSpell() {
        castTime = 3 * 20;
        baseManaCost = 45;
        manaCostPerLevel = 8;
        baseSpellPower = 2;
        spellPowerPerLevel = 2;
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
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_ANIMATION;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        // 玩家施法仍走 ISS 的目标预选 helper，因此卷轴施法前会显示目标锁定提示。
        // helper 找不到目标时只是不写入 TargetEntityCastData；七星实体随后会寻找最近敌人。
        if (caster instanceof ServerPlayer) {
            Utils.preCastTargetHelper(level, caster, magicData, this, 32, 0.2F,
                    false, target -> target != caster && !MaidSpellAllyResolver.areFriendly(caster, target));
        }
        // 目标是可选的：有目标时由它作为星阵中心，没有目标时由实体在施法位置附近寻找最近敌人。
        return IAuthoritativeHealth.combatAlive(caster);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getFinalDamage(spellLevel, caster, 0), 1)),
                Component.translatable("ui.irons_spellbooks.projectile_count", 8));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            LivingEntity target = null;
            if (magicData.getAdditionalCastData() instanceof TargetEntityCastData targetData) {
                LivingEntity candidate = targetData.getTarget(serverLevel);
                if (candidate != null && IAuthoritativeHealth.combatAlive(candidate)
                        && candidate != caster && !MaidSpellAllyResolver.areFriendly(caster, candidate)) {
                    target = candidate;
                }
            }
            SevenStarGuidanceEntity ritual = new SevenStarGuidanceEntity(
                    serverLevel, caster, target, getStarDamage(spellLevel, caster),
                    getBaseFinalDamage(spellLevel, caster));
            ritual.setPos(target != null ? target.getX() : caster.getX(),
                    target != null ? target.getY() : caster.getY(),
                    target != null ? target.getZ() : caster.getZ());
            serverLevel.addFreshEntity(ritual);
            magicData.resetAdditionalCastData();
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /** 前七次是较轻的落击，避免仪式过程本身压过最后的收束。 */
    public float getStarDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.18F;
    }

    /** 0 次命中时的收束伤害；落进实体后由实体按命中次数加成。 */
    public float getBaseFinalDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * FINAL_DAMAGE_RATIO;
    }

    /**
     * 收束一击的最终伤害：{@code 基础 × (1 + 命中次数 × 20%)}。
     *
     * <p>实体持有的只是基础值，所以它调这个静态方法；这里刻意不接受法术等级/施法者，
     * 避免同一份公式在实体里再写一遍。
     */
    public static float finalBeamDamage(float baseFinalDamage, int hitCount) {
        return baseFinalDamage * (1.0F + Math.max(0, hitCount) * FINAL_HIT_BONUS);
    }

    /** 碎裂补刀的最终伤害：{@code 基础 × (75% + 命中次数 × 10%)}。 */
    public static float shatterDamage(float baseFinalDamage, int hitCount) {
        return baseFinalDamage * (SHATTER_BASE_RATIO + Math.max(0, hitCount) * SHATTER_HIT_BONUS);
    }

    /** 保留旧签名：{@code hitCount} 只是想看「吃到几发」时的预期值，实际伤害仍由实体调上面的静态方法。 */
    public float getFinalDamage(int spellLevel, LivingEntity caster, int hitCount) {
        return finalBeamDamage(getBaseFinalDamage(spellLevel, caster), hitCount);
    }

    @Override
    public boolean allowCrafting() {
        return false;
    }

    @Override
    public boolean allowLooting() {
        return false;
    }
}
