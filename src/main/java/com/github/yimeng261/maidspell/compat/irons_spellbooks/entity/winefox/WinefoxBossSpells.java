package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.winefox.WinefoxSpellChoice;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * 万法酒狐的铁魔法法术行为。boss 本身只在装了铁魔法时注册，所以这里直接调用铁魔法 API。
 */
public final class WinefoxBossSpells {

    private static final ResourceLocation ARCANE_SHACKLE_ID =
            new ResourceLocation("irons_spellbooks", "arcane_shackle");

    private WinefoxBossSpells() {
    }

    public static void addAttributes(AttributeSupplier.Builder builder) {
        builder.add(AttributeRegistry.MAX_MANA.get(), 1_000_000.0D)
                .add(AttributeRegistry.SPELL_POWER.get(), 1.0D)
                .add(AttributeRegistry.CASTING_MOVESPEED.get(), 1.0D)
                .add(AttributeRegistry.ENDER_SPELL_POWER.get(), 1.2D)
                .add(AttributeRegistry.FIRE_SPELL_POWER.get(), 1.0D)
                .add(AttributeRegistry.LIGHTNING_SPELL_POWER.get(), 1.0D)
                .add(AttributeRegistry.HOLY_SPELL_POWER.get(), 1.0D);
    }

    /**
     * 由 ISS 状态机管理吟唱和收尾，酒狐只选择施放的法术。
     * @return 是否成功开始吟唱
     */
    public static boolean cast(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                        WinefoxBossSpellAction action, int spellLevel) {
        if (boss.level().isClientSide) {
            return false;
        }
        if (!isSpellAvailable(action)) {
            return false;
        }
        int clampedLevel = Mth.clamp(spellLevel, 1, 10);
        AbstractSpell spell = getSpell(action);
        // 上一发还没结束就不再叠一发；下面用 isCasting() 判断本次是否真的起来了，也依赖这个前置。
        if (boss.isCasting()) {
            return false;
        }
        if (needsTargetData(action) && !IAuthoritativeHealth.combatAlive(target)) {
            return false;
        }
        // 净化是以自己为圆心的范围法术，转不转身都一样 —— 和 HEAL 同理，别让一次
        // 施法平白把她的朝向掰到玩家身上。黑洞则相反：它在 onCast 里沿视线射线找落点，
        // 必须先对准目标。
        if (target != null && action != WinefoxBossSpellAction.HEAL
                && action != WinefoxBossSpellAction.VOID_PHASE
                && action != WinefoxBossSpellAction.ECHOING_STRIKES
                && action != WinefoxBossSpellAction.CLEANSE) {
            faceTarget(boss, target);
        }

        MagicData magicData = boss.getMagicData();
        if (needsTargetData(action)) {
            // 必须在 initiateCastSpell 之前设：它内部的 onServerPreCast 就要读这份数据。
            // MagicData.initiateCast 不会碰 additionalCastData，所以设了不会被冲掉（已核对）。
            magicData.setAdditionalCastData(new TargetEntityCastData(target));
        }

        boss.initiateCastSpell(spell, clampedLevel);
        // initiateCastSpell 是 void 的：法术为 none、或 checkPreCastConditions 不通过时会静默放弃。
        // 上面已确保进来时不在施法，所以这里为 true 就说明这一发确实起来了。
        return boss.isCasting();
    }

    /**
     * Cast a spell copied from a player or maid without waiting through its
     * normal wind-up. The regular mob entry point is still used so its target
     * data and pre-cast hooks remain compatible with Iron's Spellbooks.
     */
    public static boolean castInstant(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                                      AbstractSpell spell, int spellLevel) {
        if (boss.level().isClientSide || spell == null || spell == SpellRegistry.none()
                || boss.isCasting()) {
            return false;
        }
        if (IAuthoritativeHealth.combatAlive(target)) {
            boss.setTarget(target);
            faceTarget(boss, target);
        }
        int level = Mth.clamp(spellLevel, 1, 10);
        boss.initiateCastSpell(spell, level);
        if (!boss.isCasting()) {
            return false;
        }
        MagicData magicData = boss.getMagicData();
        spell.onCast(boss.level(), level, boss, io.redspace.ironsspellbooks.api.spells.CastSource.MOB, magicData);
        boss.castComplete();
        return true;
    }

    public static boolean castCustom(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                                     WinefoxSpellChoice choice) {
        if (boss.level().isClientSide || boss.isCasting() || choice == null) return false;
        AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(choice.id()));
        if (spell == SpellRegistry.none()) return false;
        try {
            if (IAuthoritativeHealth.combatAlive(target)) faceTarget(boss, target);
            if (IAuthoritativeHealth.combatAlive(target)) {
                boss.getMagicData().setAdditionalCastData(new TargetEntityCastData(target));
            }
            boss.initiateCastSpell(spell, Mth.clamp(choice.level(), 1, Math.max(1, spell.getMaxLevel())));
            return boss.isCasting();
        } catch (RuntimeException exception) {
            boss.cancelCast();
            com.github.yimeng261.maidspell.MaidSpellMod.LOGGER.warn(
                    "Stellar Witch cannot cast configured spell {}", choice.id(), exception);
            return false;
        }
    }

    public static int customCooldown(WinefoxSpellChoice choice, double scale) {
        AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(choice.id()));
        return spell == SpellRegistry.none() ? 20 : Math.max(1, Mth.ceil(spell.getSpellCooldown() * scale));
    }

    static WinefoxSpellChoice choiceFor(WinefoxBossSpellAction action) {
        AbstractSpell spell = getSpell(action);
        if (spell == SpellRegistry.none()) return null;
        int level = switch (action) {
            case EVASION, CLEANSE -> 1;
            case BLACK_HOLE -> 3;
            case SUMMON_SWORDS, MODIFIED_TELEPORT, ARROW_VOLLEY, ARCANE_SHACKLE -> 4;
            default -> 5;
        };
        return WinefoxSpellChoice.valid(spell.getSpellId(), level);
    }

    public static List<WinefoxSpellChoice> defaultChoices(boolean secondPhase) {
        return WinefoxCombatGoal.defaultChoices(secondPhase);
    }

    /** 这两个法术要在施法数据里带上目标实体，没有目标就没法施。 */
    private static boolean needsTargetData(WinefoxBossSpellAction action) {
        return action == WinefoxBossSpellAction.MODIFIED_TELEPORT
                || action == WinefoxBossSpellAction.SWORD_PRISON
                || action == WinefoxBossSpellAction.ARROW_VOLLEY
                || action == WinefoxBossSpellAction.ARCANE_SHACKLE;
    }

    public static boolean isCasting(LivingEntity entity) {
        if (entity instanceof Player) {
            return MagicData.getPlayerMagicData(entity).isCasting();
        }
        return entity instanceof IMagicEntity magicEntity && magicEntity.isCasting();
    }

    public static boolean hasVoidPhase(MagicalWinefoxBossEntity boss) {
        return boss.hasEffect(IronsSpellbooksCompatEffects.VOID_PHASE.get());
    }

    /**
     * 她这一发法术的冷却：铁魔法给该法术定的基础冷却乘上一个倍率。
     *
     * <p>基础值一律从法术自己身上取，不在本模组这边另抄一张表 ——
     * 铁魔法调平衡的时候她跟着一起变，不会悄悄跑偏。倍率是她相对普通施法者的加速。
     */
    public static int getCooldownTicks(WinefoxBossSpellAction action, double multiplier) {
        return Math.max(1, Mth.ceil(getSpell(action).getSpellCooldown() * multiplier));
    }

    /**
     * 检查法术是否存在于当前加载的铁魔法版本中。
     * 奥术镣铐在 3.16 才加入，旧版注册表会返回 {@code none()}。
     */
    public static boolean isSpellAvailable(WinefoxBossSpellAction action) {
        return action != WinefoxBossSpellAction.ARCANE_SHACKLE
                || SpellRegistry.getSpell(ARCANE_SHACKLE_ID) != SpellRegistry.none();
    }

    /**
     * 普通法术通过稳定的 {@link RegistryObject} 字段获取；奥术镣铐使用注册 ID 查询，
     * 避免旧版铁魔法没有该字段时触发 {@link NoSuchFieldError}。
     */
    private static AbstractSpell getSpell(WinefoxBossSpellAction action) {
        return switch (action) {
            case MAGIC_MISSILE -> SpellRegistry.MAGIC_MISSILE_SPELL.get();
            case COUNTERSPELL -> SpellRegistry.COUNTERSPELL_SPELL.get();
            case MAGIC_ARROW -> SpellRegistry.MAGIC_ARROW_SPELL.get();
            case SUMMON_SWORDS -> SpellRegistry.SUMMON_SWORDS.get();
            case FIREBALL -> SpellRegistry.FIREBALL_SPELL.get();
            case LIGHTNING_LANCE -> SpellRegistry.LIGHTNING_LANCE_SPELL.get();
            case LIGHTNING_BOLT -> SpellRegistry.LIGHTNING_BOLT_SPELL.get();
            case ARROW_VOLLEY -> SpellRegistry.ARROW_VOLLEY_SPELL.get();
            case EVASION -> SpellRegistry.EVASION_SPELL.get();
            case ARCANE_SHACKLE -> SpellRegistry.getSpell(ARCANE_SHACKLE_ID);
            case HEAL -> SpellRegistry.HEAL_SPELL.get();
            case ABYSSAL_SHROUD -> SpellRegistry.ABYSSAL_SHROUD_SPELL.get();
            case MODIFIED_STARFALL -> IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get();
            case MAGIC_SHOTGUN -> IronsSpellbooksCompatSpells.MAGIC_SHOTGUN.get();
            case VOID_PHASE -> IronsSpellbooksCompatSpells.VOID_PHASE.get();
            case ECHOING_STRIKES -> SpellRegistry.ECHOING_STRIKES_SPELL.get();
            case SHADOW_SLASH -> SpellRegistry.SHADOW_SLASH.get();
            case MODIFIED_TELEPORT -> IronsSpellbooksCompatSpells.MODIFIED_TELEPORT.get();
            case STAR_SHADOW_STRIKE -> IronsSpellbooksCompatSpells.STAR_SHADOW_STRIKE.get();
            case SHOCKWAVE -> SpellRegistry.SHOCKWAVE_SPELL.get();
            case DIVINE_SMITE -> SpellRegistry.DIVINE_SMITE_SPELL.get();
            case SWORD_PRISON -> IronsSpellbooksCompatSpells.SWORD_PRISON.get();
            case CLEANSE -> SpellRegistry.CLEANSE_SPELL.get();
            case BLACK_HOLE -> SpellRegistry.BLACK_HOLE_SPELL.get();
            case TRIPLE_STAR_ARROW -> IronsSpellbooksCompatSpells.TRIPLE_STAR_ARROW.get();
            case COMPANION_BLACK_HOLE -> IronsSpellbooksCompatSpells.COMPANION_BLACK_HOLE.get();
            case SPELLBREAKING_ECHO -> IronsSpellbooksCompatSpells.SPELLBREAKING_ECHO.get();
        };
    }

    private static void faceTarget(MagicalWinefoxBossEntity boss, LivingEntity target) {
        Vec3 direction = target.getEyePosition().subtract(boss.getEyePosition());
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
        boss.setYRot(yaw);
        boss.setXRot(pitch);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
    }
}
