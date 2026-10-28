package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ThreeEnclosuresFieldEntity;
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
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("removal")
@AutoSpellConfig
public class ThreeEnclosuresSpell extends AbstractSpell implements BossExclusiveSpell {
    public static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "three_enclosures");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(75)
            .build();

    public ThreeEnclosuresSpell() {
        castTime = 24;
        baseManaCost = 50;
        manaCostPerLevel = 12;
        baseSpellPower = 8;
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
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    /**
     * 这片场地的目标是可选的：生物有当前目标时在此记录下来；
     * 没有目标也允许继续施法，以生成以自身为中心向外展开的变体。
     */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster,
                                          MagicData magicData) {
        if (magicData.getAdditionalCastData() instanceof TargetEntityCastData targetData
                && level instanceof ServerLevel serverLevel) {
            LivingEntity target = targetData.getTarget(serverLevel);
            if (IAuthoritativeHealth.combatAlive(target)
                    && !MaidSpellAllyResolver.areFriendly(caster, target)) {
                return true;
            }
            magicData.resetAdditionalCastData();
        }
        // 玩家施法走铁魔法常规的射线辅助方法，它会在吟唱期间发送绿色的
        // 目标确认提示和客户端的锁定数据包；没有命中也允许继续，进入向外展开的模式。
        if (!(caster instanceof Mob)) {
            Utils.preCastTargetHelper(level, caster, magicData, this, 32, 0.2F,
                    false, target -> target != caster
                            && IAuthoritativeHealth.combatAlive(target)
                            && !MaidSpellAllyResolver.areFriendly(caster, target));
        }
        if (caster instanceof Mob mob && level instanceof ServerLevel serverLevel) {
            LivingEntity target = mob.getTarget();
            if (IAuthoritativeHealth.combatAlive(target)
                    && !MaidSpellAllyResolver.areFriendly(caster, target)) {
                magicData.setAdditionalCastData(new TargetEntityCastData(target));
            }
        }
        return true;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.AMETHYST_BLOCK_CHIME);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        // 玻璃破碎声只在实体真正碰到光壁时播放，避免每次起手都误报碰撞。
        return Optional.empty();
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
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("spell.touhou_little_maid_spell.three_enclosures.area"),
                Component.translatable("ui.irons_spellbooks.duration",
                        ThreeEnclosuresFieldEntity.TOTAL_TICKS / 20));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            LivingEntity target = null;
            if (magicData.getAdditionalCastData() instanceof TargetEntityCastData targetData) {
                target = targetData.getTarget(serverLevel);
            }

            // 长吟唱在铁魔法的某些路径上会丢失附加的目标数据；
            // 以首领当前的 AI 目标作为权威兜底，
            // 只有完全没有战斗目标时才以施法者自身为中心。
            boolean selectedTarget = IAuthoritativeHealth.combatAlive(target)
                    && !MaidSpellAllyResolver.areFriendly(caster, target);
            if (!selectedTarget
                    && caster instanceof Mob mob
                    && IAuthoritativeHealth.combatAlive(mob.getTarget())
                    && !MaidSpellAllyResolver.areFriendly(caster, mob.getTarget())) {
                target = mob.getTarget();
                selectedTarget = true;
            }
            if (!selectedTarget) {
                target = caster;
            }
            Vec3 center = target.position();
            ThreeEnclosuresFieldEntity field = new ThreeEnclosuresFieldEntity(
                    serverLevel, caster, center, spellLevel, !selectedTarget);
            serverLevel.addFreshEntity(field);
            magicData.resetAdditionalCastData();
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    public float getWallDamage(int spellLevel, LivingEntity caster) {
        return Math.max(1.0F, getSpellPower(spellLevel, caster) * 0.25F);
    }
}
