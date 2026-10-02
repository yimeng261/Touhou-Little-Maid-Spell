package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.CompanionBlackHoleEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * 施法者头顶的跟随黑洞，只吸引其他施法者的弹射物。
 * 继承 ISS 黑洞的引力，追踪过滤由 Mixin 处理；存活时间随等级增长，最长 30 秒。
 */
@SuppressWarnings("removal")
@AutoSpellConfig
public class CompanionBlackHoleSpell extends AbstractSpell {
    public static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "companion_black_hole");

    private static final int CAST_TIME_TICKS = 3 * 20;

    private static final int BASE_DURATION_TICKS = 12 * 20;

    /** 五级为 30 秒，低于旧版 ISS 的 32 秒硬上限。 */
    private static final int DURATION_PER_LEVEL_TICKS = 6 * 20;

    /** 旧版 ISS 黑洞的固定自毁阈值。 */
    private static final int VANILLA_DISCARD_TICKS = 20 * 16 * 2;

    /** 一级半径与球心到施法者眼睛的 5 格高度差相等，能覆盖眼睛高度的弹道。 */
    private static final float BASE_RADIUS = 5.0F;
    private static final float RADIUS_PER_LEVEL = 0.5F;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    public CompanionBlackHoleSpell() {
        this.castTime = CAST_TIME_TICKS;
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
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getDamage(spellLevel, caster), 1)),
                // ISS 使用 duration 翻译键，参数为秒。
                Component.translatable("ui.irons_spellbooks.duration",
                        getDurationTicks(spellLevel) / 20));
    }

    /**
     * 先设置半径再计算落点；ISS 改变半径时会重建包围盒。
     * 后续跟随由实体 tick 处理。
     */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide) {
            CompanionBlackHoleEntity blackHole = new CompanionBlackHoleEntity(level, caster);
            float radius = getRadius(spellLevel, caster);
            blackHole.setRadius(radius);
            Vec3 eyes = caster.getEyePosition();
            Vec3 spawn = new Vec3(eyes.x,
                    CompanionBlackHoleEntity.placementY(eyes.y, radius), eyes.z);
            blackHole.setPos(spawn.x, spawn.y, spawn.z);
            blackHole.setDamage(getDamage(spellLevel, caster));
            maidspell$applyDuration(blackHole, getDurationTicks(spellLevel));
            level.addFreshEntity(blackHole);
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /**
     * 新版 ISS 支持 setDuration；旧版缺少此方法时沿用 {@value #VANILLA_DISCARD_TICKS} tick 的固定自毁时间。
     * 反射避免宽版本依赖下的 NoSuchMethodError。
     */
    private static void maidspell$applyDuration(CompanionBlackHoleEntity blackHole, int ticks) {
        try {
            java.lang.reflect.Method setter = CompanionBlackHoleEntity.class.getMethod("setDuration", int.class);
            setter.invoke(blackHole, ticks);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 旧版 ISS 沿用固定自毁时间。
        }
    }

    /** 一级 12 秒，每级增加 6 秒。 */
    public int getDurationTicks(int spellLevel) {
        return BASE_DURATION_TICKS + DURATION_PER_LEVEL_TICKS * (Math.max(1, spellLevel) - 1);
    }

    public float getRadius(int spellLevel, LivingEntity caster) {
        return Mth.clamp(BASE_RADIUS + RADIUS_PER_LEVEL * (Math.max(1, spellLevel) - 1),
                BASE_RADIUS, 48.0F);
    }

    /** 伤害照法术强度走，比原版黑洞低一档：它主要是个防守/控制件，不是主输出。 */
    public float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.5F;
    }

    /** 可在卷轴撰写台制作，另有酒狐交易和战利品来源。 */
    @Override
    public boolean allowCrafting() {
        return true;
    }

    /** 保留随机战利品来源。 */
    @Override
    public boolean allowLooting() {
        return true;
    }
}
