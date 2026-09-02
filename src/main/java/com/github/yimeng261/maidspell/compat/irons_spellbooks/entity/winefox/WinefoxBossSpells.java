package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 万法酒狐的铁魔法法术行为。boss 本身只在装了铁魔法时注册，所以这里直接调用铁魔法 API。
 */
public final class WinefoxBossSpells {

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
     * 发起一次施法。
     *
     * <p>以前这里是自己造一个 {@code new MagicData(true)}，然后把 {@code onServerPreCast} 与
     * {@code onCast} 连着调掉——等于绕开铁魔法整套吟唱状态机，法术全是瞬发的，
     * 前摇由酒狐这边另拿一套 {@code pendingCastTicks} 手算。
     *
     * <p>改继承 {@code AbstractSpellCastingMob} 之后直接走 {@code initiateCastSpell}：
     * 吟唱时长、{@code onServerCastTick}、CONTINUOUS 每 10t 复发、收尾、以及存档中断后的续播，
     * 全部由铁魔法自己管。酒狐只负责"什么时候选哪个法术"。
     *
     * @return 是否真的开始了一次吟唱
     */
    public static boolean cast(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                        WinefoxBossSpellAction action, int spellLevel) {
        if (boss.level().isClientSide) {
            return false;
        }
        int clampedLevel = Mth.clamp(spellLevel, 1, 10);
        AbstractSpell spell = getSpell(action);
        if (spell == null) {
            return false;
        }
        // 上一发还没结束就不再叠一发；下面用 isCasting() 判断本次是否真的起来了，也依赖这个前置。
        if (boss.isCasting()) {
            return false;
        }
        if (needsTargetData(action) && (target == null || !target.isAlive())) {
            return false;
        }
        if (target != null && action != WinefoxBossSpellAction.HEAL
                && action != WinefoxBossSpellAction.VOID_PHASE
                && action != WinefoxBossSpellAction.ECHOING_STRIKES) {
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

    /** 这两个法术要在施法数据里带上目标实体，没有目标就没法施。 */
    private static boolean needsTargetData(WinefoxBossSpellAction action) {
        return action == WinefoxBossSpellAction.MODIFIED_TELEPORT
                || action == WinefoxBossSpellAction.SWORD_PRISON;
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
     * 每一个 {@link WinefoxBossSpellAction} 都对得上一个已注册的法术，所以不会返回 null：
     * switch 是穷尽的，而 {@code RegistryObject.get()} 取不到时直接抛。
     */
    private static AbstractSpell getSpell(WinefoxBossSpellAction action) {
        return switch (action) {
            case MAGIC_MISSILE -> SpellRegistry.MAGIC_MISSILE_SPELL.get();
            case COUNTERSPELL -> SpellRegistry.COUNTERSPELL_SPELL.get();
            case MAGIC_ARROW -> SpellRegistry.MAGIC_ARROW_SPELL.get();
            case SUMMON_SWORDS -> SpellRegistry.SUMMON_SWORDS.get();
            case FIREBALL -> SpellRegistry.FIREBALL_SPELL.get();
            case LIGHTNING_LANCE -> SpellRegistry.LIGHTNING_LANCE_SPELL.get();
            case HEAL -> SpellRegistry.HEAL_SPELL.get();
            case MODIFIED_STARFALL -> IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get();
            case MAGIC_SHOTGUN -> IronsSpellbooksCompatSpells.MAGIC_SHOTGUN.get();
            case VOID_PHASE -> IronsSpellbooksCompatSpells.VOID_PHASE.get();
            case ECHOING_STRIKES -> SpellRegistry.ECHOING_STRIKES_SPELL.get();
            case SHADOW_SLASH -> SpellRegistry.SHADOW_SLASH.get();
            case MODIFIED_TELEPORT -> IronsSpellbooksCompatSpells.MODIFIED_TELEPORT.get();
            case FLAMING_STRIKE -> SpellRegistry.FLAMING_STRIKE_SPELL.get();
            case DIVINE_SMITE -> SpellRegistry.DIVINE_SMITE_SPELL.get();
            case SWORD_PRISON -> IronsSpellbooksCompatSpells.SWORD_PRISON.get();
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
