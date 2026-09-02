package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.SwordPrisonSpell;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.network.casting.SyncEntityDataPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
import net.minecraft.world.item.ItemStack;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.ITrueDamageRedirect;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MagicalWinefoxBossEntity extends AbstractSpellCastingMob
    implements Enemy, IMaid, CastingAnimateStateAccessor, ITrueDamageRedirect {
    private static final EntityDataAccessor<Integer> ACTION =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_SERIAL =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PHASE_TWO =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRANSITIONING =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * 战败演出已经开始。
     *
     * <p>必须是同步字段，不能拿 {@code deathTime} 当标志：那是纯服务端字段，
     * 客户端只有在收到实体事件 3（{@code LivingEntity.die()} 发的）时才会跟着动。
     * 我们从不调 {@code die()}，于是客户端的 {@code deathTime} 永远是 0 —— 战败动画一帧都不会播，玩家看到的就是她原地凭空消失。
     */
    private static final EntityDataAccessor<Boolean> DEFEATED =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 正坐在秋千上等人来邀战。
     *
     * <p>必须同步：坐姿动画整条由客户端的 {@code main} 通道驱动，
     * TLM 在 {@code AnimationRegister} 里把 {@code sit} 挂在
     * {@code maid.isMaidInSittingPose()} 上（优先级 1，压得住 walk / idle），
     * 所以只要 {@link #isMaidInSittingPose()} 报得出来，动画一行都不用自己写。
     */
    private static final EntityDataAccessor<Boolean> SEATED =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 上一场被判了「女仆代打」：不掉星云核心，也不解锁特殊交易。见 {@link #computeRestricted}。
     *
     * <p>做成同步字段是为了让客户端也能在交易界面之外给出提示；服务端这边它同样落 NBT。
     */
    private static final EntityDataAccessor<Boolean> RESTRICTED =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);

    /** 战败演出放完到回秋千坐下之间的间隔，流程图上写的是 3 秒。 */
    private static final int DEFEAT_RETURN_HOME_TICKS = 60;

    /** 场上连续这么久没有可打的目标就收场，见 {@link #tickBattleOver}。 */
    private static final int BATTLE_OVER_GRACE_TICKS = 100;

    /**
     * 连段窗口是 AI 手感参数，动画文件里没有对应物，所以留在这儿。
     */
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
     * （她自带治疗法术，这不是假设），一次 120t 的转场就够卡死整场战斗。 中间这 10% 是迟滞带。
     */
    private static final float PHASE_TWO_HEALTH_FRACTION = 0.5F;
    private static final float PHASE_ONE_HEALTH_FRACTION = 0.6F;

    /**
     * 这是一场不杀人的表演赛：她把人打到只剩 1 点，自己也只掉到剩 1 点。
     *
     * <p>两边都用同一个下限，读起来是一条规则而不是两条巧合。
     */
    private static final float SURVIVAL_HEALTH_FLOOR = 1.0F;

    /**
     * 投掷动画真正把枪甩出去的那一帧。
     *
     * <p>{@code iss:spear_throw} 自带一把枪：{@code weapon3}（挂在 {@code LeftHand} 下面，
     * 4 个 cube）在 0.6s 凭空出现在她左手里，2.0s 之前一直握着，
     * <b>2.1s 那一帧 scale 归 0、位置飞到 {@code [8,-24,-15]}</b> —— 那才是投出去的瞬间，
     * 法阵 {@code ysmGlowmofazhen14} 也在同一帧炸开。她右手的法杖/长剑全程都在， 这把投枪是动画自己召出来的，不是换装备换出来的。
     *
     * <p>2.1s × 20 = 42。剑要等到这一帧才落，否则就是「剑先出现，她过两秒才把枪甩出去」。
     */
    private static final int SWORD_RING_RELEASE_TICKS = 42;

    /**
     * 身体转向目标的最大角速度。太大就是瞬间贴脸，太小绕圈时会追不上。
     */
    private static final float BODY_TURN_DEGREES_PER_TICK = 15.0F;
    private static final double TRANSITION_KNOCKBACK_RADIUS = 5.0D;
    private static final double TRANSITION_KNOCKBACK_STRENGTH = 4.0D;

    /**
     * 内置模型包里那份模型的 id。
     *
     * <p>客户端渲染时 TLM 拿它去 {@code CustomPackLoader.MAID_MODELS} 查模型 / 贴图 / 动画，
     * 包由 {@code TouhouLittleMaidModelPackInstaller} 解压到 {@code gameDir/tlm_custom_pack}。
     * 包没装上时 TLM 会静默退回默认女仆模型，不会崩。
     */
    public static final String MODEL_ID = "touhou_little_maid_spell:sea_witch_winefox";

    private final ServerBossEvent bossEvent = new ServerBossEvent(
        this.createBossBarName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private int nextSwordVariant;
    private int nextStaffVariant;
    private int lastSwordSwingTick = Integer.MIN_VALUE;

    /**
     * 当前动作动画是第几 tick 起的（服务端计时，不同步）。
     * 只给 {@link #canStartNewComboAction()} 用 —— 服务端本来没有这个计时： {@code clearAction()} 只在转阶段结束和战败时调，近战动作起了就一直挂着。
     */
    private int actionStartTick;
    /**
     * &gt;0 表示投掷动画在跑、还没到甩出去那一帧；数到 0 剑才落。
     */
    private int swordRingDelayTicks;
    private int swordRingSpellLevel;
    @Nullable
    private LivingEntity swordRingTarget;
    private int phaseTransitionTicks;
    private boolean phaseTransitionKnockbackReleased;
    private boolean phaseTransitionWeaponSwapped;
    /** 秋千位。纯服务端，客户端只要知道"在不在坐"，不需要知道坐在哪。 */
    @Nullable
    private BlockPos homePos;
    /** 开场白的排队播报，见 {@link WinefoxDialogue}。 */
    private final WinefoxDialogue dialogue = new WinefoxDialogue();
    /** 本场累计吃到的伤害，以及其中出自女仆的部分。用来算 R1 的伤害占比。 */
    private float totalDamageTaken;
    private float maidDamageTaken;
    /** 本场有没有人对她用过真伤。R1 的另一个触发条件。 */
    private boolean trueDamageUsed;
    /** 战败演出结束后回秋千的倒计时，见 {@link #tickReturnHome}。 */
    private int returnHomeTicks;
    /** 连续多少 tick 没有可打的目标了，见 {@link #tickBattleOver}。 */
    private int noTargetTicks;
    /**
     * 本次转场结束后该处于二阶段还是一阶段。进二阶段为 true，退形为 false。
     */
    private boolean phaseTransitionTarget;

    /**
     * 上一帧的手持物快照，**必须是持久字段**。
     *
     * <p>TLM 的 {@code AnimationManager.predicateMainhandHold} 会往这个数组里写当前手持物，
     * 用来判断“手里的东西换了没有”。{@link IMaid} 的默认实现每次返回一个新数组，
     * 写进去当场就丢 —— 于是每一帧都判定成“刚换了武器”，持握动画被 {@code empty} 打断， 表现为持握姿势疯狂闪烁。
     */
    private final ItemStack[] handItemsForAnimation = {ItemStack.EMPTY, ItemStack.EMPTY};

    /**
     * 挂在 TLM {@code magic_casting} 通道上的那份状态，只在客户端读写。
     *
     * <p>放在实体上而不是 provider 里，是因为 provider 是全局单例、一份要伺候所有酒狐。
     */
    private final WinefoxCastingAnimateState castingAnimateState = new WinefoxCastingAnimateState();

    /**
     * 施法动画那一份状态，由 {@code ISSCastingAnimationProvider} 读，只在客户端读写。
     *
     * <p>与上面那份 {@link #castingAnimateState} 不是一回事：这一份喂的是施法，
     * 上面那份喂的是近战 / 转阶段 / 战败。
     * 普通女仆的这一份由 {@code MaidEntityAnimateStateMixin} 挂上去，酒狐不是 {@code EntityMaid}，
     * 只能自己实现 {@link CastingAnimateStateAccessor}。
     */
    private final MagicCastingAnimateState issCastingAnimateState =
        new MagicCastingAnimateState(IMagicCastingState.CastingPhase.NONE);

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

    /**
     * 从枚举声明的中途事件里取出指定种类的 tick，取不到就是枚举写漏了。
     */
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
        // 生成点即秋千位：结构里她本来就摆在秋千上，战败之后要回到这儿。
        this.homePos = this.blockPosition();
        return result;
    }

    /**
     * 秋千的位置。
     *
     * <p>没做成方块实体也没做成坐骑，就取她的生成点——结构 nbt 里她本来就摆在秋千上，
     * 坐姿完全由动画表现。少一整套方块实体，也少一条"秋千被拆了怎么办"的边界。
     */
    @Nullable
    public BlockPos homePos() {
        return this.homePos;
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

    /**
     * 剑牢的剑不当场落，等投掷动画甩出去那一帧再落。
     *
     * <p>由 {@code SwordPrisonSpell.onCast} 调：法术本身仍是瞬发的，
     * 玩家和女仆放它照旧当场落剑，只有酒狐要跟自己的投掷动作对齐。
     */
    public void scheduleSwordRing(int spellLevel, LivingEntity target) {
        this.swordRingDelayTicks = SWORD_RING_RELEASE_TICKS;
        this.swordRingSpellLevel = spellLevel;
        this.swordRingTarget = target;
    }

    /**
     * 甩枪那一下还没做完，别的 AI 决策一律让路，见 {@link #isBusyCombatAction}。
     */
    private boolean isThrowingSpear() {
        return this.swordRingDelayTicks > 0;
    }

    /**
     * 中途出事（转阶段 / 战败 / 脱战）就作废：动作被打断了，枪就当没投出去。
     */
    void cancelSwordRing() {
        this.swordRingDelayTicks = 0;
        this.swordRingTarget = null;
    }

    /**
     * 数到甩出去那一帧，把剑放出来。
     *
     * <p>放在实体这边而不是战斗 Goal 里：Goal 的 {@code tick()} 在目标没了时第一行就返回，
     * 计时会卡住，剑永远不落。
     */
    private void tickSwordRing() {
        if (this.swordRingDelayTicks <= 0 || --this.swordRingDelayTicks > 0) {
            return;
        }
        LivingEntity target = this.swordRingTarget;
        this.swordRingTarget = null;
        if (target == null || !target.isAlive()
            || !(IronsSpellbooksCompatSpells.SWORD_PRISON.get() instanceof SwordPrisonSpell spell)) {
            return;
        }
        spell.summonSwordRing(this.level(), this.swordRingSpellLevel, this, target);
    }

    /**
     * 主手拿的是星影长剑。主手物品会同步给客户端，动画可以直接判。
     */
    private boolean isHoldingLongsword() {
        return this.getMainHandItem().getItem() instanceof StarShadowLongswordItem;
    }

    /**
     * 主手拿的是星影法杖。
     */
    private boolean isHoldingStaff() {
        return this.getMainHandItem().getItem() instanceof StarShadowStaffItem;
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
     * 而 {@code Mob} 默认让身体跟着 {@code MoveControl} 的行进方向转 —— 于是绕圈时 她是侧着甚至背对着人飞的，看上去像在逃跑而不是在压迫。
     *
     * <p>{@code yBodyRot} 是**身体**朝向，头由 {@code LookControl} 另外管、
     * 再由 TLM 的渲染器按模型包里的骨骼叠上去，所以这里只钉身体就够，头会自然跟上。
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        // 这两条必须挂在 aiStep 而不是 customServerAiStep 上。
        // LivingEntity.aiStep 里 isImmobile() 为真时整条 serverAiStep 都不跑，
        // 而她恰恰在「坐着」和「战败」这两种状态下都是 immobile ——
        // 台词播报和回秋千的倒计时放那边会永远停在第一 tick。
        this.dialogue.tick(this);
        this.tickReturnHome();
        if (this.isDefeated()) {
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
        this.entityData.define(PHASE_TWO, false);
        this.entityData.define(TRANSITIONING, false);
        // 默认坐着：她是被邀战才起身的，不是刷出来就打。
        this.entityData.define(SEATED, true);
        this.entityData.define(RESTRICTED, false);
    }

    /**
     * 她还坐在秋千上，没有接受挑战。
     */
    public boolean isSeated() {
        return this.entityData.get(SEATED);
    }

    /**
     * TLM 的 {@code main} 动画通道靠这一位选 {@code sit}。
     */
    @Override
    public boolean isMaidInSittingPose() {
        return this.isSeated();
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
     * 那 65t 里阶段标志还没翻，但手里已经是新武器了 —— 看阶段会姿势对不上。 与 {@code weapon_form} / 两条 hold 控制器同源。
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
     *
     * <p>坐着的时候一律返回 false。这一条挡在<b>目标选择器的谓词</b>上，
     * 比在 {@code customServerAiStep} 里每 tick 清目标可靠——那边清掉之后，
     * 同一 tick 里 {@code NearestAttackableTargetGoal} 还能立刻再选一个回来。
     */
    boolean isViableTarget(@Nullable LivingEntity candidate) {
        if (this.isSeated()) {
            return false;
        }
        if (candidate == null || !candidate.isAlive()) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return candidate.getHealth() > SURVIVAL_HEALTH_FLOOR;
    }

    /**
     * 当前目标已经濒死就松手，交给 {@code targetSelector} 另选一个。
     */
    private void releaseSubduedTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && !this.isViableTarget(target)) {
            this.setTarget(null);
        }
    }

    private void beginAction(WinefoxAction action) {
        this.entityData.set(ACTION, action.id());
        this.entityData.set(ACTION_SERIAL, this.entityData.get(ACTION_SERIAL) + 1);
        this.actionStartTick = this.tickCount;
    }

    /**
     * 上一条动作动画放完了没有 —— 没放完就不再起新的。
     *
     * <p>不这样卡的话，施法动画会在她贴脸时**整段哑掉**。链路是这样的：
     * 近战冷却写死 12t，可挥砍动画是 18~30t，于是她总在上一条播完之前就砍下一刀， 每刀都 {@code beginAction} 涨一次序号 → provider 报一次 INSTANT → {@code markNeedsReload()} 把 {@code magic_casting} 控制器重新拉起来。 而 TLM 的 {@code predicateMagicCastingAnimation} 里有这么一段：
     *
     * <pre>
     * if (currentPhase == NONE) {
     *     if ((lastPhase == INSTANT || lastPhase == END)
     *             &amp;&amp; controller.getAnimationState() != STOPPED) {
     *         return PlayState.CONTINUE;   // &lt;-- 直接 return，轮不到下一个 provider
     *     }
     *     ...
     * </pre>
     * <p>
     * 控制器只要没 STOPPED 就从这儿返回，{@code ISSCastingAnimationProvider} 一次都轮不到。持续贴脸 = 控制器永远没机会停 = 施法动画一直不播。
     *
     * <p>所以这里只卡「起不起新动画」，<b>不卡伤害</b>：{@code doHurtTarget} 仍旧每 12t 一次，
     * DPS 一点没变；只是两刀之间留出了空档，让控制器停下来、把通道让给施法动画。
     */
    private boolean canStartNewComboAction() {
        WinefoxAction action = this.currentAction();
        if (!action.hasOwnAnimation()) {
            return true;
        }
        return this.tickCount - this.actionStartTick >= action.durationTicks();
    }

    /**
     * 客户端与服务端都从这里读当前动作，整数编号只活在同步值里。
     */
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
            // 只卡动画，不卡伤害 —— doHurtTarget 在 goal 那边照旧每 12t 一次。
            if (this.canStartNewComboAction()) {
                if (this.isPhaseTwo()) {
                    this.beginAction(this.nextGroundSwordAction());
                } else {
                    this.beginAction(this.nextStaffAction());
                }
            }
        }
        super.swing(hand);
    }

    /**
     * 法术起不来时的兜底远程攻击。
     *
     * <p><b>不调 {@link #swing}。</b>她这边 {@code swing()} 被重载成「起一段武器攻击动作」
     * （{@code sword_attack_*} / {@code staff_attack_*}），射一箭却播一段劈砍是错的； 而且这一发本来就是替某个法术兜底的，法术那条路自己有动画 （{@code ISSCastingAnimationProvider} 从铁魔法的同步数据算相位）。
     *
     * <p>女仆那边同理：{@code IronsSpellbooksProvider} 里那句 {@code maid.swing(...)}
     * 是注释掉的，只有 {@code ArsNouveauProvider} / {@code ManaAndArtificeProvider} 留着 —— 那两个模组的法术没有自己的施法动画，才需要挥一下手当兜底。
     */
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
    }

    boolean isBusyCombatAction() {
        return this.isTransitioning() || this.isThrowingSpear();
    }

    boolean teleportAwayFrom(LivingEntity target, double distance) {
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
     * serverAiStep()} 就是原版给的开关：判真则整个 {@code serverAiStep} 都不进， 同时把 {@code xxa/zza} 清零 —— 顺手解决了"控制器停了、上一帧的移动输入还留着 继续把她往前推"这个尾巴。
     *
     * <p>只能停在这一层。光让 {@code customServerAiStep} 早退是不够的：那个回调挂在
     * {@code Mob.serverAiStep} 的中段，它**前面**的 {@code goalSelector} 与 **后面**的 {@code lookControl} / {@code bodyRotationControl} 照样会走， 于是她躺在地上还会转头看人、跟着扭身子。而 {@code serverAiStep} 本身是 {@code final}， 覆写不了。
     *
     * <p>{@code travel} 不在这条分支里，所以重力照旧 —— {@link #beginDefeat} 关掉了
     * {@code NoGravity}，她还是会落到地上。
     */
    @Override
    protected boolean isImmobile() {
        return this.isDefeated() || this.isSeated() || super.isImmobile();
    }

    /**
     * 递上星云核心即为邀战。
     *
     * <p>只认坐着的时候：打起来之后再塞一颗核心不该有任何效果，
     * 已经战败躺下的更不该被"重新激活"。
     */
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!this.isSeated() || this.isDefeated()
            || !held.is(MaidSpellItems.NEBULA_CORE.get())) {
            return super.mobInteract(player, hand);
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        this.acceptChallenge(player);
        return InteractionResult.CONSUME;
    }

    /**
     * 接受挑战：起身、亮血条、放开场白、锁定挑战者。
     */
    private void acceptChallenge(Player challenger) {
        this.entityData.set(SEATED, false);
        this.bossEvent.setVisible(true);
        this.dialogue.speak(WinefoxDialogue.challengeAccepted());
        this.resetBattleTally();
        this.entityData.set(RESTRICTED, false);
        this.setTarget(challenger);
        this.level().playSound(null, this.blockPosition(),
            SoundEvents.BEACON_ACTIVATE, this.getSoundSource(), 1.0F, 1.2F);
    }

    /**
     * 战败之后推不动。
     *
     * <p>{@link #isImmobile} 只掐掉 AI，管不着碰撞推挤 —— 那是另一条路：别人的
     * {@code LivingEntity.pushEntities} 用 {@code EntitySelector.pushableBy} 收集周围实体， 而那个谓词问的是**被推者**的 {@code isPushable()}。所以不覆写这一个开关的话， 玩家能把躺在地上、AI 全停的她一路顶着走。
     */
    @Override
    public boolean isPushable() {
        return !this.isDefeated() && super.isPushable();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.isSeated()) {
            // 坐着的时候不索敌、不转阶段、不结算血条：这一场还没开始。
            this.setTarget(null);
            this.bossEvent.setVisible(false);
            return;
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.prioritizePlayerTarget();
        this.tickBattleOver();

        this.releaseSubduedTarget();
        this.tickPhaseThresholds();
        if (this.isTransitioning()) {
            this.tickPhaseTransition();
        } else {
            this.tickSwordRing();
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
        // 转阶段要独占动作层，先把在飞的吟唱掐掉，免得法术在转场动画里落地。
        // 掐掉本身会让客户端算出 END 相位、想播一遍收尾动画，但转阶段这 120t 里
        // WinefoxActionAnimationProvider 一直占着 magic_casting 通道（优先级 200），
        // 施法 provider 挤不进来，所以看不到。
        this.cancelCast();
        this.cancelSwordRing();
        this.phaseTransitionTicks = PHASE_TRANSITION_TICKS;
        this.phaseTransitionKnockbackReleased = false;
        this.phaseTransitionWeaponSwapped = false;
        this.phaseTransitionTarget = toPhaseTwo;
        this.entityData.set(TRANSITIONING, true);
        // 两个方向共用同一项动作，方向已经记在 phaseTransitionTarget 上了。
        this.beginAction(WinefoxAction.PHASE_TRANSITION);
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
        // 直接扫 level().players()，不走 getEntitiesOfClass：后者要把 48 格立方体覆盖到的
        // 三百来个 entity section 全走一遍，还得为结果和 stream 各分配一次，
        // 而在线玩家本来就是一张很短的表。
        double rangeSqr = Mth.square(this.getAttributeValue(Attributes.FOLLOW_RANGE));
        Player nearestPlayer = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (Player player : this.level().players()) {
            if (!isAttackablePlayer(player)) {
                continue;
            }
            double distanceSqr = this.distanceToSqr(player);
            if (distanceSqr <= rangeSqr && distanceSqr < nearestDistanceSqr) {
                nearestPlayer = player;
                nearestDistanceSqr = distanceSqr;
            }
        }
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
        // 坐着的时候打不动她：这一场得用星云核心正式邀战，偷袭不算数。
        if (this.isSeated()) {
            return false;
        }
        float adjustedAmount = amount;
        if (isMaidDamage(source)) {
            adjustedAmount *= 0.5F;
        }
        if (this.isPhaseTwo()) {
            adjustedAmount *= 0.5F;
        }

        float healthBefore = this.getHealth();
        boolean hurt = super.hurt(source, adjustedAmount);
        this.recordDamageShare(source, healthBefore - this.getHealth());
        if (this.getHealth() <= SURVIVAL_HEALTH_FLOOR && !this.level().isClientSide) {
            this.beginDefeat(source);
        }
        return hurt;
    }

    /**
     * 记一笔"这一场是谁打的"。
     *
     * <p>统计的是<b>真正扣掉的血</b>而不是入参伤害：护甲、抗性、吸收都结算过了，
     * 也不会把无敌帧里被 {@code LivingEntity.hurt} 丢掉的那些算进来 —— 那些根本没造成伤害。
     *
     * <p>挂在 {@link #hurt} 里而不是另开一个事件监听，是因为这里本来就已经判过
     * {@code isMaidDamage(source)} 了，多加两个累加器不需要任何新的拦截点。
     */
    private void recordDamageShare(DamageSource source, float dealt) {
        if (dealt <= 0.0F || this.level().isClientSide) {
            return;
        }
        this.totalDamageTaken += dealt;
        if (isMaidDamage(source)) {
            this.maidDamageTaken += dealt;
        }
    }

    /**
     * 真伤改道：不许直写血量，一律折回 {@link #hurt}。
     *
     * <p>不改道的话，真伤会一次性绕过女仆减伤、二阶段减伤、转阶段的 120t 无敌、
     * 1 点血地板，还会让血直接归零走原版死亡 —— 战败演出、血条收起、战利品判定全部跳过。
     *
     * <p>顺带把"用过真伤"这一位记下来：这是流程图里 R1 的两个触发条件之一。
     * 记在这儿而不是在饰品那边，是因为这里能看到<b>所有</b>真伤来源，
     * 包括以后新加的饰品和调试指令。
     */
    @Override
    public boolean maidspell$redirectTrueDamage(float amount, @Nullable LivingEntity attacker) {
        if (this.level().isClientSide || amount <= 0.0F) {
            return false;
        }
        this.trueDamageUsed = true;
        DamageSource source = attacker != null
                              ? this.damageSources().mobAttack(attacker)
                              : this.damageSources().magic();
        return this.hurt(source, amount);
    }

    /**
     * 血量的地板：护甲、抗性、吸收全部结算完之后，把血兜回 1 点。
     *
     * <p>{@code actuallyHurt} 是扣血的那一步，而 {@code LivingEntity.hurt} 是在它返回之后
     * 才查 {@code isDeadOrDying()} 决定要不要走死亡流程 —— 卡在这两步中间兜血， 血量就从来没有到过 0，原版的死亡分支一次都不会进。
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (!bypassesSurvival(source) && this.getHealth() < SURVIVAL_HEALTH_FLOOR) {
            this.setHealth(SURVIVAL_HEALTH_FLOOR);
        }
    }

    /**
     * 这一击是不是 {@code /kill} 一类的强制移除：那种不受 1 点血地板保护。
     */
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
    private void beginDefeat(DamageSource source) {
        if (this.isDefeated()) {
            return;
        }
        this.entityData.set(DEFEATED, true);
        // 她倒下了，场上的召唤物没有理由继续打。
        this.recallSummons();
        this.cancelCast();
        this.cancelSwordRing();
        this.clearAction();
        this.dropWeaponForDefeat();
        this.entityData.set(TRANSITIONING, false);
        this.phaseTransitionTicks = 0;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(false);
        this.setInvulnerable(true);
        this.setTarget(null);
        // 打完了，血条收掉；她本人留在场上倒着。
        this.bossEvent.setVisible(false);
        // 战败那一刻把这一场的归属结算下来，之后回秋千、发战利品都读它。
        this.entityData.set(RESTRICTED, this.computeRestricted());
        this.dropDefeatRewards(source);
        this.returnHomeTicks = DEFEAT_RETURN_HOME_TICKS;
    }

    /**
     * 发战败奖励。
     *
     * <p><b>必须自己调 {@code dropFromLootTable}。</b>原版只在 {@code LivingEntity.die()}
     * 里发战利品，而她的血被 {@link #SURVIVAL_HEALTH_FLOOR} 钉在 1、{@code die()} 一次都不会进 ——
     * 光把物品写进 {@code loot_tables/entities/magical_winefox_boss.json} 是发不出来的。
     *
     * <p>星云核心不走战利品表而是直接落地：它是 R1 的结算结果，
     * 掉不掉取决于这一场怎么打的，不是随机项。掉的就是进场时消耗的那一枚 ——
     * 堂堂正正赢下来她就还给你，被判代打就留下。
     */
    private void dropDefeatRewards(DamageSource source) {
        this.dropFromLootTable(source, this.lastHurtByPlayerTime > 0);
        if (!this.isRestricted()) {
            this.spawnAtLocation(new ItemStack(MaidSpellItems.NEBULA_CORE.get()));
        }
    }

    /**
     * 这一场算不算「女仆代打」，也就是流程图里的 R1。
     *
     * <p>两个触发条件任一成立即判限制：用过真伤，或者女仆打出的伤害占比超过阈值。
     * 前者是因为真伤本身就绕过了她全部的防御机制（见 {@link ITrueDamageRedirect}），
     * 后者是因为流程图要求玩家自己下场，而不是站在后面看女仆刷。
     *
     * <p>一滴伤害都没吃到（比如被指令直接判负）时不判限制：那不是代打，是没打。
     */
    private boolean computeRestricted() {
        if (this.trueDamageUsed && Config.winefoxTrueDamageRestrictsReward) {
            return true;
        }
        if (this.totalDamageTaken <= 0.0F) {
            return false;
        }
        return this.maidDamageTaken / this.totalDamageTaken > Config.winefoxMaidDamageShareLimit;
    }

    /**
     * 这一场被判了限制：不掉星云核心、不解锁特殊交易。
     */
    public boolean isRestricted() {
        return this.entityData.get(RESTRICTED);
    }

    /**
     * 战败演出放完之后回到秋千坐下。
     *
     * <p>用传送而不是寻路：她刚被打趴下、AI 全停（{@link #isImmobile}），
     * 而且秋千通常悬在半空，寻路根本走不过去。
     *
     * <p>坐下之后 {@code DEFEATED} 就撤了 —— 战败是一段演出，不是一个终态。
     * 撤掉它 {@code magic_casting} 通道才会松手，{@code main} 通道上的 {@code sit} 才盖得住。
     * 她仍然打不动（{@link #isSeated} 那条守卫），要再打得再递一颗核心。
     */
    private void tickReturnHome() {
        if (this.returnHomeTicks <= 0 || --this.returnHomeTicks > 0) {
            return;
        }
        BlockPos home = this.homePos;
        if (home != null) {
            this.teleportTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.setInvulnerable(false);
        this.entityData.set(DEFEATED, false);
        this.entityData.set(SEATED, true);
        this.equipStarMajoGear();
        this.resetBattleTally();
    }

    /**
     * 玩家这边打完了：她收手，回秋千坐下。
     *
     * <p>这就是流程图里的 B6 → B7。判据不是「谁的血到 1」而是<b>场上再没有可打的目标</b> ——
     * {@link #isViableTarget} 已经把 1 点血的玩家排除掉了，所以「把人打服」自然表现为没目标；
     * 顺带还兜住了玩家跑掉、下线、切维度这几种同样该收场的情况。
     *
     * <p>要连续空 {@link #BATTLE_OVER_GRACE_TICKS} 才算数：战斗中目标短暂消失
     * （对方传送、换目标的间隙）很常见，立刻收场会把打到一半的架判和局。
     */
    private void tickBattleOver() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.noTargetTicks = 0;
            return;
        }
        if (++this.noTargetTicks < BATTLE_OVER_GRACE_TICKS) {
            return;
        }
        this.noTargetTicks = 0;
        this.dialogue.speak(WinefoxDialogue.playerSubdued());
        // 走和战败一样的归位路径，只是不进战败演出、不结算战利品。
        this.cancelCast();
        this.cancelSwordRing();
        this.clearAction();
        this.recallSummons();
        this.getNavigation().stop();
        this.bossEvent.setVisible(false);
        this.returnHomeTicks = DEFEAT_RETURN_HOME_TICKS;
    }

    /**
     * 归零这一场的记账，为下一次挑战让路。
     *
     * <p>{@code RESTRICTED} 不在此列：它是<b>上一场的结论</b>，交易解锁要一直读它，
     * 直到下一场重新开打（{@link #acceptChallenge}）才刷新。
     */
    private void resetBattleTally() {
        this.totalDamageTaken = 0.0F;
        this.maidDamageTaken = 0.0F;
        this.trueDamageUsed = false;
    }

    /**
     * 读档时把战败契约重新落一遍。
     *
     * <p>{@link #beginDefeat} 做的那些事只在"战败发生的那一刻"跑过一次，
     * 之前版本存下来的战败个体不满足这些约束 —— 手上这个存档里就躺着一只 57 血、手里还攥着星影长剑的。幂等地补齐，免得旧档看起来像新 bug。
     *
     * <p>放在 {@code super.readAdditionalSaveData} 之后：
     * {@code Mob} 那一层刚用 NBT 里的 HandItems 把武器塞回手上， {@code LivingEntity} 那一层刚把 Health 读回来， {@code Entity.load} 更早（第 45 行 vs 第 66 行调 {@code readAdditionalSaveData}） 就把 NoGravity 读回来了 —— 这里正好一并覆盖掉。
     *
     * <p>NoGravity 这一项尤其不能漏：{@link #isImmobile} 之后整个 {@code serverAiStep}
     * 都不跑，那句把重力开回来的 {@code setNoGravity(false)} 只在 {@link #beginDefeat} 里执行过。存档里带着 {@code NoGravity=true} 的战败个体读回来会一直浮在空中， 而且现在 {@link #isPushable} 返回 false，连推都推不下来。
     */
    private void normalizeDefeatState() {
        this.dropWeaponForDefeat();
        this.setNoGravity(false);
        if (this.getHealth() > SURVIVAL_HEALTH_FLOOR) {
            this.setHealth(SURVIVAL_HEALTH_FLOOR);
        }
    }

    /**
     * 战败时把手里的武器收掉，否则她躺下了手上还举着剑。
     *
     * <p>这一条是渲染器迁移带出来的。模型包作者本来考虑过：{@code pre_parallel0} 常年把
     * {@code Mweapon}（包里自带的那把武器几何体）{@code scale} 成 0，而 {@code death} 排在 {@code magic_casting} 上、盖得过它，于是先张开 1.25s 再在 1.5s 缩回 0 —— 一套"武器随人一起消失"的编排。
     *
     * <p>可迁到 TLM 的女仆渲染器之后，她手里那把是真的 {@code ItemStack}，由
     * {@code GeckoLayerMaidHeld} 画，走的是 {@code RightHandLocator} 那条定位链， 跟 {@code Mweapon} 只是**兄弟**关系 —— 作者把 {@code Mweapon} 缩成 0 对它毫无影响。
     *
     * <p>TLM 那边能遮住持物的只有两个口子：
     * <ol>
     *   <li>{@code RenderUtils.prepMatrixForLocator} 里，定位链上**除最后一根之外**任意一根
     *       {@code scale} 恰好为 (0,0,0) 就返回 true，持物整个不画。可这条链上够得着的
     *       （{@code rightshou} / {@code RightHand} / {@code RightForeArm}…）全都带几何体，
     *       {@code rightshou} 那颗就有 2.7³，缩掉等于把她小臂削一块，不能用。</li>
     *   <li>{@code mainHandItem.isEmpty()} —— 这一条干净。</li>
     * </ol>
     *
     * <p>所以走第二条。清掉之后，作者给 {@code death} 编的那套武器消失动画反而正好显出来：
     * 手上的真武器立刻没了，包里那把在 0~1.25s 张开、1.5s 缩掉。掉落率本来就是 0，不用管。
     */
    private void dropWeaponForDefeat() {
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /**
     * 战败演出已经开始（含已放完等待移除）。客户端也要能判，动画靠它切 {@code defeat}。
     */
    public boolean isDefeated() {
        return this.entityData.get(DEFEATED);
    }

    /**
     * 收回她召唤出来的东西。
     *
     * <p>不能走 {@code PlayerRecasts.removeAll}：那条路最后会调到
     * {@code AbstractSpell.onRecastFinished(ServerPlayer, ...)}，而召唤系法术
     * （如 {@code SummonSwordsSpell}）在里面直接 {@code serverPlayer.serverLevel()} —— 施法者是怪物时那个参数是 {@code null}，当场 NPE。
     *
     * <p>所以自己来：{@link SummonManager} 记着"谁召的谁"，按主人反查一遍解散掉，
     * 再把她的 recast 记账整个换成一份空的。
     *
     * <p>换掉记账这一步是必须的。{@code PlayerRecasts.tick} 只对真玩家走
     * （{@code serverPlayer != null} 才递减），怪物那份记录于是永不过期；
     * 而 {@code SummonSwordsSpell.onCast} 开头就查 {@code hasRecastForSpell}， 有记录就整个跳过 —— 不清的话她这辈子只能召唤这一次。
     */
    void recallSummons() {
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
     * 护甲、抗性、吸收全部结算完、马上就要扣到血条上的那个数， 后者是结算**之前**的原始伤害。按原始伤害去削，护甲会再砍一刀，
     * 玩家的血只会渐近 1 而永远碰不到 1 —— 那样 {@link #isViableTarget} 就一直认为他还能打，她会追着一个永远打不服的人不放。
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

        /**
         * 伤害是否出自酒狐：直接打的、她的弹体、或她发的法术。
         */
        private static boolean isWinefoxDamage(DamageSource source) {
            return damageFrom(source, MagicalWinefoxBossEntity.class);
        }
    }

    private static boolean isMaidDamage(DamageSource source) {
        return damageFrom(source, EntityMaid.class);
    }

    /**
     * 伤害是否出自某一类实体：它直接打的、或它射出去的弹体。
     *
     * <p>只往回走一层 owner，不用 {@code MaidSpellAllyResolver.resolveResponsibleEntity} ——
     * 那个会把整条 owner 链连同铁魔法的 {@code IMagicSummon} 一起认下来， 归属范围会连她召出来的剑一并算进去，比这两处想要的宽。
     */
    private static boolean damageFrom(DamageSource source, Class<?> type) {
        Entity causingEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        if (type.isInstance(causingEntity) || type.isInstance(directEntity)) {
            return true;
        }
        return directEntity instanceof Projectile projectile && type.isInstance(projectile.getOwner());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WinefoxPhaseTwo", this.isPhaseTwo());
        tag.putBoolean("WinefoxTransitioning", this.isTransitioning());
        tag.putInt("WinefoxTransitionTicks", this.phaseTransitionTicks);
        tag.putBoolean("WinefoxTransitionToPhaseTwo", this.phaseTransitionTarget);
        tag.putBoolean("WinefoxDefeated", this.isDefeated());
        tag.putBoolean("WinefoxSeated", this.isSeated());
        tag.putBoolean("WinefoxRestricted", this.isRestricted());
        tag.putInt("WinefoxReturnHomeTicks", this.returnHomeTicks);
        tag.putFloat("WinefoxTotalDamageTaken", this.totalDamageTaken);
        tag.putFloat("WinefoxMaidDamageTaken", this.maidDamageTaken);
        tag.putBoolean("WinefoxTrueDamageUsed", this.trueDamageUsed);
        if (this.homePos != null) {
            tag.put("WinefoxHomePos", NbtUtils.writeBlockPos(this.homePos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(PHASE_TWO, tag.getBoolean("WinefoxPhaseTwo"));
        if (tag.contains("WinefoxHomePos")) {
            this.homePos = NbtUtils.readBlockPos(tag.getCompound("WinefoxHomePos"));
        }
        // 缺键的是坐姿这套做出来之前存下的个体，那时候她们都是站着打的，
        // getBoolean 的默认 false 正好，不必特判。
        this.entityData.set(SEATED, tag.getBoolean("WinefoxSeated"));
        this.entityData.set(RESTRICTED, tag.getBoolean("WinefoxRestricted"));
        this.returnHomeTicks = tag.getInt("WinefoxReturnHomeTicks");
        this.totalDamageTaken = tag.getFloat("WinefoxTotalDamageTaken");
        this.maidDamageTaken = tag.getFloat("WinefoxMaidDamageTaken");
        this.trueDamageUsed = tag.getBoolean("WinefoxTrueDamageUsed");
        // 她战败之后是留在场上的，读档得接着躺着，不能爬起来重新开打。
        this.entityData.set(DEFEATED, tag.getBoolean("WinefoxDefeated"));
        if (this.isDefeated()) {
            this.setInvulnerable(true);
            this.bossEvent.setVisible(false);
            this.normalizeDefeatState();
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
            this.beginAction(WinefoxAction.PHASE_TRANSITION);
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

    /**
     * 血条标题为「万法酒狐——显示名」，显示名本身走实体的翻译键。
     */
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
        this.resendCastingStateTo(player);
        this.resendHealthTo(player);
    }

    /**
     * 给刚进入追踪范围的玩家补一份血量。
     *
     * <p>{@code LivingEntity.defineSynchedData} 把 {@code DATA_HEALTH_ID} 的默认值定成了
     * 正好 {@code 1.0F}，而 {@code SynchedEntityData.getNonDefaultValues()} 会跳过 "当前值 equals 初始值"的项（{@code DataItem.isSetToDefault}）。
     * 战败后她被 {@link #SURVIVAL_HEALTH_FLOOR} 钉在 1.0F —— 不多不少正是那个默认值 —— 于是血量根本不进 {@code ServerEntity} 的配对包，客户端那侧的实体一直停在
     * {@code LivingEntity} 构造函数里 {@code setHealth(getMaxHealth())} 给的 600， 而且此后再不会变：她无敌又不动，血量不会有第二次 set 去触发同步。
     *
     * <p>后果不止是血条数字。{@code query.health} 是客户端求值的，作者写在
     * {@code parallel4} 里的血量门 {@code (query.health/query.max_health) < 0.25} 因此判假， 躺在地上的她显示的是满血形态的九尾，还跟着 {@code pre_parallel2} 一起摆。
     *
     * <p>{@code ServerEntity.addPairing} 先发配对包（内含 {@code getNonDefaultValues}
     * 的快照）、再调 {@code startSeenByPlayer}，所以这一发必定盖在后面。 不加战败判断：任何时候血量恰好落在 1.0F 都会踩到，无条件补发才是对的。
     */
    private void resendHealthTo(ServerPlayer player) {
        if (this.level().isClientSide) {
            return;
        }
        player.connection.send(new ClientboundSetEntityDataPacket(this.getId(),
            List.of(SynchedEntityData.DataValue.create(
                LivingEntityHealthAccessor.maidspell$getHealthAccessor(), this.getHealth()))));
    }

    /**
     * 给刚进入追踪范围的玩家补一份施法状态。
     *
     * <p>施法动画改由铁魔法的 {@code SyncedSpellData} 驱动之后丢了一样东西：那份数据只在
     * <b>变化时</b>下发（{@code SyncedSpellData.doSync()}，唯一的发送点），不像
     * {@code entityData} 那样对新追踪者自动补发。而 {@code AbstractSpellCastingMob} 没有 覆写 {@code startSeenByPlayer}（已核字节码），铁魔法自己也不补。
     *
     * <p>于是中途进场的人在她放 {@code long_cast} / {@code charge_black_hole} 这类
     * 十几秒的循环施法时什么都收不到，只能看她站着发呆 —— 正是旧设计里 {@code CAST_ANIMATION}（{@code entityData}，天然会补发）挡住的那个场景。 这一发把它补回来。
     */
    private void resendCastingStateTo(ServerPlayer player) {
        if (this.level().isClientSide || !this.isCasting()) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
            new SyncEntityDataPacket(this.getMagicData().getSyncedData(), this));
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

    /**
     * {@code IMaid.convert} 第一句就是 {@code mob instanceof IMaid}，直接实现接口即可， 不必走 {@code ConvertMaidEvent}；客户端的 {@code CapabilityEvent} 随后会自动 给她挂上 gecko 动画能力。
     *
     * <p>{@code asStrictMaid()} 保持默认的 {@code null} 不覆写：本项目三个
     * {@code GeckoLayer*Halo} 与 TLM 的聊天气泡都靠它判定，覆写了就会一并挂到 boss 身上。
     */
    @Override
    public String getModelId() {
        return MODEL_ID;
    }

    @Override
    public Mob asEntity() {
        return this;
    }

    @Override
    public ItemStack[] getHandItemsForAnimation() {
        return this.handItemsForAnimation;
    }

    /**
     * 动作动画的两个只读入口，给 {@code WinefoxActionAnimationProvider} 用。
     *
     * <p>迁移前这些状态由实体自己的 gecko4 {@code action} 控制器读；换成 TLM 的女仆渲染器
     * 之后 gecko4 那条路整条不再运行，判读挪到了 TLM 的 {@code magic_casting} 通道上， 于是得开出来。两个都是同步值，客户端读得到。
     */
    public WinefoxAction animationAction() {
        return this.currentAction();
    }

    /**
     * 序号一变就是「新动作开始了」，provider 靠它决定哪一帧报 INSTANT 让动画从头播。
     */
    public int animationActionSerial() {
        return this.entityData.get(ACTION_SERIAL);
    }

    public WinefoxCastingAnimateState castingAnimateState() {
        return this.castingAnimateState;
    }

    @Override
    public MagicCastingAnimateState maidspell$getCastingAnimateState() {
        return this.issCastingAnimateState;
    }

    /**
     * 战败之后她**留在场上**，不再走任何移除流程。
     *
     * <p>{@code death} 是 {@code HOLD_LAST_FRAME} 且被加长到 10000 秒，播不完，
     * 于是她会一直躺在原地。原版的 {@code tickDeath} 本来就只在 {@code isDeadOrDying()} 时被调，而她的血永远是 1，
     * 那个回调根本不会触发 —— 这里覆写成空是为了挡住别处（比如别的模组）主动调它把她计时移除。
     *
     * <p>{@code deathTime} 一并保持为 0：它没有同步给客户端，但渲染那边
     * （原先是 {@code GeoEntityRenderer.applyRotations}）会照着它把实体侧翻， 万一哪天有人把它同步出去，非零值会让倒地姿势再被扭一次。
     *
     * <p><b>但血真的掉到 0 时必须放行。</b>{@link #hurt} 与 {@link #actuallyHurt}
     * 都特意给 {@code BYPASSES_INVULNERABILITY}（{@code /kill}、虚空伤害那一类） 开了口子，就是为了留一条把她清掉的路。
     * 而原版**唯一**的移除路径正是 {@code tickDeath()} 里的 {@code ++deathTime} 到 20 之后那句 {@code remove(RemovalReason.KILLED)} —— 这里整个覆写成空，
     * 等于把自己特意留的那条口子又堵死了：{@code /kill} 把血打到 0，她照样躺着不走。
     *
     * <p>所以只在「战败演出」那种血还剩 1 的状态下空转（挡住别的模组主动调它
     * 提前把她计时移除），真死了就老老实实走原版流程。
     */
    @Override
    protected void tickDeath() {
        if (this.getHealth() <= 0.0F) {
            super.tickDeath();
        }
    }
}
