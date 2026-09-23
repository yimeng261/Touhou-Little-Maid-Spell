package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.SwordRingScheduler;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class SwordPrisonSpell extends AbstractSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "sword_prison");

    private static final int TARGET_RANGE = 32;

    /** 同一圈上相邻两柄剑的弧长上限，圈上剑数由它反推。只约束圈内，不约束圈间。 */
    private static final double MAX_SWORD_SPACING = 1.5D;
    /** 最内圈的最小半径，保证剑围住目标而不是插在身上。 */
    private static final double MIN_INNER_RADIUS = 1.2D;
    /**
     * 相邻两圈的半径差。圈间距与剑间距是两回事：1.5 格只约束同一圈内相邻的两柄剑，
     * 圈与圈之间没有这个限制，所以这里取一个比它更小的固定值，让剑阵更聚拢
     * （五级六圈的最外圈也只有 7.2 格），而不是随圈数把整个阵型撑开。
     */
    private static final double RING_RADIUS_STEP = 1.2D;
    /** 相邻两圈的落下间隔：0.25 秒 = 5 tick。 */
    private static final int RING_INTERVAL_TICKS = 5;
    /** 单圈剑数上限，避免高等级下实体数量失控。 */
    private static final int MAX_SWORDS_PER_RING = 48;
    private static final int MIN_SWORDS_PER_RING = 3;
    /** 拿不到施法者尺寸时（比如查看法术书 tooltip）假定的半宽。 */
    private static final double DEFAULT_CASTER_HALF_WIDTH = 0.3D;

    /** 剑从落点上方的斜上方扑向落点，避免远距离施法时被施法者自身高度遮挡。 */
    private static final double LAUNCH_HEIGHT = 8.0D;
    private static final double LAUNCH_HEIGHT_JITTER = 1.5D;
    /** 出发点的水平散布，免得一圈剑从同一个点出发叠成一根柱子。 */
    private static final double LAUNCH_SPREAD = 1.6D;
    /** 落点抖动，保留手掷的不规则感；幅度远小于间距上限，不会破坏圈内间距。 */
    private static final double LANDING_JITTER = 0.12D;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(12)
            .build();

    public SwordPrisonSpell() {
        baseManaCost = 35;
        manaCostPerLevel = 8;
        baseSpellPower = 8;
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
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_INSTANT_CAST;
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
            if (target != null && target.isAlive()
                    && !MaidSpellAllyResolver.areFriendly(caster, target)) {
                summonSwordRings((ServerLevel) level, spellLevel, caster, target);
            }
            magicData.resetAdditionalCastData();
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /**
     * 把落点按同心圆铺开：最内圈（最靠近目标的那圈）先落，之后每 0.5 秒往外落一圈。
     *
     * <p>每圈剑数由该圈周长与 {@link #MAX_SWORD_SPACING} 反推（{@code ceil(2πr / 1.5)}），
     * 于是同一圈上任意相邻两柄剑的弧长都压在上限内，圈越大剑越密。
     * 半径按固定步长 {@link #RING_RADIUS_STEP} 递增，而不是按"最密间距"递增，
     * 否则最内圈会被撑大、圈数被迫变少。
     */
    private void summonSwordRings(ServerLevel level, int spellLevel,
                                  LivingEntity caster, LivingEntity target) {
        List<Vec3[]> rings = buildLandingRings(spellLevel, target);
        float damage = getSwordDamage(spellLevel, caster);

        for (int ring = 0; ring < rings.size(); ring++) {
            Vec3[] landingPoints = rings.get(ring);
            if (ring == 0) {
                launchRing(level, caster, landingPoints, damage);
            } else {
                // chain=true：第 n 圈接在第 n-1 圈之后 0.5 秒，而不是都从施法那一刻起算。
                SwordRingScheduler.schedule(level, RING_INTERVAL_TICKS, true,
                        () -> launchRing(level, caster, landingPoints, damage));
            }
        }
    }

    /**
     * 先算好每一圈的落点，避免目标移动后各圈圆心错位。
     *
     * <p>第一维是圈（0 为最内圈），第二维是该圈内的落点。
     */
    private static List<Vec3[]> buildLandingRings(int spellLevel, LivingEntity target) {
        int ringCount = getRingCount(spellLevel);
        RandomSource random = target.getRandom();
        Vec3 center = target.getBoundingBox().getCenter();
        double groundY = target.getY();
        double baseAngle = random.nextDouble() * Mth.TWO_PI;

        // 内圈至少离目标半个身位，否则剑会插在身上而不是围住。
        double innerRadius = Math.max(MIN_INNER_RADIUS, getTargetHalfWidth(target) * 2.0D + 0.65D);
        List<Vec3[]> rings = new ArrayList<>(ringCount);

        for (int ring = 0; ring < ringCount; ring++) {
            double radius = innerRadius + ring * RING_RADIUS_STEP;
            int swordCount = getSwordsForRing(radius);
            // 逐圈错开半个扇区，外圈的剑正好落在内圈两柄剑的缝隙外侧，不会连成一条辐条。
            double angleOffset = baseAngle + ring * Math.PI / swordCount;
            Vec3[] landingPoints = new Vec3[swordCount];

            for (int i = 0; i < swordCount; i++) {
                double angle = angleOffset + Mth.TWO_PI * i / swordCount;
                landingPoints[i] = new Vec3(
                        center.x + Math.cos(angle) * radius + jitter(random),
                        groundY,
                        center.z + Math.sin(angle) * radius + jitter(random));
            }

            rings.add(landingPoints);
        }

        return rings;
    }

    private static double jitter(RandomSource random) {
        return (random.nextDouble() - 0.5D) * 2.0D * LANDING_JITTER;
    }

    /** 一圈同时落下。 */
    private static void launchRing(ServerLevel level, LivingEntity caster,
                                   Vec3[] landingPoints, float damage) {
        for (Vec3 landing : landingPoints) {
            launchSword(level, caster, landing, damage);
        }
    }

    /** 从落点上方的斜上方出发，朝 {@code landing} 斜插过去。 */
    private static void launchSword(ServerLevel level, LivingEntity caster,
                                    Vec3 landing, float damage) {
        RandomSource random = level.getRandom();
        Vec3 spawn = landing.add(
                (random.nextDouble() - 0.5D) * LAUNCH_SPREAD,
                LAUNCH_HEIGHT + random.nextDouble() * LAUNCH_HEIGHT_JITTER,
                (random.nextDouble() - 0.5D) * LAUNCH_SPREAD);

        Vec3 direction = landing.subtract(spawn);
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, -1.0D, 0.0D);
        }

        WinefoxSwordProjectileEntity sword = new WinefoxSwordProjectileEntity(level, caster);
        sword.setPos(spawn);
        sword.shoot(direction.normalize());
        sword.setLandingY(landing.y);
        sword.setRoll(random.nextFloat() * 360.0F);
        sword.setDamage(damage);
        level.addFreshEntity(sword);
    }

    /** 一级两圈，此后每一级多一圈。 */
    public static int getRingCount(int spellLevel) {
        return Math.max(1, spellLevel) + 1;
    }

    private static double getTargetHalfWidth(LivingEntity target) {
        return target != null ? target.getBbWidth() * 0.5D : DEFAULT_CASTER_HALF_WIDTH;
    }

    /** 由圈周长反推剑数，保证同一圈内相邻两柄剑的弧长不超过 {@link #MAX_SWORD_SPACING}。 */
    public static int getSwordsForRing(double radius) {
        int swords = Mth.ceil(Mth.TWO_PI * radius / MAX_SWORD_SPACING);
        return Mth.clamp(swords, MIN_SWORDS_PER_RING, MAX_SWORDS_PER_RING);
    }

    /** 全部圈上的剑数总和。最内圈本身就是包围圈，不再额外补一柄压轴剑。 */
    public static int getTotalSwordCount(int spellLevel, double targetHalfWidth) {
        int ringCount = getRingCount(spellLevel);
        double innerRadius = Math.max(MIN_INNER_RADIUS, targetHalfWidth * 2.0D + 0.65D);
        int total = 0;
        for (int ring = 0; ring < ringCount; ring++) {
            total += getSwordsForRing(innerRadius + ring * RING_RADIUS_STEP);
        }
        return total;
    }

    public float getSwordDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.4F;
    }
}
