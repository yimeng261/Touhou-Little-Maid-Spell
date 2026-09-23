package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 万法酒狐的战斗 AI：走位、近战连段、选法术、爆发点名、卡住了跳一下。
 *
 * <p>原先是 {@link MagicalWinefoxBossEntity} 里的一个私有静态内部类，占了那个文件三分之一，
 * 搬出来之后实体那边只剩状态与同步。搬迁本身没改行为，只是把它够得着的那几样 从 {@code private} 放宽到包内可见（{@code isViableTarget}、{@code isBusyCombatAction}、 {@code teleportAwayFrom}、{@code recallSummons}、{@code cancelSwordRing}）—— 它们仍然只有这个类在用。
 */
final class WinefoxCombatGoal extends Goal {
    /**
     * 拉开距离用的传送距离。只有战斗 AI 会传送，所以跟着一起搬过来了。
     */
    private static final double COMBAT_TELEPORT_DISTANCE = 15.0D;

    /**
     * 连续这么多 tick 想走却没挪窝，就认定卡住了。
     */
    private static final int STUCK_TICKS_BEFORE_HOP = 20;
    /**
     * 一 tick 位移小于这个值就当没动（0.05 格）。
     */
    private static final double STUCK_MOVE_EPSILON_SQR = 0.0025D;
    private static final double HOP_VERTICAL_SPEED = 0.55D;
    private static final double HOP_HORIZONTAL_SPEED = 0.35D;

    /**
     * Ignore ordinary jumps and small steps while following a target vertically.
     * A larger change still makes the flying boss move to the target's level.
     */
    private static final double COMBAT_ALTITUDE_TOLERANCE = 1.75D;

    /** 一阶段的施法冷却相对法术基础值的倍率，见 {@link #getSpellCooldown}。 */
    private static final double PHASE_ONE_COOLDOWN_SCALE = 0.2D;

    /** 一阶段上下浮动的目标高度范围与重选间隔。 */
    private static final double PHASE_ONE_MIN_HOVER_HEIGHT = 0.5D;
    private static final double PHASE_ONE_FLOAT_MIN_DISTANCE = 1.0D;
    private static final double PHASE_ONE_FLOAT_MAX_DISTANCE = 2.0D;
    private static final int PHASE_ONE_FLOAT_MIN_INTERVAL = 80;
    private static final int PHASE_ONE_FLOAT_MAX_INTERVAL = 140;

    /** 二阶段的施法冷却倍率。比一阶段松，节奏改由近战撑。 */
    private static final double PHASE_TWO_COOLDOWN_SCALE = 0.5D;

    /**
     * 二阶段跟目标保持的水平距离。
     *
     * <p>原先二阶段直接把目标所在的那一格当成目标点，她会一路顶到人身上：碰撞箱互相推挤，
     * 画面里是她贴着玩家来回挤。改成站定在这个距离上出刀 —— 仍在近战判定
     * （{@code distanceToSqr <= 9}，即 3 格）之内，但不会贴脸。
     */
    private static final double PHASE_TWO_STANDOFF_DISTANCE = 2.0D;

    /**
     * 二阶段站定时允许的高度差。
     *
     * <p>近战那条判定用的是三维距离（{@code distanceToSqr(target) <= 9}），
     * 所以水平拉到 2 格之后垂直只剩很少的余量：她要是悬在目标上方两格多，
     * 水平再近也够不着 —— 表现为二阶段一刀都不砍。站定前必须把高度也收进这个带里。
     */
    private static final double PHASE_TWO_MELEE_VERTICAL_TOLERANCE = 1.0D;

    /** 近战出手间隔。 */
    private static final int BASE_MELEE_COOLDOWN_TICKS = 12;

    /**
     * 「深渊庇佑」的血量档位：每跌 10% 记一档，每档掷一次 50%。
     *
     * <p>第一档落在 90%（100% − 10%），不是 100% —— 满血时 {@code health <= max} 恒真，
     * 档位放在 1.0 会让她一开打就白掷一次。往下一直排到 10%，再往下的 0% 那一档
     * 永远用不到：她有 1 点血的锁血，血量比例到不了 0。
     */
    private static final double ABYSSAL_SHROUD_FIRST_HEALTH_THRESHOLD = 0.9D;
    private static final double ABYSSAL_SHROUD_HEALTH_THRESHOLD_STEP = 0.1D;
    private static final float ABYSSAL_SHROUD_CAST_CHANCE = 0.5F;

    private static final List<WinefoxBossSpellAction> PHASE_ONE_SPELLS = List.of(
        WinefoxBossSpellAction.MAGIC_MISSILE,
        WinefoxBossSpellAction.MAGIC_ARROW,
        WinefoxBossSpellAction.SUMMON_SWORDS,
        WinefoxBossSpellAction.FIREBALL,
        WinefoxBossSpellAction.LIGHTNING_LANCE,
        WinefoxBossSpellAction.LIGHTNING_BOLT,
        WinefoxBossSpellAction.ARROW_VOLLEY,
        WinefoxBossSpellAction.EVASION,
        WinefoxBossSpellAction.ARCANE_SHACKLE,
        WinefoxBossSpellAction.HEAL,
        WinefoxBossSpellAction.MODIFIED_STARFALL,
        WinefoxBossSpellAction.MAGIC_SHOTGUN);
    private static final List<WinefoxBossSpellAction> PHASE_TWO_CLOSE_SPELLS = List.of(
        WinefoxBossSpellAction.ECHOING_STRIKES,
        WinefoxBossSpellAction.SHADOW_SLASH,
        WinefoxBossSpellAction.MODIFIED_TELEPORT,
        WinefoxBossSpellAction.STAR_SHADOW_STRIKE,
        WinefoxBossSpellAction.SHOCKWAVE);
    private static final List<WinefoxBossSpellAction> PHASE_TWO_FAR_SPELLS = List.of(
        WinefoxBossSpellAction.SHADOW_SLASH,
        WinefoxBossSpellAction.MODIFIED_TELEPORT,
        WinefoxBossSpellAction.SWORD_PRISON);

    /**
     * 各项「隔多久再考虑一次」的间隔。注意它们和 {@code spellCooldowns} 不是一回事： 这几个是**试过就重置**（够不够条件都算试过），冷却表那份是施法成功才重置。
     *
     * <p>每一项的重置点都分散在 {@code start()} / 对应的 tick 方法 / {@code onPhaseChanged()}
     * 里，原先各写一遍字面量，改一处漏一处。
     */
    private static final int SPELL_DECISION_INTERVAL = 20;
    private static final int ESCAPE_TELEPORT_CHECK_INTERVAL = 200;
    private static final int COUNTERSPELL_CHECK_INTERVAL = 20;
    private static final int SPEAR_CHECK_INTERVAL = 400;
    private static final int VOID_PHASE_CHECK_INTERVAL = 200;
    private static final int HEAL_CHECK_INTERVAL = 200;

    private final MagicalWinefoxBossEntity boss;
    private final EnumMap<WinefoxBossSpellAction, Integer> spellCooldowns =
        new EnumMap<>(WinefoxBossSpellAction.class);
    private int spellDecisionCooldown;
    private int meleeCooldown;
    private int closeRangeTicks;
    private int escapeTeleportCheckCooldown;
    private int counterspellCheckCooldown;
    private int spearCheckCooldown;
    private int voidPhaseCheckCooldown;
    private int healCheckCooldown;
    private int meleeComboRemaining;
    private int movementRefreshCooldown;
    private double orbitDirection = 1.0D;
    @Nullable
    private Vec3 lastPosition;
    private int stuckTicks;
    private double preferredHeight;
    private int phaseOneFloatRefreshCooldown;
    private boolean phaseOneFloatHigh;
    private double starfallHealthThreshold;
    private double abyssalShroudHealthThreshold = ABYSSAL_SHROUD_FIRST_HEALTH_THRESHOLD;
    private boolean phaseTwo;
    @Nullable
    private WinefoxBossSpellAction burstAction;
    private int burstShots;
    private int burstDelay;
    private int burstSpellLevel;

    WinefoxCombatGoal(MagicalWinefoxBossEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.boss.isDefeated()) {
            return false;
        }
        // 目标已经被打到 1 点血就不再是有效目标，这条与 targetSelector 的过滤同源。
        return this.boss.isViableTarget(this.boss.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.phaseTwo = this.boss.isPhaseTwo();
        this.spellDecisionCooldown = 0;
        this.escapeTeleportCheckCooldown = ESCAPE_TELEPORT_CHECK_INTERVAL;
        this.counterspellCheckCooldown = COUNTERSPELL_CHECK_INTERVAL;
        this.spearCheckCooldown = SPEAR_CHECK_INTERVAL;
        this.voidPhaseCheckCooldown = VOID_PHASE_CHECK_INTERVAL;
        this.healCheckCooldown = HEAL_CHECK_INTERVAL;
        this.spellCooldowns.clear();
        this.meleeCooldown = 0;
        this.meleeComboRemaining = 0;
        this.closeRangeTicks = 0;
        this.stuckTicks = 0;
        this.lastPosition = null;
        this.refreshMovementPattern();
        this.resetPhaseOneFloat();
        this.starfallHealthThreshold = 0.75D;
        // 只有她血还高过第一档（90%）时，才把「深渊庇佑」的档位拨回去重新数：
        // 目标是"一档一次"，战斗中途 goal 重启（换目标、卡住重选）不该把数过的档位再掷一遍。
        // 反过来，被自己的治疗抬回 90% 以上就算重新武装，与天降之星那份 75% 的写法同源。
        if (this.boss.getHealth() > this.boss.getMaxHealth() * ABYSSAL_SHROUD_FIRST_HEALTH_THRESHOLD) {
            this.abyssalShroudHealthThreshold = ABYSSAL_SHROUD_FIRST_HEALTH_THRESHOLD;
        }

        LivingEntity target = this.boss.getTarget();
        if (!this.phaseTwo && target != null) {
            this.boss.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE);
            if (this.castAction(target, WinefoxBossSpellAction.MAGIC_SHOTGUN,
                1 + this.boss.getRandom().nextInt(5))) {
                this.spellCooldowns.put(WinefoxBossSpellAction.MAGIC_SHOTGUN,
                    this.getSpellCooldown(WinefoxBossSpellAction.MAGIC_SHOTGUN));
            }
            this.spellDecisionCooldown = 8;
        }
    }

    @Override
    public void stop() {
        this.boss.getNavigation().stop();
        this.burstAction = null;
        this.burstShots = 0;
        // cancelCast() 会把 isCasting 同步成 false，客户端那边照样算出 END 相位、
        // 把该法术的收尾动画播出来。迁移前这里紧跟一句 stopCastAnimation() 压掉它
        // （"打断"不该有收尾），现在压不了了 —— 施法动画整条归铁魔法的同步数据管。
        // 这与普通女仆、以及铁魔法自己所有怪物的表现一致。
        this.boss.cancelCast();
        this.boss.cancelSpearThrow();
        this.closeRangeTicks = 0;
        // 她收手了，剑也该收回来：召唤物本身有 12000 tick 的存活时间，
        // 不主动解散的话会在她脱战之后继续追着人砍十分钟。
        this.boss.recallSummons();
    }

    @Override
    public void tick() {
        LivingEntity target = this.boss.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        this.tickCooldowns();
        this.boss.getLookControl().setLookAt(target, 45.0F, 45.0F);

        if (this.phaseTwo != this.boss.isPhaseTwo()) {
            this.onPhaseChanged();
        }
        if (this.boss.isBusyCombatAction()) {
            return;
        }

        double horizontalDistance = horizontalDistance(this.boss, target);
        if (this.phaseTwo) {
            this.tickPhaseTwo(target, horizontalDistance);
        } else {
            this.tickPhaseOne(target, horizontalDistance);
        }
    }

    private void tickPhaseOne(LivingEntity target, double horizontalDistance) {
        if (horizontalDistance < 3.0D) {
            ++this.closeRangeTicks;
        } else {
            this.closeRangeTicks = 0;
        }
        this.movePhaseOne(target, horizontalDistance);

        if (this.boss.isCasting()) {
            this.spellDecisionCooldown = 8;
            // 吟唱中：时长、收尾、CONTINUOUS 复发都归铁魔法管，这里不插手。
            // 必须挡在下面任何 castAction 之前——否则吟唱途中 cast() 返回 false，
            // 会被兜底逻辑当成"施法失败"而改去传送或射箭。
            return;
        }

        if (this.tickAbyssalShroud(target)) {
            return;
        }

        if (this.starfallHealthThreshold > 0.0D
            && this.boss.getHealth() / this.boss.getMaxHealth() <= this.starfallHealthThreshold) {
            double threshold = this.starfallHealthThreshold;
            this.starfallHealthThreshold = Math.max(0.0D, threshold - 0.10D);
            if (this.boss.getRandom().nextFloat() < (threshold >= 0.75D ? 1.0F : 0.5F)
                && this.isSpellReady(WinefoxBossSpellAction.MODIFIED_STARFALL)
                && this.castAction(target, WinefoxBossSpellAction.MODIFIED_STARFALL, 5)) {
                this.spellCooldowns.put(WinefoxBossSpellAction.MODIFIED_STARFALL,
                    this.getSpellCooldown(WinefoxBossSpellAction.MODIFIED_STARFALL));
                return;
            }
        }

        if (this.escapeTeleportCheckCooldown <= 0) {
            this.escapeTeleportCheckCooldown = ESCAPE_TELEPORT_CHECK_INTERVAL;
            if (horizontalDistance < 3.0D && this.boss.getRandom().nextFloat() < 0.25F
                && this.boss.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE)) {
                this.spellDecisionCooldown = 8;
                this.closeRangeTicks = 0;
                return;
            }
        }

        if (this.counterspellCheckCooldown <= 0) {
            this.counterspellCheckCooldown = COUNTERSPELL_CHECK_INTERVAL;
            if (WinefoxBossSpells.isCasting(target)
                && this.isSpellReady(WinefoxBossSpellAction.COUNTERSPELL)
                && this.boss.getRandom().nextFloat() < 0.25F) {
                if (this.castAction(target, WinefoxBossSpellAction.COUNTERSPELL, 1)) {
                    this.spellCooldowns.put(WinefoxBossSpellAction.COUNTERSPELL,
                        this.getSpellCooldown(WinefoxBossSpellAction.COUNTERSPELL));
                    this.spellDecisionCooldown = 8;
                    return;
                }
            }
        }

        if (this.tickBurst(target) || this.spellDecisionCooldown > 0) {
            return;
        }
        this.spellDecisionCooldown = 8;
        if (!this.boss.getSensing().hasLineOfSight(target)) {
            return;
        }

        WinefoxBossSpellAction action = this.chooseSpell(PHASE_ONE_SPELLS, target, horizontalDistance);
        if (action == null) {
            this.boss.performRangedAttack(target, 1.0F);
            return;
        }
        if (action == WinefoxBossSpellAction.MAGIC_MISSILE) {
            this.startBurst(action, 3 + this.boss.getRandom().nextInt(3), 5);
            this.spellCooldowns.put(action, this.getSpellCooldown(action));
            this.tickBurst(target);
            return;
        }
        int spellLevel = this.randomSpellLevel(action);
        if (this.castAction(target, action, spellLevel)) {
            this.spellCooldowns.put(action, this.getSpellCooldown(action));
        }
    }

    /**
     * 「深渊庇佑」：血量每跌 10% 掷一次 50%，中了就放。
     *
     * <p>档位在掷之前就先往下走一格：这一档不论中没中、法术起没起来，都只掷这一次。
     * 反过来说，一次掉血跨过好几档时，接下来几 tick 会连着各掷一次 —— 这是要的，
     * 那几档本来就各自欠她一次机会；而且这里只在没在吟唱时被调到，跨档连着放不会插队。
     *
     * <p>两个阶段共用这一处判定：10% 那一档落在二阶段里，不能只挂在一阶段的循环上。
     */
    private boolean tickAbyssalShroud(LivingEntity target) {
        if (this.abyssalShroudHealthThreshold <= 0.0D
            || this.boss.getHealth() / this.boss.getMaxHealth() > this.abyssalShroudHealthThreshold) {
            return false;
        }
        this.abyssalShroudHealthThreshold = Math.max(0.0D,
            this.abyssalShroudHealthThreshold - ABYSSAL_SHROUD_HEALTH_THRESHOLD_STEP);
        if (this.boss.getRandom().nextFloat() >= ABYSSAL_SHROUD_CAST_CHANCE) {
            return false;
        }
        return this.castAction(target, WinefoxBossSpellAction.ABYSSAL_SHROUD, 1);
    }

    private void tickPhaseTwo(LivingEntity target, double horizontalDistance) {
        this.movePhaseTwo(target, horizontalDistance);

        if (this.boss.isCasting()) {
            // 吟唱中：时长、收尾、CONTINUOUS 复发都归铁魔法管，这里不插手。
            return;
        }

        if (this.tickAbyssalShroud(target)) {
            return;
        }

        if (this.spearCheckCooldown <= 0) {
            this.spearCheckCooldown = SPEAR_CHECK_INTERVAL;
            if (this.boss.getRandom().nextFloat() < 0.5F) {
                this.boss.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE);
                this.boss.startSpearThrow(target);
                return;
            }
        }
        if (this.voidPhaseCheckCooldown <= 0) {
            this.voidPhaseCheckCooldown = VOID_PHASE_CHECK_INTERVAL;
            if (!WinefoxBossSpells.hasVoidPhase(this.boss)
                && this.boss.getRandom().nextFloat() < 0.5F
                && this.castAction(target, WinefoxBossSpellAction.VOID_PHASE, 1)) {
                this.spellCooldowns.put(WinefoxBossSpellAction.VOID_PHASE,
                        this.getSpellCooldown(WinefoxBossSpellAction.VOID_PHASE));
                return;
            }
        }
        if (this.healCheckCooldown <= 0) {
            this.healCheckCooldown = HEAL_CHECK_INTERVAL;
            if (this.boss.getHealth() < this.boss.getMaxHealth()
                && this.boss.getRandom().nextFloat() < 0.5F
                && this.castAction(target, WinefoxBossSpellAction.HEAL, 5)) {
                this.spellCooldowns.put(WinefoxBossSpellAction.HEAL,
                    this.getSpellCooldown(WinefoxBossSpellAction.HEAL));
                return;
            }
        }
        if (this.escapeTeleportCheckCooldown <= 0 && horizontalDistance > 8.0D) {
            this.escapeTeleportCheckCooldown = ESCAPE_TELEPORT_CHECK_INTERVAL;
            if (this.boss.teleportToward(target)) {
                return;
            }
        }

        if (this.meleeCooldown <= 0 && this.boss.distanceToSqr(target) <= 9.0D
            && this.boss.getSensing().hasLineOfSight(target)) {
            if (this.meleeComboRemaining == 0) {
                this.meleeComboRemaining = 3 + this.boss.getRandom().nextInt(2);
            }
            this.boss.swing(InteractionHand.MAIN_HAND);
            this.boss.doHurtTarget(target);
            --this.meleeComboRemaining;
            this.meleeCooldown = this.meleeComboRemaining > 0
                ? BASE_MELEE_COOLDOWN_TICKS : this.boss.animationAction().durationTicks() + SPELL_DECISION_INTERVAL;
            if (this.meleeComboRemaining == 0) {
                this.spellDecisionCooldown = this.boss.animationAction().durationTicks();
            }
            return;
        } else if (this.meleeComboRemaining > 0 && this.boss.distanceToSqr(target) <= 9.0D) {
            return;
        }

        if (this.tickBurst(target) || this.spellDecisionCooldown > 0) {
            return;
        }
        this.spellDecisionCooldown = SPELL_DECISION_INTERVAL;
        if (!this.boss.getSensing().hasLineOfSight(target)) {
            return;
        }

        WinefoxBossSpellAction action = this.choosePhaseTwoSpell(target, horizontalDistance);
        if (action == null) {
            return;
        }
        if (action == WinefoxBossSpellAction.MAGIC_SHOTGUN) {
            this.startBurst(action, 1 + this.boss.getRandom().nextInt(2),
                1 + this.boss.getRandom().nextInt(5));
            this.spellCooldowns.put(action, this.getSpellCooldown(action));
            this.tickBurst(target);
            return;
        }
        int spellLevel = this.randomSpellLevel(action);
        if (this.castAction(target, action, spellLevel)) {
            this.spellCooldowns.put(action, this.getSpellCooldown(action));
        }
    }

    private void movePhaseOne(LivingEntity target, double horizontalDistance) {
        if (--this.movementRefreshCooldown <= 0) {
            this.refreshMovementPattern();
        }
        Vec3 away = horizontalDirection(target.position(), this.boss.position());
        Vec3 tangent = new Vec3(-away.z, 0.0D, away.x).scale(this.orbitDirection);
        Vec3 movementDirection;
        double speedModifier;
        boolean retreating = horizontalDistance < 3.0D && this.closeRangeTicks >= 80;

        if (retreating) {
            movementDirection = away;
            speedModifier = 2.0D;
        } else if (horizontalDistance > 15.0D) {
            movementDirection = away.scale(-1.0D);
            speedModifier = 1.5D;
        } else {
            double radialCorrection = Mth.clamp((horizontalDistance - 7.5D) * 0.2D, -0.8D, 0.8D);
            movementDirection = tangent.add(away.scale(-radialCorrection)).normalize();
            speedModifier = 0.7D;
        }

        double desiredY = this.phaseOneFlightY(target.getY());
        Vec3 desired = this.boss.position().add(movementDirection.scale(3.0D));
        desired = new Vec3(desired.x, desiredY, desired.z);
        this.moveTowardClearPosition(desired, speedModifier, retreating ? 0.0D : 4.0D);
    }

    private void movePhaseTwo(LivingEntity target, double horizontalDistance) {
        // 二阶段把驻留高度锚到目标脚下，而不是沿用 COMBAT_ALTITUDE_TOLERANCE 那套"离得近就
        // 保持当前高度"。一阶段她是悬在目标上方 0.5~2.5 格飞行的，那点温和的容忍带在这里是
        // 个陷阱：转阶段那一刻要是正好悬在最高档，当前高度已经超出容忍带，返回的就是"保持当前
        // 高度"，于是高度被永久冻在上方两格多 —— 三维距离永远进不了近战窗口，她一刀都砍不到人。
        // 所以水平收进站定距离之后一律压到目标高度；还在远处水平飞过来时，容忍带只用来
        // 忽略普通跳跃与台阶，免得每 tick 追着目标的垂直速度抖动。
        double desiredY = horizontalDistance > PHASE_TWO_STANDOFF_DISTANCE
                          ? targetFlightY(target.getY(), target.getY())
                          : target.getY();
        // 站定要同时满足水平与垂直：水平进了 2 格、高度也收进近战余量，才算真的站到位。
        if (horizontalDistance <= PHASE_TWO_STANDOFF_DISTANCE * 1.5D
            && horizontalDistance >= PHASE_TWO_STANDOFF_DISTANCE * 0.5D
            && Math.abs(target.getY() - this.boss.getY()) <= PHASE_TWO_MELEE_VERTICAL_TOLERANCE) {
            this.holdPosition();
            return;
        }
        Vec3 away = horizontalDirection(target.position(), this.boss.position());
        // 站定点取在目标背面 PHASE_TWO_STANDOFF_DISTANCE 格处，而不是目标本身那一格。
        // 当前位置距离为零（同一格，比如刚传送落进去）时方向是常量 (1,0,0)，
        // 站定点自然就落在她当前所在的一侧，不会把她从目标身上穿过去。
        Vec3 desired = new Vec3(
            target.getX() + away.x * PHASE_TWO_STANDOFF_DISTANCE,
            desiredY,
            target.getZ() + away.z * PHASE_TWO_STANDOFF_DISTANCE);
        double speedModifier = horizontalDistance > PHASE_TWO_STANDOFF_DISTANCE
                               ? 1.5D
                               : horizontalDistance < PHASE_TWO_STANDOFF_DISTANCE * 0.5D ? 0.35D : 0.7D;
        this.moveTowardClearPosition(desired, speedModifier, 1.5D);
    }

    /**
     * 二阶段远距离接近时的高度命令：当前高度还在容忍带里就原地保持，否则收到目标高度。
     *
     * <p>只在 {@code horizontalDistance > PHASE_TWO_STANDOFF_DISTANCE} 的接近段用得到 ——
     * 站定段的垂直余量是另一个常量（见 {@link #PHASE_TWO_MELEE_VERTICAL_TOLERANCE}）。
     */
    private double targetFlightY(double targetY, double requestedY) {
        double currentY = this.boss.getY();
        return Math.abs(targetY - currentY) <= COMBAT_ALTITUDE_TOLERANCE ? currentY : requestedY;
    }

    /**
     * 停在原地，并把已经算好的高度挂住。
     *
     * <p>不复用 {@code moveTowardClearPosition(action, 0.0)}：那条路会把"没挪窝"记进
     * {@link #trackProgress}，站定连段二十 tick 之后就会被判成卡死、白白弹一下
     * {@link #breakDeadlock} —— 她这会儿是有意不动的。
     */
    private void holdPosition() {
        this.boss.getNavigation().stop();
        this.boss.setFlightDestination(
            this.boss.position().add(0.0D, this.boss.flightTargetY() - this.boss.getY(), 0.0D), 0.0D);
        this.stuckTicks = 0;
        this.lastPosition = this.boss.position();
    }

    /**
     * 一阶段保持缓慢的上下浮动。每次只改变 1～2 格目标高度，
     * 并把目标高度限制在目标脚下半格以上，避免贴地飞行。
     */
    private double phaseOneFlightY(double targetY) {
        if (--this.phaseOneFloatRefreshCooldown <= 0) {
            this.phaseOneFloatRefreshCooldown = PHASE_ONE_FLOAT_MIN_INTERVAL
                + this.boss.getRandom().nextInt(
                    PHASE_ONE_FLOAT_MAX_INTERVAL - PHASE_ONE_FLOAT_MIN_INTERVAL + 1);
            this.phaseOneFloatHigh = !this.phaseOneFloatHigh;
            this.preferredHeight = this.phaseOneFloatHigh
                ? PHASE_ONE_MIN_HOVER_HEIGHT + this.randomPhaseOneFloatDistance()
                : PHASE_ONE_MIN_HOVER_HEIGHT;
        }
        return targetY + this.preferredHeight;
    }

    /**
     * 朝目标点走，撞上东西就往上抬着找一条空路。
     *
     * <p>抬升幅度由调用方给（贴身缠斗 1.5 格、放风筝 4 格）。抬满了还是不通，
     * 就交给 {@link #breakDeadlock} 处理。
     */
    private void moveTowardClearPosition(Vec3 desired, double speedModifier, double maxLift) {
        Vec3 origin = this.boss.position();
        AABB destinationBox = this.boss.getBoundingBox().move(desired.subtract(origin));
        boolean blocked = this.boss.horizontalCollision
            || !this.boss.level().noCollision(this.boss, destinationBox);
        if (blocked) {
            double liftStep = maxLift <= 1.5D ? 0.5D : 1.0D;
            for (double lift = liftStep; lift <= maxLift; lift += liftStep) {
                Vec3 lifted = desired.add(0.0D, lift, 0.0D);
                AABB liftedBox = this.boss.getBoundingBox().move(lifted.subtract(origin));
                if (this.boss.level().noCollision(this.boss, liftedBox)) {
                    desired = lifted;
                    blocked = false;
                    break;
                }
            }
        }
        this.boss.getNavigation().stop();
        this.boss.setFlightDestination(desired, speedModifier);
        this.trackProgress(blocked);
    }

    /**
     * 盯住"想走却没动"的情况，连续若干 tick 就强行脱困。
     *
     * <p>{@code moveTowardClearPosition} 的抬升只试**目标点**那一列，
     * 她自己被卡在一个凹角里时那一列可能是通的，于是每 tick 都算出一个走不到的目标， 位移始终为零 —— 从外面看就是贴着墙原地抖。
     *
     * <p>判据用实际位移而不是碰撞标志：撞墙但仍在蹭着走不算卡死，
     * 真正的问题是**一直没挪窝**。
     */
    private void trackProgress(boolean blockedDestination) {
        Vec3 position = this.boss.position();
        boolean moved = this.lastPosition == null
            || position.distanceToSqr(this.lastPosition) > STUCK_MOVE_EPSILON_SQR;
        this.lastPosition = position;

        if (moved && !blockedDestination) {
            this.stuckTicks = 0;
            return;
        }
        if (++this.stuckTicks >= STUCK_TICKS_BEFORE_HOP) {
            this.stuckTicks = 0;
            this.breakDeadlock();
        }
    }

    /**
     * 脱困：往上蹿一段，同时朝一个随机水平方向甩出去。
     *
     * <p>她是飞行单位，所以"跳"就是直接给一个向上的速度 —— 不需要
     * {@code JumpControl}（那个只对贴地单位有意义，而她一进战斗就 {@code setNoGravity(true)}，原版跳跃逻辑根本不会触发）。
     *
     * <p>随机方向是必要的：如果每次都朝同一侧脱困，两堵墙夹角里会来回弹。
     */
    private void breakDeadlock() {
        double angle = this.boss.getRandom().nextDouble() * Mth.TWO_PI;
        Vec3 escape = new Vec3(Math.cos(angle) * HOP_HORIZONTAL_SPEED,
            HOP_VERTICAL_SPEED,
            Math.sin(angle) * HOP_HORIZONTAL_SPEED);
        this.boss.setDeltaMovement(this.boss.getDeltaMovement().add(escape));
        this.boss.hasImpulse = true;
        this.boss.getNavigation().stop();
        // 换一套绕圈参数，免得脱困之后又照着原来那条卡死的路线走回去。
        this.refreshMovementPattern();
    }

    @Nullable
    private WinefoxBossSpellAction choosePhaseTwoSpell(LivingEntity target, double horizontalDistance) {
        List<WinefoxBossSpellAction> rangePool = horizontalDistance <= 3.0D
                                                 ? PHASE_TWO_CLOSE_SPELLS
                                                 : PHASE_TWO_FAR_SPELLS;
        List<WinefoxBossSpellAction> pool = new ArrayList<>(rangePool.size() + 1);
        pool.add(WinefoxBossSpellAction.MAGIC_SHOTGUN);
        pool.addAll(rangePool);
        return this.chooseSpell(pool, target, horizontalDistance);
    }

    @Nullable
    private WinefoxBossSpellAction chooseSpell(List<WinefoxBossSpellAction> pool,
                                               LivingEntity target, double horizontalDistance) {
        List<WinefoxBossSpellAction> eligible = new ArrayList<>();
        for (WinefoxBossSpellAction action : pool) {
            if (!WinefoxBossSpells.isSpellAvailable(action)) {
                continue;
            }
            if (!this.isSpellReady(action)) {
                continue;
            }
            if (action == WinefoxBossSpellAction.HEAL
                && this.boss.getHealth() >= this.boss.getMaxHealth()) {
                continue;
            }
            if (action == WinefoxBossSpellAction.SWORD_PRISON && horizontalDistance < 3.0D) {
                continue;
            }
            eligible.add(action);
        }
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(this.boss.getRandom().nextInt(eligible.size()));
    }

    private boolean tickBurst(LivingEntity target) {
        if (this.burstAction == null || this.burstShots <= 0) {
            return false;
        }
        if (this.burstDelay > 0) {
            --this.burstDelay;
            return true;
        }
        if (!this.castAction(target, this.burstAction, this.burstSpellLevel)) {
            return true;
        }
        --this.burstShots;
        this.burstDelay = this.phaseTwo ? 6 : 8;
        if (this.burstShots <= 0) {
            this.burstAction = null;
        }
        return true;
    }

    private void startBurst(WinefoxBossSpellAction action, int shots, int spellLevel) {
        this.burstAction = action;
        this.burstShots = shots;
        this.burstDelay = 0;
        this.burstSpellLevel = spellLevel;
    }

    /**
     * 发起一次施法，失败时退化成传送 / 普通远程攻击。
     *
     * <p>起手与收尾动画都不在这儿：客户端从铁魔法的 {@code SyncedSpellData} 自己算相位，
     * 由 {@code ISSCastingAnimationProvider} 播（第 6 步之前是实体这边另开一条同步字段自己播）。 冷却统一在"发起成功"时记，而不是原来那样瞬发的记在发起、长吟唱的记在结束。
     */
    private boolean castAction(LivingEntity target, WinefoxBossSpellAction action, int spellLevel) {
        boolean cast = WinefoxBossSpells.cast(this.boss, target, action, spellLevel);
        if (!cast) {
            if (action == WinefoxBossSpellAction.MODIFIED_TELEPORT) {
                cast = this.boss.teleportAwayFrom(target, 8.0D);
            } else if (action != WinefoxBossSpellAction.HEAL
                && action != WinefoxBossSpellAction.COUNTERSPELL
                && action != WinefoxBossSpellAction.VOID_PHASE
                && action != WinefoxBossSpellAction.ECHOING_STRIKES
                && action != WinefoxBossSpellAction.ABYSSAL_SHROUD
                && action != WinefoxBossSpellAction.SHOCKWAVE) {
                this.boss.performRangedAttack(target, 1.0F);
                return true;
            }
        }
        return cast;
    }

    private int randomSpellLevel(WinefoxBossSpellAction action) {
        return switch (action) {
            case ABYSSAL_SHROUD, COUNTERSPELL, VOID_PHASE, EVASION -> 1;
            case MAGIC_SHOTGUN -> 1 + this.boss.getRandom().nextInt(5);
            case SUMMON_SWORDS, MODIFIED_TELEPORT, ARROW_VOLLEY, ARCANE_SHACKLE -> 4;
            default -> 5;
        };
    }

    /**
     * 一阶段的施法冷却压到基础值的 {@value #PHASE_ONE_COOLDOWN_SCALE} 倍，二阶段
     * {@value #PHASE_TWO_COOLDOWN_SCALE} 倍。
     *
     * <p>一阶段更密是有意的：那时候她只有法杖，靠出手频率撑压力；
     * 二阶段换长剑近身，节奏改由近战和位移撑，法术反而要留出间隙。
     *
     * <p>这里原先还各挂了一张按法术写死的 fallback 表，用于
     * {@code getCooldownTicks} 取不到法术时兜底。但那张表永远读不到 ——
     * 法术表是穷尽的、取不到直接抛，于是那二十来行看着像手感基线、
     * 改了却毫无效果。删掉了。
     */
    private int getSpellCooldown(WinefoxBossSpellAction action) {
        double scale = this.phaseTwo ? PHASE_TWO_COOLDOWN_SCALE : PHASE_ONE_COOLDOWN_SCALE;
        return Math.max(1, Mth.ceil(WinefoxBossSpells.getCooldownTicks(action, scale)));
    }

    private boolean isSpellReady(WinefoxBossSpellAction action) {
        return !this.spellCooldowns.containsKey(action);
    }

    private void tickCooldowns() {
        this.spellCooldowns.replaceAll((action, ticks) -> ticks - 1);
        this.spellCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
        if (this.spellDecisionCooldown > 0) {
            --this.spellDecisionCooldown;
        }
        if (this.meleeCooldown > 0) {
            --this.meleeCooldown;
        }
        if (this.escapeTeleportCheckCooldown > 0) {
            --this.escapeTeleportCheckCooldown;
        }
        if (this.counterspellCheckCooldown > 0) {
            --this.counterspellCheckCooldown;
        }
        if (this.spearCheckCooldown > 0) {
            --this.spearCheckCooldown;
        }
        if (this.voidPhaseCheckCooldown > 0) {
            --this.voidPhaseCheckCooldown;
        }
        if (this.healCheckCooldown > 0) {
            --this.healCheckCooldown;
        }
    }

    /**
     * 阶段变了（哪个方向都算），把战斗状态重新起一遍。
     *
     * <p>原先只处理"进二阶段"这一个方向，因为阶段是单程的。她能被治疗回血退形之后，
     * 这里必须跟着 {@code boss.isPhaseTwo()} 走 —— 否则退回一阶段后 {@code this.phaseTwo} 还是 true，tick() 会一直走 {@code tickPhaseTwo}： 拿着法杖放二阶段的近战法术，且每 tick 都判定为"阶段不一致"反复重置冷却。
     */
    private void onPhaseChanged() {
        this.phaseTwo = this.boss.isPhaseTwo();
        this.burstAction = null;
        this.burstShots = 0;
        this.closeRangeTicks = 0;
        this.spellDecisionCooldown = SPELL_DECISION_INTERVAL;
        this.meleeCooldown = 0;
        this.meleeComboRemaining = 0;
        this.escapeTeleportCheckCooldown = 0;
        this.spearCheckCooldown = SPEAR_CHECK_INTERVAL;
        this.voidPhaseCheckCooldown = VOID_PHASE_CHECK_INTERVAL;
        this.healCheckCooldown = HEAL_CHECK_INTERVAL;
        this.resetPhaseOneFloat();
        this.refreshMovementPattern();
    }

    private void resetPhaseOneFloat() {
        this.phaseOneFloatHigh = this.boss.getRandom().nextBoolean();
        this.preferredHeight = this.phaseOneFloatHigh
            ? PHASE_ONE_MIN_HOVER_HEIGHT + this.randomPhaseOneFloatDistance()
            : PHASE_ONE_MIN_HOVER_HEIGHT;
        this.phaseOneFloatRefreshCooldown = PHASE_ONE_FLOAT_MIN_INTERVAL
            + this.boss.getRandom().nextInt(
                PHASE_ONE_FLOAT_MAX_INTERVAL - PHASE_ONE_FLOAT_MIN_INTERVAL + 1);
    }

    private double randomPhaseOneFloatDistance() {
        return PHASE_ONE_FLOAT_MIN_DISTANCE + this.boss.getRandom().nextDouble()
            * (PHASE_ONE_FLOAT_MAX_DISTANCE - PHASE_ONE_FLOAT_MIN_DISTANCE);
    }

    private void refreshMovementPattern() {
        this.movementRefreshCooldown = 40 + this.boss.getRandom().nextInt(41);
        this.orbitDirection = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;
    }

    private static double horizontalDistance(Entity first, Entity second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from).multiply(1.0D, 0.0D, 1.0D);
        return direction.lengthSqr() < 1.0E-4D
               ? new Vec3(1.0D, 0.0D, 0.0D)
               : direction.normalize();
    }
}
