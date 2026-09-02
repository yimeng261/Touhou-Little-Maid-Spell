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

    private static final List<WinefoxBossSpellAction> PHASE_ONE_SPELLS = List.of(
        WinefoxBossSpellAction.MAGIC_MISSILE,
        WinefoxBossSpellAction.MAGIC_ARROW,
        WinefoxBossSpellAction.SUMMON_SWORDS,
        WinefoxBossSpellAction.FIREBALL,
        WinefoxBossSpellAction.LIGHTNING_LANCE,
        WinefoxBossSpellAction.HEAL,
        WinefoxBossSpellAction.MODIFIED_STARFALL,
        WinefoxBossSpellAction.MAGIC_SHOTGUN);
    private static final List<WinefoxBossSpellAction> PHASE_TWO_CLOSE_SPELLS = List.of(
        WinefoxBossSpellAction.ECHOING_STRIKES,
        WinefoxBossSpellAction.SHADOW_SLASH,
        WinefoxBossSpellAction.MODIFIED_TELEPORT,
        WinefoxBossSpellAction.HEAL,
        WinefoxBossSpellAction.FLAMING_STRIKE,
        WinefoxBossSpellAction.DIVINE_SMITE);
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
    private static final int ESCAPE_TELEPORT_CHECK_INTERVAL = 80;
    private static final int MODIFIED_TELEPORT_CHECK_INTERVAL = 120;
    private static final int COUNTERSPELL_CHECK_INTERVAL = 20;
    private static final int SWORD_PRISON_CHECK_INTERVAL = 400;
    private static final int VOID_PHASE_CHECK_INTERVAL = 400;

    private final MagicalWinefoxBossEntity boss;
    private final EnumMap<WinefoxBossSpellAction, Integer> spellCooldowns =
        new EnumMap<>(WinefoxBossSpellAction.class);
    private int spellDecisionCooldown;
    private int meleeCooldown;
    private int closeRangeTicks;
    private int escapeTeleportCheckCooldown;
    private int modifiedTeleportCheckCooldown;
    private int counterspellCheckCooldown;
    private int swordPrisonCheckCooldown;
    private int voidPhaseCheckCooldown;
    private int movementRefreshCooldown;
    private double orbitDirection = 1.0D;
    @Nullable
    private Vec3 lastPosition;
    private int stuckTicks;
    private double preferredHeight;
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
    public void start() {
        this.phaseTwo = this.boss.isPhaseTwo();
        this.spellDecisionCooldown = 0;
        this.escapeTeleportCheckCooldown = ESCAPE_TELEPORT_CHECK_INTERVAL;
        this.modifiedTeleportCheckCooldown = MODIFIED_TELEPORT_CHECK_INTERVAL;
        this.counterspellCheckCooldown = COUNTERSPELL_CHECK_INTERVAL;
        this.swordPrisonCheckCooldown = SWORD_PRISON_CHECK_INTERVAL;
        this.voidPhaseCheckCooldown = VOID_PHASE_CHECK_INTERVAL;
        this.refreshMovementPattern();

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
        // 这与普通女仆、以及铁魔法自己所有怪物的表现一致，见 §6.2 偏差四。
        this.boss.cancelCast();
        this.boss.cancelSwordRing();
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
            // 吟唱中：时长、收尾、CONTINUOUS 复发都归铁魔法管，这里不插手。
            // 必须挡在下面任何 castAction 之前——否则吟唱途中 cast() 返回 false，
            // 会被兜底逻辑当成"施法失败"而改去传送或射箭。
            return;
        }

        boolean shouldTeleport = false;
        if (this.modifiedTeleportCheckCooldown <= 0) {
            this.modifiedTeleportCheckCooldown = MODIFIED_TELEPORT_CHECK_INTERVAL;
            shouldTeleport = this.boss.getRandom().nextFloat() < 0.2F;
        }
        if (this.escapeTeleportCheckCooldown <= 0 && horizontalDistance < 5.0D) {
            this.escapeTeleportCheckCooldown = ESCAPE_TELEPORT_CHECK_INTERVAL;
            boolean closeRangeTeleport = this.boss.getRandom().nextFloat() < 0.25F;
            shouldTeleport = shouldTeleport || closeRangeTeleport;
        }
        if (shouldTeleport
            && this.isSpellReady(WinefoxBossSpellAction.MODIFIED_TELEPORT)
            && this.castAction(target, WinefoxBossSpellAction.MODIFIED_TELEPORT, 4)) {
            this.spellCooldowns.put(WinefoxBossSpellAction.MODIFIED_TELEPORT,
                this.getSpellCooldown(WinefoxBossSpellAction.MODIFIED_TELEPORT));
            this.spellDecisionCooldown = 8;
            this.closeRangeTicks = 0;
            return;
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

    private void tickPhaseTwo(LivingEntity target, double horizontalDistance) {
        this.movePhaseTwo(target, horizontalDistance);

        if (this.boss.isCasting()) {
            // 吟唱中：时长、收尾、CONTINUOUS 复发都归铁魔法管，这里不插手。
            return;
        }

        // 原先这里是"投枪动作"：一个自己计时的 64t 动作，末尾偷偷放一发剑牢的弹体。
        // 现在它就是剑牢法术本身，起手动画 iss:spear_throw 由法术指定，
        // 时长/收尾/打断一律归铁魔法管，与其余法术同一条路径。
        // 只有"剑什么时候落"是她自己的事：动画 2.1s 才把枪甩出去，
        // 所以 onCast 把剑交给 scheduleSwordRing 延后到那一帧。
        if (this.swordPrisonCheckCooldown <= 0) {
            this.swordPrisonCheckCooldown = SWORD_PRISON_CHECK_INTERVAL;
            if (this.boss.getRandom().nextFloat() < 0.5F) {
                this.boss.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE);
                if (this.castAction(target, WinefoxBossSpellAction.SWORD_PRISON, 1)) {
                    this.spellCooldowns.put(WinefoxBossSpellAction.SWORD_PRISON,
                        this.getSpellCooldown(WinefoxBossSpellAction.SWORD_PRISON));
                    return;
                }
            }
        }
        if (this.voidPhaseCheckCooldown <= 0) {
            this.voidPhaseCheckCooldown = VOID_PHASE_CHECK_INTERVAL;
            if (!WinefoxBossSpells.hasVoidPhase(this.boss)
                && this.boss.getRandom().nextFloat() < 0.5F
                && this.castAction(target, WinefoxBossSpellAction.VOID_PHASE, 1)) {
                this.spellCooldowns.put(WinefoxBossSpellAction.VOID_PHASE, 400);
            }
        }

        if (this.meleeCooldown <= 0 && this.boss.distanceToSqr(target) <= 9.0D) {
            this.boss.swing(InteractionHand.MAIN_HAND);
            this.boss.doHurtTarget(target);
            this.meleeCooldown = 12;
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

        double desiredY = retreating ? this.boss.getY() : target.getY() + this.preferredHeight;
        Vec3 desired = this.boss.position().add(movementDirection.scale(3.0D));
        desired = new Vec3(desired.x, desiredY, desired.z);
        this.moveTowardClearPosition(desired, speedModifier, retreating ? 0.0D : 4.0D);
    }

    private void movePhaseTwo(LivingEntity target, double horizontalDistance) {
        Vec3 desired = new Vec3(target.getX(), target.getY(), target.getZ());
        double speedModifier = horizontalDistance <= 8.0D ? 0.7D : 1.5D;
        this.moveTowardClearPosition(desired, speedModifier, 1.5D);
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
        this.boss.getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, speedModifier);
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
            if (!this.isSpellReady(action)) {
                continue;
            }
            if (action == WinefoxBossSpellAction.HEAL
                && this.boss.getHealth() > this.boss.getMaxHealth() * 0.8F) {
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
        this.castAction(target, this.burstAction, this.burstSpellLevel);
        --this.burstShots;
        this.burstDelay = this.burstAction == WinefoxBossSpellAction.MAGIC_MISSILE ? 3 : 6;
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
                && action != WinefoxBossSpellAction.ECHOING_STRIKES) {
                this.boss.performRangedAttack(target, 1.0F);
                return true;
            }
        }
        return cast;
    }

    private int randomSpellLevel(WinefoxBossSpellAction action) {
        return switch (action) {
            case COUNTERSPELL, VOID_PHASE -> 1;
            case MAGIC_SHOTGUN -> 1 + this.boss.getRandom().nextInt(5);
            case SUMMON_SWORDS, MODIFIED_TELEPORT -> 4;
            default -> 5;
        };
    }

    private int getSpellCooldown(WinefoxBossSpellAction action) {
        int fallbackTicks;
        if (!this.phaseTwo) {
            fallbackTicks = switch (action) {
                case MAGIC_MISSILE -> 40;
                case MAGIC_ARROW -> 24;
                case SUMMON_SWORDS -> 600;
                case FIREBALL -> 48;
                case LIGHTNING_LANCE -> 60;
                case HEAL -> 120;
                case MODIFIED_STARFALL -> 120;
                case MAGIC_SHOTGUN -> 32;
                case COUNTERSPELL -> 40;
                default -> 80;
            };
            return this.scaleByOmen(WinefoxBossSpells.getCooldownTicks(action, 0.2D, fallbackTicks));
        }
        fallbackTicks = switch (action) {
            case MAGIC_SHOTGUN -> 40;
            case ECHOING_STRIKES, SHADOW_SLASH -> 150;
            case MODIFIED_TELEPORT, DIVINE_SMITE -> 100;
            case HEAL -> 300;
            case FLAMING_STRIKE -> 160;
            default -> 120;
        };
        return this.scaleByOmen(WinefoxBossSpells.getCooldownTicks(action, 0.5D, fallbackTicks));
    }

    /**
     * 按挑战者带的不祥之兆等级压缩冷却，也就是流程图 T2 里的「施法频率变快」。
     *
     * <p>放在这一个出口上：上面两张 switch 表是手感基线，难度是另一个维度，
     * 混进表里以后调任何一边都要重新对另一边。普通挑战时系数是 1，等于没这回事。
     */
    private int scaleByOmen(int cooldownTicks) {
        return Math.max(1, Mth.ceil(cooldownTicks * this.boss.spellCooldownScale()));
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
        if (this.modifiedTeleportCheckCooldown > 0) {
            --this.modifiedTeleportCheckCooldown;
        }
        if (this.counterspellCheckCooldown > 0) {
            --this.counterspellCheckCooldown;
        }
        if (this.swordPrisonCheckCooldown > 0) {
            --this.swordPrisonCheckCooldown;
        }
        if (this.voidPhaseCheckCooldown > 0) {
            --this.voidPhaseCheckCooldown;
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
        this.boss.cancelCast();
        this.closeRangeTicks = 0;
        this.spellDecisionCooldown = SPELL_DECISION_INTERVAL;
        this.meleeCooldown = 0;
        this.swordPrisonCheckCooldown = SWORD_PRISON_CHECK_INTERVAL;
        this.voidPhaseCheckCooldown = VOID_PHASE_CHECK_INTERVAL;
        this.refreshMovementPattern();
    }

    private void refreshMovementPattern() {
        this.movementRefreshCooldown = 40 + this.boss.getRandom().nextInt(41);
        this.orbitDirection = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;
        this.preferredHeight = this.boss.getRandom().nextDouble() * 3.0D;
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
