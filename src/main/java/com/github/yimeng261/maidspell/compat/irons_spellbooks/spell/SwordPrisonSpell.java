package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
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

import java.util.List;

public class SwordPrisonSpell extends AbstractSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "sword_prison");
    private static final int TARGET_RANGE = 32;

    /** 出发点在施法者头顶多高处，以及这个高度的随机浮动。 */
    private static final double LAUNCH_HEIGHT = 3.5D;
    private static final double LAUNCH_HEIGHT_JITTER = 1.5D;
    /** 出发点的水平散布，免得所有剑从同一个点出发叠成一根柱子。 */
    private static final double LAUNCH_SPREAD = 1.6D;

    /**
     * 投枪。玩家施法时由 PlayerAnimator 播，资源在
     * {@code assets/touhou_little_maid_spell/player_animation/spear_throw.json}。
     *
     * <p>铁魔法给 INSTANT 法术的默认动画是 {@code instant_projectile}，
     * 一个 0.1875s 的抬手 —— 配不上"召出一圈剑把人围死"。
     *
     * <p>酒狐那边不走这条：她是 Mob，用的是内置模型包里的
     * {@code iss:spear_throw}（骨骼完全不同）。两边用同一个 key：
     * {@code AnimationHolder} 给 Mob 的 {@code RawAnimation} 里那一条 stage 的名字
     * 就是这里这个 {@code ResourceLocation} 的 path，
     * {@code ISSCastingAnimationProvider} 再给它拼上 {@code iss:} 前缀。
     * 两边是同一个动作的两份实现，改表演时记得一起改。
     *
     * <p>那条动画自带一把投枪：{@code weapon3} 挂在 {@code LeftHand} 下面，
     * 0.6s 凭空出现在她左手里，<b>2.1s 甩出去</b>。所以酒狐的剑不能在第 0 帧就落 ——
     * 见 {@link #onCast} 里那一支。法术本身仍是瞬发的，玩家和女仆放它照旧当场落剑。
     */
    private static final AnimationHolder CAST_START_ANIMATION =
            new AnimationHolder(new ResourceLocation(MaidSpellMod.MOD_ID, "spear_throw"), true);

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
                Component.translatable("ui.irons_spellbooks.projectile_count", getTotalSwordCount(spellLevel)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide
                && magicData.getAdditionalCastData() instanceof TargetEntityCastData targetData) {
            LivingEntity target = targetData.getTarget((ServerLevel) level);
            if (target != null && target.isAlive()
                    && !MaidSpellAllyResolver.areFriendly(caster, target)) {
                if (caster instanceof MagicalWinefoxBossEntity boss) {
                    // 她的投掷动画 2.1s 才把枪甩出去，剑得等到那一帧才落，
                    // 否则就是"剑先出现，她过两秒才做投掷动作"。
                    boss.scheduleSwordRing(spellLevel, target);
                } else {
                    summonSwordRing(level, spellLevel, caster, target);
                }
            }
            magicData.resetAdditionalCastData();
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /**
     * 从施法者头顶斜着扑向目标，落点围成一圈把人锁在中间。
     *
     * <p>原先是在**目标正上方**生成、直直往下掉 —— 像天上凭空掉下来一堆剑，
     * 跟施法者没有任何视觉联系。现在改成从施法者上方一段高度出发，
     * 朝各自落点斜插过去：看得出剑是她甩出去的。
     *
     * <p>出发点带一点横向散布，否则十把剑从同一个点出发会在起手瞬间叠成一根柱子。
     *
     * <p>{@code public} 是因为酒狐要延后到投掷动画甩出去那一帧才调它，
     * 见 {@code MagicalWinefoxBossEntity.tickSwordRing}。
     */
    public void summonSwordRing(Level level, int spellLevel, LivingEntity caster, LivingEntity target) {
        int count = getSwordCount(spellLevel);
        float damage = getSwordDamage(spellLevel, caster);
        Vec3 center = target.getBoundingBox().getCenter();
        double groundY = target.getY();
        RandomSource random = caster.getRandom();
        double baseAngle = random.nextDouble() * Mth.TWO_PI;

        for (int i = 0; i < count; i++) {
            double angle = baseAngle + Mth.TWO_PI * i / count
                    + (random.nextDouble() - 0.5D) * 0.2D;
            double radius = target.getBbWidth() + 2.2D + random.nextDouble() * 1.4D;
            Vec3 landing = new Vec3(
                    center.x + Math.cos(angle) * radius + (random.nextDouble() - 0.5D) * 0.65D,
                    groundY,
                    center.z + Math.sin(angle) * radius + (random.nextDouble() - 0.5D) * 0.65D);
            launchSword(level, caster, random, landing, damage);
        }

        // 正中间那一把压轴，落点就是目标脚下。
        launchSword(level, caster, random, new Vec3(center.x, groundY, center.z), damage);
    }

    /** 从施法者头顶附近出发，朝 {@code landing} 斜插过去。 */
    private static void launchSword(Level level, LivingEntity caster, RandomSource random,
                                    Vec3 landing, float damage) {
        Vec3 spawn = caster.position()
                .add((random.nextDouble() - 0.5D) * LAUNCH_SPREAD,
                        caster.getBbHeight() + LAUNCH_HEIGHT + random.nextDouble() * LAUNCH_HEIGHT_JITTER,
                        (random.nextDouble() - 0.5D) * LAUNCH_SPREAD);

        Vec3 direction = landing.subtract(spawn);
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, -1.0D, 0.0D);
        }

        WinefoxSwordProjectileEntity sword = new WinefoxSwordProjectileEntity(level, caster);
        sword.setPos(spawn);
        sword.shoot(direction.normalize());
        sword.setRoll(random.nextFloat() * 360.0F);
        sword.setDamage(damage);
        level.addFreshEntity(sword);
    }

    public int getSwordCount(int spellLevel) {
        return Mth.clamp(spellLevel + 5, 6, 10);
    }

    public int getTotalSwordCount(int spellLevel) {
        return getSwordCount(spellLevel) + 1;
    }

    public float getSwordDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.4F;
    }
}
