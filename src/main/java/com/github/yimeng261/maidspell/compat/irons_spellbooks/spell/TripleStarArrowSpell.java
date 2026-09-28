package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.SwordRingScheduler;
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
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * 一次吟唱连发三支魔法箭，动画和冷却只结算一次。
 * 后两支由 {@link SwordRingScheduler} 依次延迟发射。
 */
@SuppressWarnings("removal")
@AutoSpellConfig
public class TripleStarArrowSpell extends AbstractSpell implements BossExclusiveSpell {
    public static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "triple_star_arrow");

    public static final int SHOT_COUNT = 3;

    public static final int SHOT_INTERVAL_TICKS = 4;

    /** 三支箭的横向散角，弧度。 */
    private static final float SPREAD_RADIANS = 2.5F * Mth.DEG_TO_RAD;

    /** 中间箭先发，两侧各偏一个散角。 */
    private static final int[] SHOT_OFFSETS = {0, -1, 1};

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(10)
            .build();

    public TripleStarArrowSpell() {
        manaCostPerLevel = 5;
        baseSpellPower = 8;
        spellPowerPerLevel = 2;
        // 与原版魔法箭一样蓄力 30 tick。
        castTime = 30;
        baseManaCost = 40;
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
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.MAGIC_ARROW_CHARGE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.MAGIC_ARROW_RELEASE.get());
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getArrowDamage(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.projectile_count", SHOT_COUNT));
    }

    /** 每支箭在发射时读取施法者朝向，允许连发期间转向。 */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            float damage = getArrowDamage(spellLevel, caster);
            for (int shot = 0; shot < SHOT_COUNT; shot++) {
                int index = shot;
                if (shot == 0) {
                    fireArrow(serverLevel, caster, damage, index);
                } else {
                    SwordRingScheduler.schedule(serverLevel, SHOT_INTERVAL_TICKS, true,
                            () -> fireArrow(serverLevel, caster, damage, index));
                }
            }
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /** 延迟发射时重新检查施法者是否仍在该世界。 */
    private void fireArrow(ServerLevel level, LivingEntity caster, float damage, int shotIndex) {
        if (!IAuthoritativeHealth.combatAlive(caster) || caster.level() != level) {
            return;
        }

        Vec3 forward = caster.getLookAngle().normalize();
        Vec3 direction = spread(forward, SHOT_OFFSETS[Math.floorMod(shotIndex, SHOT_OFFSETS.length)]);
        Vec3 spawn = caster.position().add(0.0D,
                caster.getEyeHeight() - 0.2D, 0.0D).add(forward.scale(0.6D));

        MagicArrowProjectile arrow = new MagicArrowProjectile(level, caster);
        arrow.setPos(spawn);
        arrow.shoot(direction);
        arrow.setDamage(damage);
        level.addFreshEntity(arrow);
    }

    /** 绕竖直轴展开水平扇面。 */
    private static Vec3 spread(Vec3 forward, int steps) {
        if (steps == 0) {
            return forward;
        }
        double angle = SPREAD_RADIANS * steps;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(
                forward.x * cos + forward.z * sin,
                forward.y,
                -forward.x * sin + forward.z * cos
        ).normalize();
    }

    /** 单支箭造成法术强度的 45% 伤害。 */
    public float getArrowDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.45F;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.BOW_CHARGE_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.none();
    }

    /** 超类的具体方法优先于 {@link BossExclusiveSpell} 的默认方法，必须显式覆写。 */
    @Override
    public boolean allowCrafting() {
        return false;
    }

    @Override
    public boolean allowLooting() {
        return false;
    }
}
