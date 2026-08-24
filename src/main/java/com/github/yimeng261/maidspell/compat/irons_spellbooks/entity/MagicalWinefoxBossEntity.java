package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.WinefoxAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.WinefoxAnimations;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.WinefoxCastAnimation;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.WinefoxBossSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class MagicalWinefoxBossEntity extends AbstractSpellCastingMob implements Enemy, GeoEntity {
    private static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_SERIAL =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> CAST_ANIMATION =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> PHASE_TWO =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRANSITIONING =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * 战败演出已经开始。
     *
     * <p>必须是同步字段，不能拿 {@code deathTime} 当标志：那是纯服务端字段，
     * 客户端只有在收到实体事件 3（{@code LivingEntity.die()} 发的）时才会跟着动。
     * 我们从不调 {@code die()}，于是客户端的 {@code deathTime} 永远是 0 ——
     * 战败动画一帧都不会播，玩家看到的就是她原地凭空消失。
     */
    private static final EntityDataAccessor<Boolean> DEFEATED =
            SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);

    /** 连段窗口是 AI 手感参数，动画文件里没有对应物，所以留在这儿。 */
    private static final int SWORD_COMBO_RESET_TICKS = 40;

    // 下面这几个时长以前是手写的字面量，与动画文件各写一份；现在一律从 WinefoxAction 推导，
    // 由 WinefoxActionDataTest 与动画文件对账。
    private static final int PHASE_TRANSITION_TICKS = WinefoxAction.PHASE_TRANSITION.durationTicks();
    private static final int PHASE_TRANSITION_KNOCKBACK_TICK =
            eventTick(WinefoxAction.PHASE_TRANSITION, WinefoxAction.EventKind.KNOCKBACK);
    private static final int PHASE_TRANSITION_WEAPON_SWAP_TICK =
            eventTick(WinefoxAction.PHASE_TRANSITION, WinefoxAction.EventKind.WEAPON_SWAP);

    /**
     * 掉到半血进二阶段，回到六成血退回一阶段。
     *
     * <p>两条线不重合是有意的：贴着同一个 0.5 会让血量在阈值上下抖动时来回转阶段
     * （她自带治疗法术，这不是假设），一次 120t 的转场就够卡死整场战斗。
     * 中间这 10% 是迟滞带。
     */
    private static final float PHASE_TWO_HEALTH_FRACTION = 0.5F;
    private static final float PHASE_ONE_HEALTH_FRACTION = 0.6F;

    /**
     * 这是一场不杀人的表演赛：她把人打到只剩 1 点，自己也只掉到剩 1 点。
     *
     * <p>两边都用同一个下限，读起来是一条规则而不是两条巧合。
     */
    private static final float SURVIVAL_HEALTH_FLOOR = 1.0F;

    /** 身体转向目标的最大角速度。太大就是瞬间贴脸，太小绕圈时会追不上。 */
    private static final float BODY_TURN_DEGREES_PER_TICK = 15.0F;
    private static final double COMBAT_TELEPORT_DISTANCE = 15.0D;
    private static final double TRANSITION_KNOCKBACK_RADIUS = 5.0D;
    private static final double TRANSITION_KNOCKBACK_STRENGTH = 4.0D;

    private static final RawAnimation PHASE_ONE_IDLE = RawAnimation.begin().thenLoop("phase_one_idle");
    private static final RawAnimation PHASE_TWO_IDLE = RawAnimation.begin().thenLoop("phase_two_idle");
    private static final RawAnimation STAFF_FORM = RawAnimation.begin().thenLoop("staff_form");
    private static final RawAnimation SWORD_FORM = RawAnimation.begin().thenLoop("sword_form");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
    private static final RawAnimation JUMP = RawAnimation.begin().thenLoop("jump");
    private static final RawAnimation DEFEAT = WinefoxAnimations.of(WinefoxAction.DEFEAT);
    private static final RawAnimation AMBIENT_PARTS = RawAnimation.begin().thenLoop("ambient_parts");
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("blink");
    private static final RawAnimation TAIL_IDLE = RawAnimation.begin().thenLoop("tail_idle");
    private static final RawAnimation TAIL_WALK = RawAnimation.begin().thenLoop("tail_walk");
    private static final RawAnimation TAIL_RUN = RawAnimation.begin().thenLoop("tail_run");
    private static final RawAnimation TAIL_JUMP = RawAnimation.begin().thenLoop("tail_jump");
    private static final RawAnimation MAGIC_RINGS = RawAnimation.begin().thenLoop("magic_rings");
    /**
     * 一阶段飞行时把背后那圈法阵张开。
     *
     * <p>{@code ambient_parts} 把 {@code ysmGlowMagicCircle15~18} 常年缩成 0
     * （它是无条件控制器），而 {@code fly} 只转不放大 —— 于是飞起来法阵是隐形的，
     * 与法杖当初那个 bug 同一个形状。这条只负责把 scale 放回 1，转由 {@code magic_rings} 管。
     */
    private static final RawAnimation FLIGHT_CIRCLE =
            RawAnimation.begin().thenLoop("phase_one_flight_circle");
    private static final RawAnimation HOLD_SWORD = RawAnimation.begin().thenPlayAndHold("hold_mainhand:sword");
    private static final RawAnimation HOLD_BOW = RawAnimation.begin().thenLoop("hold_mainhand:bow");
    // 动作动画与施法动画不再手写 thenPlay / thenLoop，一律经 WinefoxAnimations 从枚举推导。

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.createBossBarName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private int nextSwordVariant;
    private int nextStaffVariant;
    private int lastSwordSwingTick = Integer.MIN_VALUE;
    private int phaseTransitionTicks;
    private int lastHandledActionSerial = Integer.MIN_VALUE;
    private boolean phaseTransitionKnockbackReleased;
    private boolean phaseTransitionWeaponSwapped;
    /** 本次转场结束后该处于二阶段还是一阶段。进二阶段为 true，退形为 false。 */
    private boolean phaseTransitionTarget;
    private boolean actionAnimationPlaying;

    public MagicalWinefoxBossEntity(EntityType<? extends MagicalWinefoxBossEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 80;
        this.moveControl = new FlyingMoveControl(this, 20, true);
        // 一阶段的装备在这里就位，而不是等 finalizeSpawn：后者只有自然生成 / 刷怪蛋 / summon
        // 这几条路会调，别的生成方式（结构、其他模组代码）不会，酒狐就会空着手站在那儿。
        // 读档时 Mob.readAdditionalSaveData 会用 NBT 里的 ArmorItems / HandItems 覆盖回去，不冲突。
        //
        // 只在服务端装。客户端也塞一份的话，会造出一份服务端从未确认过的装备：
        // ServerEntity.sendPairingData 首次同步装备时**只发非空槽位**，所以头部一旦被清空
        // （/item replace ... armor.head with air），客户端下次重新开始追踪这只实体
        // ——重登、走远再回来、区块重载——构造器塞回去的法帽就再也没有人来纠正，
        // 于是服务端头上是空的、客户端却一直画着帽子。装备一律以服务端为准。
        if (!level.isClientSide) {
            this.equipStarMajoGear();
        }
    }

    @Override
    protected float getFlyingSpeed() {
        return 0.04F;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isNoGravity() && this.getTarget() != null && this.getTarget().isAlive()) {
            // Combat flight is horizontal; the vanilla flying controller's binary Y input can overshoot badly.
            Vec3 delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            super.travel(new Vec3(travelVector.x, 0.0D, travelVector.z));
            delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            return;
        }
        super.travel(travelVector);
    }

    /** 从枚举声明的中途事件里取出指定种类的 tick，取不到就是枚举写漏了。 */
    private static int eventTick(WinefoxAction action, WinefoxAction.EventKind kind) {
        for (WinefoxAction.Event event : action.events()) {
            if (event.kind() == kind) {
                return event.tick();
            }
        }
        throw new IllegalStateException(action + " declares no " + kind + " event");
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FLYING_SPEED, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
        WinefoxBossSpells.addAttributes(builder);
        return builder;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        // 构造器里已经装备过了，这里只是兜底：Mob.finalizeSpawn 可能按难度重置装备槽。
        this.equipStarMajoGear();
        return result;
    }

    /**
     * 佩戴星之魔女法帽与当前形态对应的武器。这些装备提供外观与属性，但不会掉落。
     */
    private void equipStarMajoGear() {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IronsSpellbooksCompatItems.STAR_WITCH_HAT.get()));
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        this.equipPhaseWeapon(this.isPhaseTwo());
    }

    /**
     * 一阶段持法杖，二阶段换成长剑。
     *
     * <p>形态是参数而不是 {@code isPhaseTwo()}：转阶段是在动画中途换手的，
     * 那一刻 {@code PHASE_TWO} 还没翻（它要等动画放完）。
     *
     * <p>主手物品就是切换外观的唯一开关：装备层按它决定渲染哪把武器，
     * {@code weapon_form} / {@code staff_hold} / {@code sword_hold} 三个控制器也一律看它。
     */
    private void equipPhaseWeapon(boolean phaseTwo) {
        Item weapon = phaseTwo
                ? IronsSpellbooksCompatItems.STAR_SHADOW_LONGSWORD.get()
                : IronsSpellbooksCompatItems.STAR_SHADOW_STAFF.get();
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** 主手拿的是星影长剑。主手物品会同步给客户端，动画可以直接判。 */
    private boolean isHoldingLongsword() {
        return this.getMainHandItem().getItem() instanceof StarShadowLongswordItem;
    }

    /** 主手拿的是星影法杖。 */
    private boolean isHoldingStaff() {
        return this.getMainHandItem().getItem() instanceof StarShadowStaffItem;
    }

    /**
     * 该摆持杖/持弓姿势：法杖、弓、弩，以及铁魔法的法杖类。
     *
     * <p>{@code hold_mainhand:bow} 这个名字是 YSM 遗留的（见 D2），它其实就是
     * "双手在身前端着一根长杆"的姿势 —— 法杖和弓都合适。
     *
     * <p>判 {@code StaffItem} 用的是铁魔法的基类，所以别的模组加的铁魔法法杖也算数；
     * 星影法杖自己就继承它，不必单独列。
     */
    private boolean usesStaffHoldPose() {
        Item item = this.getMainHandItem().getItem();
        return item instanceof StaffItem
                || item instanceof BowItem
                || item instanceof CrossbowItem;
    }

    /**
     * 该摆持剑姿势：任何 {@link SwordItem}。
     *
     * <p>星影长剑、铁魔法的 {@code MagicSwordItem}（经 {@code ExtendedSwordItem}）
     * 都继承原版 {@code SwordItem}，一条就够，不必逐个列举。
     */
    private boolean usesSwordHoldPose() {
        return this.getMainHandItem().getItem() instanceof SwordItem;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    /**
     * 有目标时身体正对目标，而不是正对飞行方向。
     *
     * <p>她的战斗走位大量绕圈与侧向平移（{@code movePhaseOne} 的切向分量），
     * 而 {@code Mob} 默认让身体跟着 {@code MoveControl} 的行进方向转 —— 于是绕圈时
     * 她是侧着甚至背对着人飞的，看上去像在逃跑而不是在压迫。
     *
     * <p>{@code yBodyRot} 是**身体**朝向，头由 {@code LookControl} 另外管、
     * 并在模型里以 ±55° 的偏移叠加（见 {@code MagicalWinefoxBossModel}），
     * 所以这里只钉身体就够，头会自然跟上。
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || this.isDefeated()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.yBodyRot = Mth.approachDegrees(this.yBodyRot, wanted, BODY_TURN_DEGREES_PER_TICK);
        this.setYRot(this.yBodyRot);
        this.yHeadRot = Mth.approachDegrees(this.yHeadRot, wanted, BODY_TURN_DEGREES_PER_TICK);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DEFEATED, false);
        this.entityData.define(ACTION, WinefoxAction.NONE.id());
        this.entityData.define(ACTION_SERIAL, 0);
        this.entityData.define(CAST_ANIMATION, "");
        this.entityData.define(PHASE_TWO, false);
        this.entityData.define(TRANSITIONING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new WinefoxCombatGoal(this));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // 血量 <= 1 的玩家不再被选为目标：她已经把人打服了，不必再追着打。
        // 这条同时管住 HurtByTargetGoal —— 它也走 canAttack 这层过滤。
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class,
                10, true, false, this::isViableTarget));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    /**
     * 铁魔法 {@code AbstractSpellCastingMob} 用这两个判断持械姿势。
     *
     * <p>都改成看主手实物而不是阶段：转阶段是在动画中途换手的，
     * 那 65t 里阶段标志还没翻，但手里已经是新武器了 —— 看阶段会姿势对不上。
     * 与 {@code weapon_form} / 两条 hold 控制器同源。
     */
    public boolean isHoldingSword() {
        return this.isHoldingLongsword();
    }

    public boolean isHoldingBow() {
        return this.isHoldingStaff();
    }

    public boolean isPhaseTwo() {
        return this.entityData.get(PHASE_TWO);
    }

    public boolean isTransitioning() {
        return this.entityData.get(TRANSITIONING);
    }

    /**
     * 这个目标还值不值得打。
     *
     * <p>把人打到 1 点血就算赢了（见 {@link NonLethalGuard}），再追着打只会变成
     * 一个永远打不死人的骚扰循环。所以濒死的玩家直接从目标池里排除。
     */
    private boolean isViableTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return candidate.getHealth() > SURVIVAL_HEALTH_FLOOR;
    }

    /** 当前目标已经濒死就松手，交给 {@code targetSelector} 另选一个。 */
    private void releaseSubduedTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && !this.isViableTarget(target)) {
            this.setTarget(null);
        }
    }

    private void beginAction(WinefoxAction action) {
        this.entityData.set(ACTION, action.id());
        this.entityData.set(ACTION_SERIAL, this.entityData.get(ACTION_SERIAL) + 1);
    }

    /** 客户端与服务端都从这里读当前动作，整数编号只活在同步值里。 */
    private WinefoxAction currentAction() {
        return WinefoxAction.byId(this.entityData.get(ACTION));
    }

    private void clearAction() {
        this.entityData.set(ACTION, WinefoxAction.NONE.id());
    }

    private WinefoxAction nextGroundSwordAction() {
        if (this.lastSwordSwingTick == Integer.MIN_VALUE
                || this.tickCount - this.lastSwordSwingTick > SWORD_COMBO_RESET_TICKS) {
            this.nextSwordVariant = 0;
        }
        this.lastSwordSwingTick = this.tickCount;
        return switch (this.nextSwordVariant++ & 3) {
            case 1 -> WinefoxAction.SWORD_ATTACK_2;
            case 2 -> WinefoxAction.SWORD_ATTACK_3;
            case 3 -> WinefoxAction.SWORD_ATTACK_4;
            default -> WinefoxAction.SWORD_ATTACK_1;
        };
    }

    private WinefoxAction nextStaffAction() {
        return this.nextStaffVariant++ % 2 == 0
                ? WinefoxAction.STAFF_ATTACK_1
                : WinefoxAction.STAFF_ATTACK_2;
    }

    @Override
    public void swing(InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            if (this.isTransitioning()) {
                return;
            }
            if (this.isPhaseTwo()) {
                this.beginAction(this.nextGroundSwordAction());
            } else {
                this.beginAction(this.nextStaffAction());
            }
        }
        super.swing(hand);
    }

    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, distanceFactor);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontalDistance * 0.2, dz, 1.6F, 4.0F);
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        this.level().addFreshEntity(arrow);
        this.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * 正在施放的法术动作，供 {@link #castComplete()} 播收尾动画；{@code null} 表示没在施法。
     *
     * <p>铁魔法只记 {@code castingSpell}（一个 {@code AbstractSpell}），倒推不回酒狐这边的
     * {@link WinefoxBossSpellAction}，所以自己留一份。
     */
    @Nullable
    private WinefoxBossSpellAction castingSpellAction;

    /** 由 {@code WinefoxBossSpells.cast} 在真的起了一次吟唱之后调用。 */
    void onSpellCastStarted(WinefoxBossSpellAction action) {
        this.castingSpellAction = action;
        this.triggerCastStartAnimation(action);
    }

    /**
     * 吟唱结束（正常走完或被 {@code cancelCast()} 打断）时，铁魔法都会调到这里。
     *
     * <p>收尾动画就挂在这儿——原先是战斗 Goal 自己数着一个 {@code pendingCastTicks} 到点了再触发
     * （那套已经删掉），现在时机由铁魔法说了算。若该法术的收尾动画是 {@code "#stop"} 或压根没有，
     * {@code triggerCastAnimation} 内部会退化成停掉当前吟唱动画。
     */
    @Override
    public void castComplete() {
        WinefoxBossSpellAction finished = this.castingSpellAction;
        this.castingSpellAction = null;
        super.castComplete();
        if (finished != null && !this.level().isClientSide) {
            this.triggerCastFinishAnimation(finished);
        }
    }

    private void triggerCastStartAnimation(WinefoxBossSpellAction action) {
        this.triggerCastAnimation(action, false);
    }

    private void triggerCastFinishAnimation(WinefoxBossSpellAction action) {
        this.triggerCastAnimation(action, true);
    }

    private void triggerCastAnimation(WinefoxBossSpellAction action, boolean finish) {
        if (this.isTransitioning()) {
            return;
        }
        String animationPath = WinefoxBossSpells.getCastAnimation(action, finish);
        if (WinefoxBossSpells.STOP_CAST_ANIMATION.equals(animationPath)) {
            this.stopCastAnimation();
        } else if (animationPath != null && WinefoxCastAnimation.byKey(animationPath) != null) {
            this.entityData.set(CAST_ANIMATION, animationPath);
            this.beginAction(WinefoxAction.CAST);
        }
    }

    private void stopCastAnimation() {
        if (this.currentAction() == WinefoxAction.CAST) {
            this.clearAction();
        }
        this.entityData.set(CAST_ANIMATION, "");
    }

    private boolean isBusyCombatAction() {
        return this.isTransitioning();
    }

    private boolean teleportAwayFrom(LivingEntity target, double distance) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Vec3 away = this.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-4D) {
            away = target.getLookAngle().multiply(-1.0D, 0.0D, -1.0D);
        }
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();

        Vec3 origin = this.position();
        int[] verticalOffsets = {0, 1, 2, -1, 3, -2, 4};
        double[] distanceScales = {1.0D, 0.85D, 0.7D, 0.55D, 0.4D};
        for (double distanceScale : distanceScales) {
            Vec3 horizontal = away.scale(distance * distanceScale);
            for (int verticalOffset : verticalOffsets) {
                Vec3 candidate = origin.add(horizontal).add(0.0D, verticalOffset, 0.0D);
                BlockPos candidatePos = BlockPos.containing(candidate);
                AABB destinationBox = this.getBoundingBox().move(candidate.subtract(origin));
                if (!serverLevel.getWorldBorder().isWithinBounds(candidatePos)
                        || !serverLevel.getFluidState(candidatePos).isEmpty()
                        || !serverLevel.noCollision(this, destinationBox)) {
                    continue;
                }
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(0.5D), this.getZ(),
                        32, 0.35D, 0.7D, 0.35D, 0.2D);
                this.teleportTo(candidate.x, candidate.y, candidate.z);
                this.setDeltaMovement(Vec3.ZERO);
                this.resetFallDistance();
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(0.5D), this.getZ(),
                        32, 0.35D, 0.7D, 0.35D, 0.2D);
                this.playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.0F);
                return true;
            }
        }
        return false;
    }

    /**
     * 战败之后整套 AI 停摆：goal、目标选择、导航、朝向与移动控制器一个都不跑。
     *
     * <p>{@code LivingEntity.aiStep} 里那句 {@code if (isImmobile()) ... else if (isEffectiveAi())
     * serverAiStep()} 就是原版给的开关：判真则整个 {@code serverAiStep} 都不进，
     * 同时把 {@code xxa/zza} 清零 —— 顺手解决了"控制器停了、上一帧的移动输入还留着
     * 继续把她往前推"这个尾巴。
     *
     * <p>只能停在这一层。光让 {@code customServerAiStep} 早退是不够的：那个回调挂在
     * {@code Mob.serverAiStep} 的中段，它**前面**的 {@code goalSelector} 与
     * **后面**的 {@code lookControl} / {@code bodyRotationControl} 照样会走，
     * 于是她躺在地上还会转头看人、跟着扭身子。而 {@code serverAiStep} 本身是 {@code final}，
     * 覆写不了。
     *
     * <p>{@code travel} 不在这条分支里，所以重力照旧 —— {@link #beginDefeat} 关掉了
     * {@code NoGravity}，她还是会落到地上。
     */
    @Override
    protected boolean isImmobile() {
        return this.isDefeated() || super.isImmobile();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.prioritizePlayerTarget();

        this.releaseSubduedTarget();
        this.tickPhaseThresholds();
        if (this.isTransitioning()) {
            this.tickPhaseTransition();
        }

        LivingEntity target = this.getTarget();
        boolean combatFlight = (target != null && target.isAlive()) || this.isBusyCombatAction();
        this.setNoGravity(combatFlight);
        if (combatFlight) {
            this.resetFallDistance();
        }
    }

    /**
     * 血量越过阈值就发起一次转场。两个方向都有。
     *
     * <p>她会给自己加血（{@code HEAL} 法术，还可能被别人治疗），所以这不是单程票。
     * 上下两条阈值之间留了 10% 的迟滞带，见 {@link #PHASE_TWO_HEALTH_FRACTION}。
     */
    private void tickPhaseThresholds() {
        if (this.isTransitioning()) {
            return;
        }
        float healthFraction = this.getHealth() / this.getMaxHealth();
        if (!this.isPhaseTwo() && healthFraction <= PHASE_TWO_HEALTH_FRACTION) {
            this.startPhaseTransition(true);
        } else if (this.isPhaseTwo() && healthFraction >= PHASE_ONE_HEALTH_FRACTION) {
            this.startPhaseTransition(false);
        }
    }

    /**
     * @param toPhaseTwo 转场结束后是否处于二阶段；{@code false} 就是被治疗回血后的退形
     */
    private void startPhaseTransition(boolean toPhaseTwo) {
        // 转阶段要独占动作层，先把在飞的吟唱掐掉，免得它在转阶段动画里跑完还触发收尾动画。
        this.cancelCast();
        this.phaseTransitionTicks = PHASE_TRANSITION_TICKS;
        this.phaseTransitionKnockbackReleased = false;
        this.phaseTransitionWeaponSwapped = false;
        this.phaseTransitionTarget = toPhaseTwo;
        this.entityData.set(TRANSITIONING, true);
        this.beginAction(toPhaseTwo ? WinefoxAction.PHASE_TRANSITION : WinefoxAction.PHASE_REVERT);
    }

    private void tickPhaseTransition() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        int elapsedTicks = PHASE_TRANSITION_TICKS - this.phaseTransitionTicks;
        if (!this.phaseTransitionKnockbackReleased
                && elapsedTicks >= PHASE_TRANSITION_KNOCKBACK_TICK) {
            this.phaseTransitionKnockbackReleased = true;
            this.releasePhaseTransitionKnockback();
        }
        // 法杖变长剑就在这一刻。动画在 2.625s~3.0s 把武器缩到 0，
        // 换手落在这段看不见的窗口里，再张回来时已经是成形的长剑。
        if (!this.phaseTransitionWeaponSwapped
                && elapsedTicks >= PHASE_TRANSITION_WEAPON_SWAP_TICK) {
            this.phaseTransitionWeaponSwapped = true;
            this.equipPhaseWeapon(this.phaseTransitionTarget);
        }
        if (--this.phaseTransitionTicks > 0) {
            return;
        }

        this.entityData.set(TRANSITIONING, false);
        this.entityData.set(PHASE_TWO, this.phaseTransitionTarget);
        this.clearAction();
        this.nextSwordVariant = 0;
        if (this.phaseTransitionTarget) {
            // 虚空相位是二阶段的开场增益，退形时不给。
            WinefoxBossSpells.cast(this, this.getTarget(), WinefoxBossSpellAction.VOID_PHASE, 1);
        }
    }

    private void releasePhaseTransitionKnockback() {
        AABB area = this.getBoundingBox().inflate(TRANSITION_KNOCKBACK_RADIUS);
        for (Entity entity : this.level().getEntities(this, area,
                entity -> entity.isAlive() && !entity.isSpectator())) {
            Vec3 horizontal = entity.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            if (horizontal.lengthSqr() < 1.0E-4D) {
                double angle = this.random.nextDouble() * Mth.TWO_PI;
                horizontal = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            }
            horizontal = horizontal.normalize().scale(TRANSITION_KNOCKBACK_STRENGTH);
            entity.push(horizontal.x, 0.75D, horizontal.z);
        }
    }

    private void prioritizePlayerTarget() {
        if (this.tickCount % 10 != 0) {
            return;
        }
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget instanceof Player player && isAttackablePlayer(player)) {
            return;
        }
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        Player nearestPlayer = this.level().getEntitiesOfClass(Player.class,
                        this.getBoundingBox().inflate(range), MagicalWinefoxBossEntity::isAttackablePlayer)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (nearestPlayer != null) {
            this.setTarget(nearestPlayer);
        }
    }

    private static boolean isAttackablePlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return this.isTransitioning() || super.isInvulnerableTo(source);
    }

    /**
     * 她自己也不会真的死：血量最低留 1 点，到点转入战败演出。
     *
     * <p>兜血放在 {@link #actuallyHurt} 里，**不能**在这儿把伤害预先削到
     * {@code getHealth() - 1}。两条原因，都是实打实踩过的：
     *
     * <ul>
     *   <li>{@code LivingEntity.hurt} 在无敌帧内（{@code invulnerableTime > 10}）会拿这一击与
     *       {@code lastHurt} 相比，{@code amount <= lastHurt} 直接 {@code return false}。
     *       预削之后越接近 1 点血这一击越小，于是残血时反而**一点伤害都吃不进**——
     *       正是"血量剩 2 时怎么砍都不掉血"的成因。</li>
     *   <li>预削是在护甲结算**之前**，削出来的 1 点被护甲再砍一刀，落地永远差一截，
     *       血量只会渐近 1 而碰不到 1，战败演出也就永远不触发。</li>
     * </ul>
     *
     * <p>所以这里只管减伤倍率，让伤害照常走完整条结算链；
     * {@code actuallyHurt} 在扣血之后把地板兜住，再回到这儿判断要不要转战败。
     *
     * <p>战败之后不再拦：{@link #beginDefeat} 会给她挂 {@code INVULNERABLE},
     * 此时任何伤害都进不来，1 点血会一直保持到 {@code tickDeath} 把她移除。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // BYPASSES_INVULNERABILITY 是 /kill 与虚空伤害那一类，故意放行：
        // 战败之后她会一直躺在场上，不给这条口子就再也没有办法把她清掉。
        if (bypassesSurvival(source)) {
            return super.hurt(source, amount);
        }
        if (this.isDefeated()) {
            return false;
        }
        float adjustedAmount = amount;
        if (isMaidDamage(source)) {
            adjustedAmount *= 0.5F;
        }
        if (this.isPhaseTwo()) {
            adjustedAmount *= 0.5F;
        }

        boolean hurt = super.hurt(source, adjustedAmount);
        if (this.getHealth() <= SURVIVAL_HEALTH_FLOOR && !this.level().isClientSide) {
            this.beginDefeat();
        }
        return hurt;
    }

    /**
     * 血量的地板：护甲、抗性、吸收全部结算完之后，把血兜回 1 点。
     *
     * <p>{@code actuallyHurt} 是扣血的那一步，而 {@code LivingEntity.hurt} 是在它返回之后
     * 才查 {@code isDeadOrDying()} 决定要不要走死亡流程 —— 卡在这两步中间兜血，
     * 血量就从来没有到过 0，原版的死亡分支一次都不会进。
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (!bypassesSurvival(source) && this.getHealth() < SURVIVAL_HEALTH_FLOOR) {
            this.setHealth(SURVIVAL_HEALTH_FLOOR);
        }
    }

    /** 这一击是不是 {@code /kill} 一类的强制移除：那种不受 1 点血地板保护。 */
    private static boolean bypassesSurvival(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * 转入战败演出：播 {@code defeat}，1 点血保持到动画放完。
     *
     * <p>标志走同步字段 {@link #DEFEATED} 而不是 {@code deathTime}：后者是纯服务端的，
     * 客户端拿不到就不会切战败动画。{@code deathTime} 仍留作服务端这边的计时器。
     *
     * <p>{@code isDeadOrDying()} 判的是 {@code getHealth() <= 0}，我们的血永远是 1，
     * 那条判断在这儿恒为假。所有"她是不是已经败了"的地方一律问 {@link #isDefeated}。
     */
    private void beginDefeat() {
        if (this.isDefeated()) {
            return;
        }
        this.entityData.set(DEFEATED, true);
        // 她倒下了，场上的召唤物没有理由继续打。
        this.recallSummons();
        this.cancelCast();
        this.clearAction();
        this.entityData.set(TRANSITIONING, false);
        this.phaseTransitionTicks = 0;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(false);
        this.setInvulnerable(true);
        this.setTarget(null);
        // 打完了，血条收掉；她本人留在场上倒着。
        this.bossEvent.setVisible(false);
    }

    /** 战败演出已经开始（含已放完等待移除）。客户端也要能判，动画靠它切 {@code defeat}。 */
    public boolean isDefeated() {
        return this.entityData.get(DEFEATED);
    }

    /**
     * 收回她召唤出来的东西。
     *
     * <p>不能走 {@code PlayerRecasts.removeAll}：那条路最后会调到
     * {@code AbstractSpell.onRecastFinished(ServerPlayer, ...)}，而召唤系法术
     * （如 {@code SummonSwordsSpell}）在里面直接 {@code serverPlayer.serverLevel()} ——
     * 施法者是怪物时那个参数是 {@code null}，当场 NPE。
     *
     * <p>所以自己来：{@link SummonManager} 记着"谁召的谁"，按主人反查一遍解散掉，
     * 再把她的 recast 记账整个换成一份空的。
     *
     * <p>换掉记账这一步是必须的。{@code PlayerRecasts.tick} 只对真玩家走
     * （{@code serverPlayer != null} 才递减），怪物那份记录于是永不过期；
     * 而 {@code SummonSwordsSpell.onCast} 开头就查 {@code hasRecastForSpell}，
     * 有记录就整个跳过 —— 不清的话她这辈子只能召唤这一次。
     */
    private void recallSummons() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (UUID uuid : SummonManager.getSummons(this)) {
            Entity summon = serverLevel.getEntity(uuid);
            if (summon instanceof IMagicSummon magicSummon) {
                magicSummon.onUnSummon();
            } else if (summon != null) {
                summon.discard();
            }
        }
        MagicData magicData = this.getMagicData();
        if (magicData != null && magicData.getPlayerRecasts().hasRecastsActive()) {
            magicData.setPlayerRecasts(new PlayerRecasts());
        }
    }

    /**
     * 她的攻击不会真的打死人：把目标留在 1 点血。
     *
     * <p>盖住所有出伤口径 —— 近战、法术、弹体，只要伤害源头能追溯到她。
     * 法术伤害由铁魔法自己发，我们插不进它的计算，所以拦在**承伤方**这一侧。
     *
     * <p>挂 {@code LivingDamageEvent} 而不是 {@code LivingHurtEvent}：前者拿到的是
     * 护甲、抗性、吸收全部结算完、马上就要扣到血条上的那个数，
     * 后者是结算**之前**的原始伤害。按原始伤害去削，护甲会再砍一刀，
     * 玩家的血只会渐近 1 而永远碰不到 1 —— 那样 {@link #isViableTarget}
     * 就一直认为他还能打，她会追着一个永远打不服的人不放。
     *
     * <p>只保护玩家。小怪该死还是得死，否则召唤物永远清不掉。
     */
    @Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
    public static final class NonLethalGuard {

        private NonLethalGuard() {
        }

        @SubscribeEvent
        public static void onLivingDamage(LivingDamageEvent event) {
            if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
                return;
            }
            if (player.isCreative() || player.isSpectator()) {
                return;
            }
            if (!isWinefoxDamage(event.getSource())) {
                return;
            }
            float survivable = Math.max(0.0F, player.getHealth() - SURVIVAL_HEALTH_FLOOR);
            if (event.getAmount() >= survivable) {
                event.setAmount(survivable);
            }
        }

        /** 伤害是否出自酒狐：直接打的、她的弹体、或她发的法术。 */
        private static boolean isWinefoxDamage(DamageSource source) {
            if (source.getEntity() instanceof MagicalWinefoxBossEntity
                    || source.getDirectEntity() instanceof MagicalWinefoxBossEntity) {
                return true;
            }
            return source.getDirectEntity() instanceof Projectile projectile
                    && projectile.getOwner() instanceof MagicalWinefoxBossEntity;
        }
    }

    private static boolean isMaidDamage(DamageSource source) {
        Entity causingEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        if (causingEntity instanceof EntityMaid || directEntity instanceof EntityMaid) {
            return true;
        }
        return directEntity instanceof Projectile projectile && projectile.getOwner() instanceof EntityMaid;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WinefoxPhaseTwo", this.isPhaseTwo());
        tag.putBoolean("WinefoxTransitioning", this.isTransitioning());
        tag.putInt("WinefoxTransitionTicks", this.phaseTransitionTicks);
        tag.putBoolean("WinefoxTransitionToPhaseTwo", this.phaseTransitionTarget);
        tag.putBoolean("WinefoxDefeated", this.isDefeated());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(PHASE_TWO, tag.getBoolean("WinefoxPhaseTwo"));
        // 她战败之后是留在场上的，读档得接着躺着，不能爬起来重新开打。
        this.entityData.set(DEFEATED, tag.getBoolean("WinefoxDefeated"));
        if (this.isDefeated()) {
            this.setInvulnerable(true);
            this.bossEvent.setVisible(false);
            return;
        }
        this.phaseTransitionTicks = tag.getInt("WinefoxTransitionTicks");
        boolean transitioning = tag.getBoolean("WinefoxTransitioning") && this.phaseTransitionTicks > 0;
        this.entityData.set(TRANSITIONING, transitioning);
        this.phaseTransitionKnockbackReleased = transitioning
                && this.phaseTransitionTicks <= PHASE_TRANSITION_TICKS - PHASE_TRANSITION_KNOCKBACK_TICK;
        this.phaseTransitionWeaponSwapped = transitioning
                && this.phaseTransitionTicks <= PHASE_TRANSITION_TICKS - PHASE_TRANSITION_WEAPON_SWAP_TICK;
        // 老存档没这个键，读出 false 会把进二阶段的转场当成退形。
        // 缺键时按"与当前阶段相反"推：存档写的 PhaseTwo 是转场**开始前**的状态。
        this.phaseTransitionTarget = tag.contains("WinefoxTransitionToPhaseTwo")
                ? tag.getBoolean("WinefoxTransitionToPhaseTwo")
                : !this.isPhaseTwo();
        if (transitioning && !this.level().isClientSide) {
            this.beginAction(this.phaseTransitionTarget
                    ? WinefoxAction.PHASE_TRANSITION
                    : WinefoxAction.PHASE_REVERT);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * 以下两项是 {@code Monster} 提供、改继承 {@link AbstractSpellCastingMob} 后丢掉的，照原样补回。
     *
     * <p>另外五个音效重写（{@code getHurtSound} 等）**有意不补**，受击与死亡音会从
     * {@code HOSTILE_*} 退回原版 {@code GENERIC_*}。声道归类和播哪个音是两回事，
     * 所以 {@link #getSoundSource()} 仍然要回到 {@code HOSTILE}。
     */
    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    /** 血条标题为「万法酒狐——显示名」，显示名本身走实体的翻译键。 */
    private Component createBossBarName() {
        return Component.translatable(this.getType().getDescriptionId() + ".bossbar", this.getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.createBossBarName());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private PlayState mainAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        // 判 isDefeated() 而不是 isDeadOrDying()：她战败时血量停在 1，后者永远为假。
        if (this.isDefeated()) {
            return state.setAndContinue(DEFEAT);
        }
        if (this.hurtTime > 0) {
            return state.setAndContinue(this.isPhaseTwo() ? PHASE_TWO_IDLE : PHASE_ONE_IDLE);
        }
        if (!this.onGround()) {
            if (this.isNoGravity() && !state.isMoving()) {
                // 悬停但没有水平位移，仍然算待机。
                // 一锁定目标就 setNoGravity(true)，酒狐从此再没有 onGround() 为真的时候；
                // 少了这一条，刷出来的瞬间就卡在 fly 上，待机姿势（含持杖）永远播不到。
                return state.setAndContinue(this.isPhaseTwo() ? PHASE_TWO_IDLE : PHASE_ONE_IDLE);
            }
            return state.setAndContinue(this.isNoGravity() ? FLY : JUMP);
        }
        if (state.isMoving()) {
            return state.setAndContinue(this.getDeltaMovement().horizontalDistanceSqr() > 0.08 ? RUN : WALK);
        }
        return state.setAndContinue(this.isPhaseTwo() ? PHASE_TWO_IDLE : PHASE_ONE_IDLE);
    }

    /**
     * 自制武器的形变。
     *
     * <p>这两条动画写的是 {@code StarShadowSword} 子树里那些骨骼（{@code handle} 拉长、
     * {@code style1} 归零之类），只对自家那把武器有意义。主手拿着别的东西时必须 STOP ——
     * 否则它会去形变一把当前根本没渲染的剑，而那些骨骼正被装备层同步给玩家的剑用着（见 #2）。
     */
    private PlayState weaponFormAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isHoldingLongsword()) {
            return state.setAndContinue(SWORD_FORM);
        }
        if (this.isHoldingStaff()) {
            return state.setAndContinue(STAFF_FORM);
        }
        return PlayState.STOP;
    }

    /**
     * 一阶段飞行时展开背后的法阵，其余时候交回 {@code ambient_parts} 的 0。
     *
     * <p>二阶段不放：那时候她的排面是长剑与剑气特效，法阵是一阶段的招牌。
     */
    /**
     * 战败之后所有并行的循环动画一律停摆：不眨眼、不摆尾、法阵不转。
     *
     * <p>{@code defeat} 只画倒地那一套骨骼，眨眼与尾巴挂在**另外的控制器**上，
     * 不主动停的话她会一边躺着一边眨眼摇尾巴。
     *
     * <p>唯独 {@code ambient_parts} 不能停 —— 它名义上是"环境动画"，
     * 实际干的是把一堆特效网格（长枪、剑气、若干法阵）常年压成 {@code scale=0}。
     * 停掉它，那些网格会全部弹回原尺寸冒出来。它那两条耳朵的抽动改由
     * {@code defeat} 自己写死同名骨骼盖住 —— {@code main} 注册在它之后，压得住。
     */
    private PlayState blinkAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isDefeated()) {
            return PlayState.STOP;
        }
        return state.setAndContinue(BLINK);
    }

    private PlayState magicRingsAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isDefeated()) {
            return PlayState.STOP;
        }
        return state.setAndContinue(MAGIC_RINGS);
    }

    private PlayState flightCircleAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isPhaseTwo() || this.isDefeated() || this.onGround()) {
            return PlayState.STOP;
        }
        return state.setAndContinue(FLIGHT_CIRCLE);
    }

    private PlayState tailAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isDefeated()) {
            return PlayState.STOP;
        }
        if (!this.onGround()) {
            return state.setAndContinue(TAIL_JUMP);
        }
        if (state.isMoving()) {
            return state.setAndContinue(this.getDeltaMovement().horizontalDistanceSqr() > 0.08
                    ? TAIL_RUN : TAIL_WALK);
        }
        return state.setAndContinue(TAIL_IDLE);
    }

    /**
     * 持杖姿势。判的是主手物品**类别**而不是阶段，拿法杖、弓、弩都摆这个。
     *
     * <p>转阶段中途换手时它自然交棒给 sword_hold。转阶段期间不整段 STOP：
     * {@code phase_transition} 在 {@code action} 控制器上，注册顺序排在两个 hold 之后，
     * 握剑骨骼写成什么本来就是它说了算。
     */
    private PlayState staffHoldAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (!this.usesStaffHoldPose() || this.isDefeated() || this.hurtTime > 0
                || (this.onGround() && !state.isMoving())) {
            return PlayState.STOP;
        }
        return state.setAndContinue(HOLD_BOW);
    }

    /** 持剑姿势：任何剑类武器都摆这个，不限于星影长剑。 */
    private PlayState swordHoldAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (!this.usesSwordHoldPose() || this.isDefeated() || this.hurtTime > 0) {
            return PlayState.STOP;
        }
        return state.setAndContinue(HOLD_SWORD);
    }

    private PlayState actionAnimation(AnimationState<MagicalWinefoxBossEntity> state) {
        if (this.isDefeated()) {
            this.actionAnimationPlaying = false;
            return PlayState.STOP;
        }
        WinefoxAction action = this.currentAction();
        if (action == WinefoxAction.NONE) {
            this.lastHandledActionSerial = this.entityData.get(ACTION_SERIAL);
            this.actionAnimationPlaying = false;
            return PlayState.STOP;
        }
        int serial = this.entityData.get(ACTION_SERIAL);
        if (serial != this.lastHandledActionSerial) {
            state.getController().forceAnimationReset();
            this.lastHandledActionSerial = serial;
        } else if (state.getController().hasAnimationFinished()) {
            this.actionAnimationPlaying = false;
            return PlayState.STOP;
        }
        this.actionAnimationPlaying = true;
        if (action == WinefoxAction.CAST) {
            WinefoxCastAnimation cast = WinefoxCastAnimation.byKey(this.entityData.get(CAST_ANIMATION));
            if (cast == null) {
                this.actionAnimationPlaying = false;
                return PlayState.STOP;
            }
            return state.setAndContinue(WinefoxAnimations.of(cast));
        }
        RawAnimation animation = WinefoxAnimations.of(action);
        if (animation == null) {
            // NONE / CAST 上面已经拦掉，DEFEAT 走的是 main 控制器，剩下的都该有动画。
            animation = WinefoxAnimations.of(WinefoxAction.STAFF_ATTACK_1);
        }
        return state.setAndContinue(animation);
    }

    public boolean isActionAnimationPlaying() {
        return this.actionAnimationPlaying;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<MagicalWinefoxBossEntity> mainController =
                new AnimationController<>(this, "main", 2, this::mainAnimation)
                        .setSoundKeyframeHandler(this::handleSoundKeyframe);
        AnimationController<MagicalWinefoxBossEntity> actionController =
                new AnimationController<>(this, "action", 0, this::actionAnimation)
                        .setSoundKeyframeHandler(this::handleSoundKeyframe);
        // 注册顺序就是优先级：同一根骨骼的同一通道，后注册的控制器直接覆盖先注册的。
        // action 必须排在 weapon_form / staff_hold / sword_hold 之后：
        // 转阶段那段隐藏武器的 scale 才压得住两个 hold 写的 scale=1。别重排。
        controllers.add(
                new AnimationController<>(this, "ambient_parts", 0, state -> state.setAndContinue(AMBIENT_PARTS)),
                new AnimationController<>(this, "blink", 0, this::blinkAnimation),
                new AnimationController<>(this, "weapon_form", 0, this::weaponFormAnimation),
                mainController,
                new AnimationController<>(this, "tail", 2, this::tailAnimation),
                new AnimationController<>(this, "staff_hold", 2, this::staffHoldAnimation),
                new AnimationController<>(this, "sword_hold", 2, this::swordHoldAnimation),
                actionController,
                new AnimationController<>(this, "magic_rings", 0, this::magicRingsAnimation),
                // 排在 ambient_parts 之后才压得住它那几条 scale=0。
                new AnimationController<>(this, "flight_circle", 0, this::flightCircleAnimation));
    }

    /**
     * 战败之后她**留在场上**，不再走任何移除流程。
     *
     * <p>{@code defeat} 是 {@code HOLD_LAST_FRAME}，播完就定在倒地那一帧，
     * 于是她会一直躺在原地。原版的 {@code tickDeath} 本来就只在
     * {@code isDeadOrDying()} 时被调，而她的血永远是 1，那个回调根本不会触发 ——
     * 这里覆写成空是为了挡住别处（比如别的模组）主动调它把她计时移除。
     *
     * <p>{@code deathTime} 一并保持为 0：它没有同步给客户端，
     * 但 {@code GeoEntityRenderer.applyRotations} 会照着它把实体侧翻，
     * 万一哪天有人把它同步出去，非零值会让倒地姿势再被扭一次。
     */
    @Override
    protected void tickDeath() {
    }

    private void handleSoundKeyframe(SoundKeyframeEvent<MagicalWinefoxBossEntity> event) {
        if (!this.level().isClientSide) {
            return;
        }
        var sound = MaidSpellSounds.getWinefoxSound(event.getKeyframeData().getSound());
        if (sound != null) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE,
                    1.0F, 1.0F, false);
        }
    }

    private static final class WinefoxCombatGoal extends Goal {
        /** 连续这么多 tick 想走却没挪窝，就认定卡住了。 */
        private static final int STUCK_TICKS_BEFORE_HOP = 20;
        /** 一 tick 位移小于这个值就当没动（0.05 格）。 */
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
        @Nullable

        private WinefoxCombatGoal(MagicalWinefoxBossEntity boss) {
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
            this.escapeTeleportCheckCooldown = 80;
            this.modifiedTeleportCheckCooldown = 120;
            this.counterspellCheckCooldown = 20;
            this.swordPrisonCheckCooldown = 400;
            this.voidPhaseCheckCooldown = 400;
            this.refreshMovementPattern();

            LivingEntity target = this.boss.getTarget();
            if (!this.phaseTwo && target != null) {
                this.boss.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE);
                if (this.castAction(target, WinefoxBossSpellAction.MAGIC_SHOTGUN,
                        1 + this.boss.random.nextInt(5))) {
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
            // cancelCast() 内部会走到 castComplete()，那儿可能触发收尾动画；
            // 目标丢了属于"打断"而不是"施完"，所以随后再把动画停掉。
            this.boss.cancelCast();
            this.boss.stopCastAnimation();
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
                this.modifiedTeleportCheckCooldown = 120;
                shouldTeleport = this.boss.random.nextFloat() < 0.2F;
            }
            if (this.escapeTeleportCheckCooldown <= 0 && horizontalDistance < 5.0D) {
                this.escapeTeleportCheckCooldown = 80;
                boolean closeRangeTeleport = this.boss.random.nextFloat() < 0.25F;
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
                this.counterspellCheckCooldown = 20;
                if (WinefoxBossSpells.isCasting(target)
                        && this.isSpellReady(WinefoxBossSpellAction.COUNTERSPELL)
                        && this.boss.random.nextFloat() < 0.25F) {
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
                this.startBurst(action, 3 + this.boss.random.nextInt(3), 5);
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
            // 现在它就是剑牢法术本身，起手动画由 WinefoxBossSpells 指定成举枪，
            // 时长/收尾/打断一律归铁魔法管，与其余法术同一条路径。
            if (this.swordPrisonCheckCooldown <= 0) {
                this.swordPrisonCheckCooldown = 400;
                if (this.boss.random.nextFloat() < 0.5F) {
                    this.boss.teleportAwayFrom(target, COMBAT_TELEPORT_DISTANCE);
                    if (this.castAction(target, WinefoxBossSpellAction.SWORD_PRISON, 1)) {
                        this.spellCooldowns.put(WinefoxBossSpellAction.SWORD_PRISON,
                                this.getSpellCooldown(WinefoxBossSpellAction.SWORD_PRISON));
                        return;
                    }
                }
            }
            if (this.voidPhaseCheckCooldown <= 0) {
                this.voidPhaseCheckCooldown = 400;
                if (!WinefoxBossSpells.hasVoidPhase(this.boss)
                        && this.boss.random.nextFloat() < 0.5F
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
            this.spellDecisionCooldown = 20;
            if (!this.boss.getSensing().hasLineOfSight(target)) {
                return;
            }

            WinefoxBossSpellAction action = this.choosePhaseTwoSpell(target, horizontalDistance);
            if (action == null) {
                return;
            }
            if (action == WinefoxBossSpellAction.MAGIC_SHOTGUN) {
                this.startBurst(action, 1 + this.boss.random.nextInt(2),
                        1 + this.boss.random.nextInt(5));
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
         * 她自己被卡在一个凹角里时那一列可能是通的，于是每 tick 都算出一个走不到的目标，
         * 位移始终为零 —— 从外面看就是贴着墙原地抖。
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
         * {@code JumpControl}（那个只对贴地单位有意义，而她一进战斗就
         * {@code setNoGravity(true)}，原版跳跃逻辑根本不会触发）。
         *
         * <p>随机方向是必要的：如果每次都朝同一侧脱困，两堵墙夹角里会来回弹。
         */
        private void breakDeadlock() {
            double angle = this.boss.random.nextDouble() * Mth.TWO_PI;
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
            return eligible.get(this.boss.random.nextInt(eligible.size()));
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
         * <p>起手动画由 {@code WinefoxBossSpells.cast} 在确认吟唱起来之后触发，
         * 收尾动画由 {@link MagicalWinefoxBossEntity#castComplete()} 触发——两头都不在这儿了。
         * 冷却统一在"发起成功"时记，而不是原来那样瞬发的记在发起、长吟唱的记在结束。
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
                case MAGIC_SHOTGUN -> 1 + this.boss.random.nextInt(5);
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
                return WinefoxBossSpells.getCooldownTicks(action, 0.2D, fallbackTicks);
            }
            fallbackTicks = switch (action) {
                case MAGIC_SHOTGUN -> 40;
                case ECHOING_STRIKES, SHADOW_SLASH -> 150;
                case MODIFIED_TELEPORT, DIVINE_SMITE -> 100;
                case HEAL -> 300;
                case FLAMING_STRIKE -> 160;
                default -> 120;
            };
            return WinefoxBossSpells.getCooldownTicks(action, 0.5D, fallbackTicks);
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
         * 这里必须跟着 {@code boss.isPhaseTwo()} 走 —— 否则退回一阶段后
         * {@code this.phaseTwo} 还是 true，tick() 会一直走 {@code tickPhaseTwo}：
         * 拿着法杖放二阶段的近战法术，且每 tick 都判定为"阶段不一致"反复重置冷却。
         */
        private void onPhaseChanged() {
            this.phaseTwo = this.boss.isPhaseTwo();
            this.burstAction = null;
            this.burstShots = 0;
            this.boss.cancelCast();
            this.closeRangeTicks = 0;
            this.spellDecisionCooldown = 20;
            this.meleeCooldown = 0;
            this.swordPrisonCheckCooldown = 400;
            this.voidPhaseCheckCooldown = 400;
            this.refreshMovementPattern();
        }

        private void refreshMovementPattern() {
            this.movementRefreshCooldown = 40 + this.boss.random.nextInt(41);
            this.orbitDirection = this.boss.random.nextBoolean() ? 1.0D : -1.0D;
            this.preferredHeight = this.boss.random.nextDouble() * 3.0D;
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
}
