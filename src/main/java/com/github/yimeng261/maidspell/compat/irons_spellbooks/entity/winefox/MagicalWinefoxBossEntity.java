package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.entity.StarShadowSpearEntity;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.network.casting.SyncEntityDataPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.sounds.SoundEvent;
import java.util.OptionalInt;
import net.minecraft.world.effect.MobEffectInstance;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.ITrueDamageRedirect;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MagicalWinefoxBossEntity extends AbstractSpellCastingMob
    implements Enemy, IMaid, CastingAnimateStateAccessor, ITrueDamageRedirect, Merchant {
    static final String RETIRED_MAID_TAG = "MaidSpellWinefoxRetired";
    static final String RETIRED_MAID_BOSS_TAG = "MaidSpellWinefoxRetiredBoss";
    private static final String RETIRED_MAID_INVULNERABLE_TAG = "MaidSpellWinefoxRetiredInvulnerable";
    private static final String RETIRED_MAID_VANILLA_INVULNERABLE_TAG = "MaidSpellWinefoxRetiredVanillaInvulnerable";
    private static final String RETIRED_MAID_NO_GRAVITY_TAG = "MaidSpellWinefoxRetiredNoGravity";
    private static final String RETIRED_MAID_SITTING_TAG = "MaidSpellWinefoxRetiredSitting";
    private static final String RETIRED_MAID_X_TAG = "MaidSpellWinefoxRetiredX";
    private static final String RETIRED_MAID_Y_TAG = "MaidSpellWinefoxRetiredY";
    private static final String RETIRED_MAID_Z_TAG = "MaidSpellWinefoxRetiredZ";
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

    /** 玩家挑战结束后播放的行礼主动画。 */
    private static final EntityDataAccessor<Boolean> CURTSYING =
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
    private static final EntityDataAccessor<Boolean> BATTLE_MUSIC =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);

    /** 战败演出放完到回秋千坐下之间的间隔。 */
    private static final int DEFEAT_RETURN_HOME_TICKS = WinefoxAction.DEFEAT.durationTicks();

    /** 玩家挑战结束后的行礼动画时长。 */
    private static final int CURTSY_RETURN_HOME_TICKS = 6 * 20;

    /** 场上连续 30 秒没有可打的目标就收场，见 {@link #tickBattleOver}。 */
    private static final int BATTLE_OVER_GRACE_TICKS = 600;

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

    /** 半血进入二阶段，治疗不重置本场战斗阶段。 */
    private static final float PHASE_TWO_HEALTH_FRACTION = 0.5F;

    /**
     * 这是一场不杀人的表演赛：她把人打到只剩 1 点，自己也只掉到剩 1 点。
     *
     * <p>两边都用同一个下限，读起来是一条规则而不是两条巧合。
     */
    static final float SURVIVAL_HEALTH_FLOOR = 1.0F;

    /** 五稿规定动画第二秒发射直线投枪。 */
    private static final int SPEAR_RELEASE_TICKS =
        eventTick(WinefoxAction.SPEAR_THROW, WinefoxAction.EventKind.PROJECTILE);

    /**
     * 身体转向目标的最大角速度。太大就是瞬间贴脸，太小绕圈时会追不上。
     */
    private static final float BODY_TURN_DEGREES_PER_TICK = 15.0F;
    private static final double SEATED_LOOK_RANGE = 12.0D;
    /**
     * 行礼时找人的距离上限。
     *
     * <p>比坐着那 12 格宽得多：行礼是在「玩家挑战失败」之后放的，而那场架可能打在她
     * 48 格的追击范围里的任何地方，玩家也未必会跟着她一起回秋千。太窄的话，只要玩家
     * 站着不动看演出，镜头里就是她背对着人鞠躬。
     */
    private static final double CURTSY_LOOK_RANGE = 48.0D;
    private static final double GREETING_TRIGGER_RANGE = 5.0D;
    private static final double AMBIENT_DIALOGUE_RANGE = 10.0D;
    /**
     * 自言自语（{@code ambient_} 那几句）之间的间隔，60 秒。
     *
     * <p>她说的每一句别的话都会把这份计时钟重新拉满，见
     * {@link #resetAmbientDialogueCooldown()}，所以实际观感是
     * "60 秒内没人跟她搭过话，她才自己念叨一句"。
     */
    private static final int AMBIENT_DIALOGUE_INTERVAL_TICKS = 60 * 20;
    private static final double SEATED_HOVER_OFFSET = 0.5D;
    /**
     * 挑战者必须在这个距离内，邀战倒计时结束时她才会真的起身。
     *
     * <p>女仆替主人先动手时用的是同一把尺子（见 {@link #canStartChallengeFrom}）：
     * 主人够不着的那一击不进战斗，不然倒计时走完她还是会原地坐回去。
     */
    private static final double CHALLENGER_MAX_DISTANCE = 48.0D;
    private static final double PHASE_ONE_VERTICAL_SPEED = 0.04D;
    /**
     * 空中丢失目标后每 tick 额外补的下降速度，见 {@link #tickDescent()}。
     *
     * <p>从 combat 高度（离地约 5 格以上）落到地面的总时长就是
     * {@code 高度 / (本值 + 重力 0.08)} —— 本值取 0.08 即约 1.9 格/秒，
     * 比自由落体慢一个量级，看上去就是「缓缓飘下来」。
     */
    private static final double LOST_TARGET_DESCENT_SPEED = 0.08D;
    /**
     * 缓降期间还要交出去的重力加速度。
     *
     * <p>缓降那一段把重力开着（{@code onGround()} 要靠正常物理才会更新，而且落地观感也更自然），
     * 于是原版 {@code travel} 会在我们用 {@code setDeltaMovement} 钉住 {@code deltaY} 之后
     * 再自己减一个 {@code getGravity()}，并乘一次空气阻尼。阻尼那 2% 可以忽略，
     * 这 0.08 不能：不把它算进去，{@link #LOST_TARGET_DESCENT_SPEED} 会变成实际速度的两倍。
     */
    private static final double DESCENT_GRAVITY = 0.08D;
    /**
     * 距离地面多近就不必再接管垂直速度了。
     *
     * <p>留一点余量给台阶、草径和半砖：没有它，最后那一两格会被换成恒速下坠，
     * 落地的观感反而比自由落体更生硬。
     */
    private static final double DESCENT_GROUND_SNAP_DISTANCE = 0.6D;
    /** 悬停不动的判定阈值。比她现在的速度略大一档，只认得住「几乎停住」。 */
    private static final double HOVER_EPSILON = 0.05D;
    private static final double TRANSITION_KNOCKBACK_RADIUS = 5.0D;
    private static final double TRANSITION_KNOCKBACK_STRENGTH = 4.0D;

    /**
     * 内置模型包里那份模型的 id。
     *
     * <p>客户端渲染时 TLM 拿它去 {@code CustomPackLoader.MAID_MODELS} 查模型 / 贴图 / 动画，
     * 包由 {@code TouhouLittleMaidModelPackInstaller} 解压到 {@code gameDir/tlm_custom_pack}。
     * 包没装上时 TLM 会静默退回默认女仆模型，不会崩。
     */
    public static final String MODEL_ID = "touhou_little_maid_spell:stellar_witch";

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
    private double flightTargetY;
    /**
     * &gt;0 表示投掷动画在跑、还没到甩出去那一帧；数到 0 剑才落。
     */
    private boolean spearPending;
    @Nullable
    private LivingEntity spearTarget;
    private int challengeStartTicks;
    @Nullable
    private UUID challengerId;
    @Nullable
    private UUID postVictoryChatPlayerId;
    private final java.util.Set<UUID> greetedPlayers = new java.util.HashSet<>();
    private int ambientDialogueCooldown;
    private final Set<UUID> retiredMaidIds = new HashSet<>();
    private int phaseTransitionTicks;
    private boolean phaseTransitionKnockbackReleased;
    private boolean phaseTransitionWeaponSwapped;
    /** 秋千位。纯服务端，客户端只要知道"在不在坐"，不需要知道坐在哪。 */
    @Nullable
    private BlockPos homePos;
    /** 开场白的排队播报，见 {@link WinefoxDialogue}。 */
    private final WinefoxDialogue dialogue = new WinefoxDialogue();
    /** 本场累计吃到的伤害，以及其中出自女仆的部分。用来算「女仆代打」的伤害占比。 */
    private float totalDamageTaken;
    private float maidDamageTaken;
    /** 本场有没有人对她用过真伤。「女仆代打」的另一个触发条件。 */
    private boolean trueDamageUsed;
    /** 战败演出结束后回秋千的倒计时，见 {@link #tickReturnHome}。 */
    private int returnHomeTicks;
    /** 连续多少 tick 没有可打的目标了，见 {@link #tickBattleOver}。 */
    private int noTargetTicks;
    /** 她输过至少一次，交易就此开放。落 NBT，不随重新挑战撤销。 */
    private boolean tradingUnlocked;
    private boolean hasStartedChallenge;
    /** 正在跟她做买卖的玩家；交易表现算，见 {@link #getOffers}。 */
    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;
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
        if (this.isSeated()) {
            // A seated winefox is anchored to the swing. Do not let a combat
            // move controller or a stale vertical impulse move her afterward.
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (this.isDefeated() || this.returnHomeTicks > 0) {
            // 归位演出期间不接受任何移动输入。
            //
            // <p>这一段里 {@link #isImmobile()} 为真，{@code LivingEntity.aiStep} 会跳过整条
            // {@code serverAiStep} —— 而 {@code moveControl.tick()} 正是那里面唯一的一行。
            // 于是 {@code FlyingMoveControl} 上一场战斗锁存下来的二值垂直输入（{@code yya}）
            // 既不会被重算、也不会被清掉：原版 immobile 分支只清 {@code xxa}/{@code zza}，
            // 而 {@code travel()} 拿到的输入向量是 (xxa, yya, zza)。那一份输入会被
            // {@code moveRelative} 逐 tick 累加（垂直方向只有 0.98 的阻尼），
            // 在这 100t（战败）/ 120t（行礼）里把她一路顶上天，直到 {@link #tickReturnHome}
            // 再把她拽回秋千 —— 玩家看到的就是「归位时向上飞」。
            //
            // <p>这里只掐输入、不掐物理：战败那一路 {@link #beginDefeat} 关掉了 NoGravity，
            // 她还得照常落到地上躺着。
            super.travel(Vec3.ZERO);
            return;
        }
        if (this.isNoGravity() && this.getTarget() != null && this.getTarget().isAlive()) {
            // Keep horizontal steering, but approach the requested altitude without the controller's binary Y input.
            Vec3 delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            super.travel(new Vec3(travelVector.x, 0.0D, travelVector.z));
            if (!this.isBusyCombatAction()) {
                double maxVerticalSpeed = this.isPhaseTwo() ? 0.25D : PHASE_ONE_VERTICAL_SPEED;
                double vertical = Mth.clamp(this.flightTargetY - this.getY(),
                    -maxVerticalSpeed, maxVerticalSpeed);
                this.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.0D, vertical, 0.0D));
            }
            delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            return;
        }
        super.travel(travelVector);
    }

    /**
     * 空中丢失目标后不再听凭自由落体，改成一段恒速缓降。
     *
     * <p>此前只有 {@code customServerAiStep} 里那一句 {@code setNoGravity(combatFlight)}：
     * 目标一没，重力立刻合上，她直接从战斗高度（离地约 5 格以上）自由落体砸到地上。
     * 这里把「无目标且悬停中」这一段接管过来：每 tick 把垂直速度钉回
     * {@code -(LOST_TARGET_DESCENT_SPEED + DESCENT_GRAVITY)}，重力后续再减、阻尼再乘，
     * 净效果就是稳定在 {@link #LOST_TARGET_DESCENT_SPEED} 上下 —— 原版物理照常收尾。
     * 水平方向不碰：{@code travel} 那条「有重力就没有垂直输入」的分支会照常处理横向移动。
     *
     * <p>{@code deltaY < HOVER_EPSILON} 那条是「本来就在悬停」的判据。没有它，
     * 起跳瞬间（{@code deltaY} 为正）也会被误判成丢失目标，她会在上升途中被按住。
     * 已经在按恒速下降时这条仍然成立（{@link #LOST_TARGET_DESCENT_SPEED} 自身就在阈值内），
     * 所以整段缓降是自持的，直到落地。
     *
     * <p>落地条件用 {@code onGround()} 加高度图余量，两条都不能少：前者保证贴地之后
     * 立刻交回原版物理，后者防的是「已经被台阶/半砖接住、{@code onGround()} 还没来得及翻」
     * 那一两 tick 里继续被按着往下钻。
     */
    private void tickDescent() {
        Vec3 delta = this.getDeltaMovement();
        boolean descending = !this.isNoGravity() && !this.onGround() && delta.y < HOVER_EPSILON;
        if (!descending) {
            return;
        }
        // 已经贴住了就不再接管：留一点余量，让落地那几 tick 交回原版物理。
        BlockPos column = this.blockPosition();
        double groundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        double altitude = this.getY() - groundY;
        if (altitude <= DESCENT_GROUND_SNAP_DISTANCE) {
            return;
        }
        this.setDeltaMovement(delta.x, -(LOST_TARGET_DESCENT_SPEED + DESCENT_GRAVITY), delta.z);
    }

    void setFlightDestination(Vec3 destination, double speed) {
        this.flightTargetY = destination.y;
        this.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, speed);
    }

    /**
     * 当前被命令驻留的高度。战斗 AI 原地站定时要把这一项一起挂住 ——
     * {@code MoveControl} 记的是"想去哪"，只清速度不清目标点的话，
     * 上一帧那个高度命令会继续把她往上拽。
     */
    double flightTargetY() {
        return this.flightTargetY;
    }

    /**
     * Stop any combat altitude command when combat is no longer driving movement.
     * The move controller keeps its wanted position independently of delta movement,
     * so clearing only the latter can leave a stale upward command behind.
     *
     * <p><b>但「想去哪」不是唯一的残留。</b>{@code FlyingMoveControl} 每次处理一条
     * {@code MOVE_TO} 时还会把二值的垂直输入与当时的速度锁进实体自己身上
     * （{@code Mob.setYya(±f2)}、{@code Mob.setSpeed(f2)}），而它的清理点只有下一次
     * {@code tick()}。归位 / 坐着这两种状态下 {@link #isImmobile()} 为真，
     * {@code Mob.serverAiStep} 整条不跑，{@code moveControl.tick()} 一次都不会来 ——
     * 上一场战斗留下的 {@code yya} 就这么一直挂在输入向量里，被 {@code travel()}
     * 逐 tick 当作油门（参见那里的注释）。所以这里必须连输入一起清掉。
     *
     * <p>与 {@code MaidMovementHelper.stopAllMovement} / {@code MaidMoveControlMixin}
     * 里停女仆移动用的是同一套字段，理由相同。
     */
    private void resetFlightControl() {
        this.flightTargetY = this.getY();
        this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setZza(0.0F);
        this.setSpeed(0.0F);
        this.setJumping(false);
    }

    /**
     * 摔落伤害一律不吃。
     *
     * <p>她是常年在战斗高度上悬停的飞行单位：目标一丢就从战斗高度落回地面
     * （缓降那一段见 {@link #tickDescent}），战败演出也是从空中落到地上演完的，
     * 落地那一下按原版算下来是好几个心。她本来也不靠「掉落伤害」承受任何战斗压力，
     * 这条纯粹是抹掉一个与玩法无关的尾账。
     *
     * <p>先走 {@code super} 再返回 false：Forge 的 {@code LivingFallEvent} 挂在那里面，
     * 别的模组还想在这一击上做文章的话不该被我们吞掉。返回 false 同时让
     * {@code LivingEntity.checkFallDamage} 把 {@code fallDistance} 清零，
     * 否则那段距离会挂在她身上，等到下一次真正落地时再来一遍。
     *
     * <p>{@code BYPASSES_INVULNERABILITY}（{@code /kill}、虚空伤害那一类）不拦：
     * {@link #hurt} 特意给它们留了清场的口子，这里没有理由再堵一道。
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, @NotNull DamageSource source) {
        boolean hurt = super.causeFallDamage(fallDistance, multiplier, source);
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && hurt;
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
     * 佩戴星之魔女法帽与当前形态对应的武器。这些装备提供外观与属性，但不会掉落。
     */
    private void equipStarMajoGear() {
        this.setItemSlot(EquipmentSlot.HEAD, cosmeticEquipment(IronsSpellbooksCompatItems.STAR_WITCH_HAT.get()));
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
        if (this.isSeated()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return;
        }
        Item weapon = phaseTwo
                      ? IronsSpellbooksCompatItems.STAR_SHADOW_LONGSWORD.get()
                      : IronsSpellbooksCompatItems.STAR_SHADOW_STAFF.get();
        this.setItemSlot(EquipmentSlot.MAINHAND, cosmeticEquipment(weapon));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static ItemStack cosmeticEquipment(Item item) {
        ItemStack stack = new ItemStack(item);
        // Boss attributes already include the designed armor and melee damage; equipment only selects its appearance.
        stack.getOrCreateTag().put("AttributeModifiers", new net.minecraft.nbt.ListTag());
        return stack;
    }

    /** 独立投枪动作，不与剑牢法术共用弹体。 */
    public void startSpearThrow(LivingEntity target) {
        this.cancelCast();
        this.beginAction(WinefoxAction.SPEAR_THROW);
        this.spearPending = true;
        this.spearTarget = target;
    }

    /**
     * 甩枪那一下还没做完，别的 AI 决策一律让路，见 {@link #isBusyCombatAction}。
     */
    private boolean isThrowingSpear() {
        return this.currentAction() == WinefoxAction.SPEAR_THROW
            && this.tickCount - this.actionStartTick < WinefoxAction.SPEAR_THROW.durationTicks();
    }

    /**
     * 中途出事（转阶段 / 战败 / 脱战）就作废：动作被打断了，枪就当没投出去。
     */
    void cancelSpearThrow() {
        this.spearPending = false;
        this.spearTarget = null;
        if (this.currentAction() == WinefoxAction.SPEAR_THROW) {
            this.clearAction();
        }
    }

    /**
     * 数到甩出去那一帧，把剑放出来。
     *
     * <p>放在实体这边而不是战斗 Goal 里：Goal 的 {@code tick()} 在目标没了时第一行就返回，
     * 计时会卡住，剑永远不落。
     */
    private void tickSpearThrow() {
        if (!this.spearPending || this.tickCount - this.actionStartTick < SPEAR_RELEASE_TICKS) {
            return;
        }
        this.spearPending = false;
        LivingEntity target = this.spearTarget;
        this.spearTarget = null;
        if (target == null || !target.isAlive() || target.level() != this.level()) {
            return;
        }
        StarShadowSpearEntity spear = new StarShadowSpearEntity(this.level(), this,
            new ItemStack(MaidSpellItems.STAR_SHADOW_SPEAR.get()));
        spear.setBossProjectile();
        Vec3 direction = target.getBoundingBox().getCenter().subtract(spear.position());
        spear.shoot(direction.x, direction.y, direction.z, 3.5F, 0.0F);
        this.level().addFreshEntity(spear);
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
        this.tickActionSounds();
        this.tickChallengeStart();
        this.tickGreeting();
        this.tickRetiredMaids();
        this.tickSeatedAnchor();
        this.tickReturnHome();
        // 行礼那段和坐着一样是 immobile，正对目标的逻辑在下面（要 getTarget() 非空）够不着，
        // 所以单独在这儿补一次朝向，见 tickCurtsyLook。
        this.tickCurtsyLook();
        this.entityData.set(BATTLE_MUSIC, this.isAlive() && this.isBattleActive());
        if (this.isDefeated()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            // 目标丢了才接管垂直速度，见 tickDescent。挂在这儿而不是 customServerAiStep：
            // 那一条只在有目标时才会把 NoGravity 关掉，而我们要接手的恰恰是关掉之后那一段。
            this.tickDescent();
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
        this.entityData.define(CURTSYING, false);
        this.entityData.define(ACTION, WinefoxAction.NONE.id());
        this.entityData.define(ACTION_SERIAL, 0);
        this.entityData.define(PHASE_TWO, false);
        this.entityData.define(TRANSITIONING, false);
        // 默认坐着：她是被邀战才起身的，不是刷出来就打。
        this.entityData.define(SEATED, true);
        this.entityData.define(RESTRICTED, false);
        this.entityData.define(BATTLE_MUSIC, false);
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
        // 血量 <= 1 的挑战参与者不再被选为目标：玩家已经失败、女仆已经退场，
        // 不必再追着打。普通生物战斗不受这个血量限制。
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class,
            10, true, false, this::isViableTarget));
        // 玩家挑战允许玩家所属女仆作为第二个战斗参与者。
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, EntityMaid.class,
            10, true, false, candidate -> this.challengerId != null
                && this.isViableTarget(candidate)));
        // 非玩家生物战斗不该只会挨打还手。起身后，在当前目标阵亡或脱离时，
        // 这条会继续从附近的普通生物中选出可打对象；正式玩家挑战仍由上面两条收口。
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class,
            10, true, false, candidate -> !(candidate instanceof Player)
                && candidate != this
                && !MaidSpellAllyResolver.areFriendly(this, candidate)
                && this.isViableTarget(candidate)));
        this.targetSelector.addGoal(4, new HurtByTargetGoal(this));
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
     * <p>玩家挑战把人打到 1 点血就算赢了（见 {@link WinefoxNonLethalGuard}），再追着打只会
     * 变成一个永远打不死人的骚扰循环。所以正式玩家挑战里的濒死玩家直接从目标池里排除。
     * 普通生物不走这条锁血规则，必须允许酒狐把它们正常打死。
     *
     * <p>坐着的时候一律返回 false。这一条挡在<b>目标选择器的谓词</b>上，
     * 比在 {@code customServerAiStep} 里每 tick 清目标可靠——那边清掉之后，
     * 同一 tick 里 {@code NearestAttackableTargetGoal} 还能立刻再选一个回来。
     */
    boolean isViableTarget(@Nullable LivingEntity candidate) {
        if (this.isSeated() || this.isDefeated() || this.returnHomeTicks > 0 || this.challengeStartTicks > 0) {
            return false;
        }
        if (candidate == null || !candidate.isAlive()) {
            return false;
        }
        // 已经劝退坐下的女仆永远不再是目标。
        //
        // <p>她身上多半挂着再生一类的回血效果：{@code maintainRetiredMaid} 每 tick 把血钉回 1 点，
        // 但那是酒狐自己的 tick —— 中间只要被回上去一次，挑战参与者的「血量高于 1 点」那一关就过了，
        // 目标选择器会把她重新锁回来，玩家看到的就是「都坐下了还在打」。
        // 判定放在这里而不是只靠 {@code retireMaidFromChallenge} 里那一次 {@code setTarget(null)}：
        // 那条只清得掉「当前这一个目标」，拦不住下一次重新索敌。
        if (candidate instanceof EntityMaid maid && isRetiredMaid(maid)) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (this.challengerId != null) {
            // 正式挑战只约束玩家与女仆的邀战参与资格。其他生物若主动介入，
            // 仍按普通目标处理，酒狐需要能正常反击、锁定并击杀它们。
            if (candidate instanceof Player || candidate instanceof EntityMaid) {
                if (!this.isChallengeParticipant(candidate)) {
                    return false;
                }
                return candidate.getHealth() > SURVIVAL_HEALTH_FLOOR;
            }
            return true;
        }
        // 没有 challengerId 时是普通生物战斗：女仆不是合法目标。
        // 她们只有在正式挑战里才下场，而那条路上的致命伤由
        // {@link WinefoxNonLethalGuard} 收在 1 点血；反过来让她们落进普通生物战斗，
        // 就是替主人先出手的女仆被打死的那条路（见 {@link #beginMobCombat}）。
        if (candidate instanceof EntityMaid) {
            return false;
        }
        // 普通生物不把 1 点血当成“已打服”，否则 canAttack/releaseSubduedTarget
        // 会在最后一击前把它们放掉。
        return true;
    }

    /**
     * 非玩家生物先动手时，将坐在秋千上的她切进普通战斗，而不是走玩家的邀战契约。
     * 攻击者不享受锁血；星之魔女自身仍保留 1 点血并走战败归位演出。
     *
     * <p>玩家的女仆生态走另一套规则（见 {@link #beginMaidOwnerCombat}）：坐姿待机时
     * 女仆的挑衅既不叫醒她、也不掉血 —— 待机只认玩家递来的星芒短剑；
     * 已经打起来时则把主人补记成挑战者，女仆才拿得到那 1 点血的地板。
     *
     * @return 这一击能不能真的落到她身上
     */
    boolean beginMobCombat(LivingEntity attacker) {
        // 另一个星之魔女必须挡在这儿：她自己的 setTarget 也会再发一次
        // LivingChangeTargetEvent，两个个体互相把对方设成目标就成了无限的
        // setTarget 递归 —— beginMobCombat → setTarget → 事件 → beginMobCombat，
        // 最后是 StackOverflowError（而事件总线的错误日志又会把它盖成别的报错）。
        // 挡的是"被事件叫起来"这条入口；她要打另一个星之魔女，走的是自己的
        // HurtByTargetGoal / 目标选择器，那条路不经过这里。
        if (this.level().isClientSide || attacker == this || !attacker.isAlive()
            || attacker instanceof MagicalWinefoxBossEntity
            || attacker instanceof Player || MaidSpellAllyResolver.areFriendly(this, attacker)
            || this.isDefeated() || this.returnHomeTicks > 0) {
            return false;
        }
        if (MaidSpellAllyResolver.isOwnedBy(attacker, EntityMaid.class)) {
            // 主人取不到（离线之类）时连这一击都不算数：普通生物战斗是留给普通生物的，
            // 女仆不论如何都不该被拖进去。
            Player maidOwner = MaidSpellAllyResolver.maidOwningPlayer(attacker);
            return maidOwner != null && this.beginMaidOwnerCombat(maidOwner, attacker);
        }
        if (this.isSeated()) {
            this.resetBattleTally();
            this.challengeStartTicks = 0;
            this.entityData.set(SEATED, false);
            this.equipPhaseWeapon(this.isPhaseTwo());
            this.bossEvent.setVisible(true);
        }
        if (this.isViableTarget(attacker)) {
            this.setTarget(attacker);
            return true;
        }
        return false;
    }

    /**
     * 玩家的女仆生态先动手时的处理。
     *
     * <p><b>坐姿待机（含邀战倒计时）时一律不生效</b>：她只认玩家递来的星芒短剑，
     * 女仆自作主张不该把她叫起来，也不该从她这儿吃到伤害。女仆那侧的索敌在
     * {@code LivingChangeTargetEvent} 上就拦掉了（见 {@link WinefoxNonLethalGuard}），
     * 这里兜的是绕过索敌的伤害 —— 范围法术、召唤物、弹体。
     *
     * <p>已经站着说明这一场是别的生物开的（普通生物战斗）。女仆不该被卷进去，
     * 所以把主人补记成挑战者：她随即是挑战参与者，吃得到那 1 点血的地板，
     * 不会被当普通生物打死。主人够不着（超过 {@link #CHALLENGER_MAX_DISTANCE} 格、
     * 已经倒下、旁观）时这一击同样不生效。
     *
     * @return 这一击能不能真的落到她身上
     */
    private boolean beginMaidOwnerCombat(Player owner, LivingEntity attacker) {
        if (this.isSeated()) {
            return false;
        }
        // 已经坐下的女仆不该再和酒狐有任何交手记录。
        //
        // <p>她是被劝退退场的：这一场里她打不动了（{@link #isViableTarget} 那条守卫），
        // 酒狐也不该再把她当目标。所以这一击直接不算数 —— 让伤害落地只会记上一笔新的
        // 仇恨，而 {@code clearMaidCombatState} 每 tick 都在清她那一侧的记忆，
        // 两边来回拉扯的结果就是「人坐着，架还在打」。
        if (attacker instanceof EntityMaid maid && isRetiredMaid(maid)) {
            return false;
        }
        if (this.challengerId == null) {
            if (!this.canStartChallengeFrom(owner)) {
                return false;
            }
            this.adoptChallengeFrom(owner);
        }
        if (this.isViableTarget(attacker)) {
            this.setTarget(attacker);
        }
        return true;
    }

    /**
     * 主人挑不挑得起这一场：与 {@link #tickChallengeStart} 的收口同一套条件，
     * 不然倒计时结束时她还是会因为主人不在场而原地坐回去。
     *
     * <p>创造模式与递短剑那条路一致（见 {@link #mobInteract}）：他打不到 1 点血以下，
     * 但女仆的锁血不该因此失效。
     */
    private boolean canStartChallengeFrom(@Nullable Player owner) {
        return owner != null && owner.level() == this.level()
            && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
            && (owner.isCreative() || owner.getHealth() > SURVIVAL_HEALTH_FLOOR)
            && this.distanceToSqr(owner) <= CHALLENGER_MAX_DISTANCE * CHALLENGER_MAX_DISTANCE;
    }

    /**
     * 战斗已经开场之后补记挑战者：不重放邀战开场，只补归属。
     *
     * <p>血条、战斗音乐与目标都已经在战斗状态里，缺的只是「这一场算谁的」——
     * 而女仆的 1 点血保护认的正是这个归属。记账一并归零，这一场从现在起算主人的。
     */
    private void adoptChallengeFrom(Player owner) {
        this.challengerId = owner.getUUID();
        this.hasStartedChallenge = true;
        this.resetBattleTally();
        this.entityData.set(RESTRICTED, false);
        this.entityData.set(BATTLE_MUSIC, true);
        this.bossEvent.setVisible(true);
        // 限制标志刚被清零，旧的交易表作废；打起来了也不该还开着交易界面。
        this.offers = null;
        this.setTradingPlayer(null);
    }

    /**
     * 当前目标已经濒死就松手，交给 {@code targetSelector} 另选一个。
     */
    private void releaseSubduedTarget() {
        LivingEntity target = this.getTarget();
        if (target == null || this.isViableTarget(target)) {
            return;
        }
        // 「你输了」这一句得说给玩家本人，不能只靠她那两句场面话 ——
        // 广播是给围观的人听的，当事人需要一条明确的结论。
        // 松手之后 prioritizePlayerTarget 不会再把濒死的他锁回来，所以这里只会发一次。
        if (this.challengerId != null && target instanceof Player player
            && player.getHealth() <= SURVIVAL_HEALTH_FLOOR) {
            player.displayClientMessage(Component.translatable(
                "entity.touhou_little_maid_spell.stellar_witch.challenge_lost")
                .withStyle(ChatFormatting.RED), false);
        }
        this.setTarget(null);
    }

    private void beginAction(WinefoxAction action) {
        this.entityData.set(ACTION, action.id());
        this.entityData.set(ACTION_SERIAL, this.entityData.get(ACTION_SERIAL) + 1);
        this.actionStartTick = this.tickCount;
    }

    private void tickActionSounds() {
        int elapsed = this.tickCount - this.actionStartTick;
        for (WinefoxAction.Event event : this.currentAction().events()) {
            if (event.kind() == WinefoxAction.EventKind.SOUND && elapsed == event.tick()) {
                SoundEvent sound = com.github.yimeng261.maidspell.sound.MaidSpellSounds.getWinefoxSound(event.sound());
                if (sound != null) this.playSound(sound, 1.0F, 1.0F);
            }
        }
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

    boolean teleportToward(LivingEntity target) {
        Vec3 away = this.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 destination = target.position().add(away.scale(2.5D));
        AABB box = this.getBoundingBox().move(destination.subtract(this.position()));
        if (!this.level().getWorldBorder().isWithinBounds(box)
            || !this.level().noCollision(this, box)) {
            return false;
        }
        this.teleportTo(destination.x, destination.y, destination.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFallDistance();
        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.0F);
        return true;
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
        return this.isDefeated() || this.isSeated() || this.returnHomeTicks > 0 || super.isImmobile();
    }

    /** 坐姿时接受星芒短剑邀战、日记兑换和普通交易。 */
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        // 一交互就把自言自语推后 60 秒：她每条交互都会回一句（闲谈、交易、日记、邀战都算），
        // 而自言自语是直发的、不排队，不推后就会紧贴着刚回的那句冒出来。
        // 判在方法最前面，是为了连"这次没说话"的交互也算数 —— 宁可让她晚点念叨，
        // 也不要让人刚点完她就听见她自言自语。
        this.resetAmbientDialogueCooldown();
        if (!this.isSeated() || this.isDefeated()) {
            return super.mobInteract(player, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        if (this.challengeStartTicks > 0) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (held.is(MaidSpellItems.STARGLINT_DAGGER.get())) {
            if (!this.level().isClientSide && !player.isSpectator()
                && (player.isCreative() || player.getHealth() > SURVIVAL_HEALTH_FLOOR)) {
                this.acceptChallenge(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (WinefoxTrades.isTravelDiary(held)) {
            if (!this.level().isClientSide) {
                WinefoxTrades.exchangeDiaries(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // 手持绿宝石时打开交易界面，并播放固定的交易台词。
        if (held.is(Items.EMERALD) && this.getTradingPlayer() == null) {
            if (this.level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            this.startTrading(player);
            WinefoxDialogue.sendToPlayer(player, WinefoxDialogue.tradeLine());
            return InteractionResult.CONSUME;
        }
        if (!this.level().isClientSide) {
            Component line;
            if (player.getUUID().equals(this.postVictoryChatPlayerId)) {
                line = WinefoxDialogue.postVictoryChatLine();
                this.postVictoryChatPlayerId = null;
            } else {
                line = WinefoxDialogue.randomChatLine(this.hasNearbyOwnedMaid(player));
            }
            WinefoxDialogue.sendToPlayer(player, line);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ==================== 战败之后的交易 ====================

    /**
     * 打开交易界面。
     *
     * <p>{@link Merchant} 本身带了一份默认的 {@code openTradingScreen}，这里没直接用，
     * 只为多一句：菜单没开起来时把交易对象撤回来。默认实现开不起来就直接返回，
     * 而 {@code tradingPlayer} 只在 {@code MerchantMenu.removed()} 里清 ——
     * 菜单压根没开过，那个方法就永远不会跑，之后再也点不开交易。
     * 除这一句外与默认实现逐行一致。
     */
    private void startTrading(Player player) {
        this.setTradingPlayer(player);
        OptionalInt containerId = player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> new MerchantMenu(id, inventory, this), this.getDisplayName()));
        if (containerId.isEmpty()) {
            // 菜单没开起来（别的模组拦了、玩家手上已经开着别的界面），
            // 交易对象就得撤回来。它只在 MerchantMenu.removed() 里清，
            // 而那个方法要菜单真的开过才会跑；留着的话 mobInteract 里
            // 「getTradingPlayer() == null」永远不成立，之后再也点不开交易。
            this.setTradingPlayer(null);
            return;
        }
        MerchantOffers current = this.getOffers();
        if (!current.isEmpty()) {
            player.sendMerchantOffers(containerId.getAsInt(), current, 1,
                    this.getVillagerXp(), this.showProgressBar(), this.canRestock());
        }
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    /**
     * 交易表按当前的限制标志现算。
     *
     * <p>算一次就存在字段里，两个改变限制标志的时刻（{@link #acceptChallenge} 开打、
     * {@link #beginDefeat} 定案）各自把它置空，下次打开时按新标志重算。
     * 不每次现算是因为 {@link MerchantOffers} 是有状态的：
     * {@code MerchantOffer} 自己记着用了多少次，每次交互换一张新表等于把交易次数抹掉。
     */
    @Override
    public @NotNull MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = WinefoxTrades.build(this.tradingUnlocked);
        }
        return this.offers;
    }

    @Override
    public void overrideOffers(@NotNull MerchantOffers newOffers) {
        this.offers = newOffers;
    }

    @Override
    public void notifyTrade(@NotNull MerchantOffer offer) {
        offer.increaseUses();
        this.playSound(this.getNotifyTradeSound(), 1.0F, 1.0F);
    }

    @Override
    public void notifyTradeUpdated(@NotNull ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        // 她没有村民那套等级，进度条只会是一根永远空着的槽。
        return false;
    }

    @Override
    public @NotNull SoundEvent getNotifyTradeSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    /**
     * 接受挑战：起身、亮血条、放开场白、锁定挑战者。
     */
    private void acceptChallenge(Player challenger) {
        this.speakDialogue(WinefoxDialogue.challengeAccepted(!this.hasStartedChallenge));
        this.hasStartedChallenge = true;
        this.resetBattleTally();
        this.entityData.set(RESTRICTED, false);
        this.entityData.set(CURTSYING, false);
        // 限制标志刚被清零，旧表跟着作废；打起来了也不该还开着交易界面。
        this.offers = null;
        this.setTradingPlayer(null);
        this.challengerId = challenger.getUUID();
        this.challengeStartTicks = 60;
        this.level().playSound(null, this.blockPosition(),
            SoundEvents.BEACON_ACTIVATE, this.getSoundSource(), 1.0F, 1.2F);
    }

    private void tickChallengeStart() {
        if (this.challengeStartTicks <= 0 || --this.challengeStartTicks > 0) {
            return;
        }
        Player challenger = this.challengerId == null ? null : this.level().getPlayerByUUID(this.challengerId);
        if (challenger == null || !challenger.isAlive() || challenger.isSpectator()
            || this.distanceToSqr(challenger) > CHALLENGER_MAX_DISTANCE * CHALLENGER_MAX_DISTANCE
            || challenger.getHealth() <= SURVIVAL_HEALTH_FLOOR) {
            this.challengerId = null;
            return;
        }
        this.entityData.set(SEATED, false);
        this.equipPhaseWeapon(false);
        this.bossEvent.setVisible(true);
        this.entityData.set(BATTLE_MUSIC, true);
        this.setTarget(challenger);
    }

    /**
     * 把"自言自语"的计时重新拉满。
     *
     * <p>她的话是一句句冒出来的：自言自语走 {@code sendToNearby} 直发，不排队，
     * 只看 {@code ambientDialogueCooldown} 数到 0 没有。要是别的台词不碰这份计时，
     * 就会撞上"聊天栏里刚冒出一句，紧接着又是一句自言自语"的场面。
     * 所以除了自言自语本身，任何一句别的台词（交互、开场白、转阶段、战败……）
     * 说出口时都要调这里，把 60 秒从头数起。
     */
    private void resetAmbientDialogueCooldown() {
        if (!this.level().isClientSide) {
            this.ambientDialogueCooldown = AMBIENT_DIALOGUE_INTERVAL_TICKS;
        }
    }

    /** 排入一组台词，并让自言自语重新计时，见 {@link #resetAmbientDialogueCooldown()}。 */
    private void speakDialogue(List<Component> lines) {
        this.resetAmbientDialogueCooldown();
        this.dialogue.speak(lines);
    }

    private void tickGreeting() {
        if (!this.isSeated() || this.challengeStartTicks > 0) {
            // 这里只"停表"，不清零：战斗里也会说话（转阶段、战败、女仆退场），
            // 那些话同样把自言自语推后了 60 秒；清零等于她一回秋千坐下就又能自言自语，
            // 正好是"紧挨着上一句"。真正该清零的是下面"附近没人"那一条。
            return;
        }

        boolean hasNearbyPlayer = false;
        boolean startedGreeting = false;
        for (Player player : this.level().players()) {
            if (player.isSpectator()) {
                continue;
            }
            double distanceSqr = this.distanceToSqr(player);
            if (distanceSqr <= AMBIENT_DIALOGUE_RANGE * AMBIENT_DIALOGUE_RANGE) {
                hasNearbyPlayer = true;
            }
            if (!startedGreeting && distanceSqr <= GREETING_TRIGGER_RANGE * GREETING_TRIGGER_RANGE
                && !this.greetedPlayers.contains(player.getUUID())
                && this.dialogue.greet(this, player)) {
                this.greetedPlayers.add(player.getUUID());
                // 开场白也是"别的台词"，同样把自言自语推后 60 秒。
                this.resetAmbientDialogueCooldown();
                startedGreeting = true;
            }
        }

        if (!hasNearbyPlayer) {
            this.ambientDialogueCooldown = 0;
        } else if (!startedGreeting) {
            if (this.ambientDialogueCooldown > 0) {
                --this.ambientDialogueCooldown;
            } else if (!this.dialogue.isSpeaking()) {
                WinefoxDialogue.sendToNearby(this, WinefoxDialogue.randomAmbientLine(), AMBIENT_DIALOGUE_RANGE);
                this.ambientDialogueCooldown = AMBIENT_DIALOGUE_INTERVAL_TICKS;
            }
        }
    }

    /**
     * 战败之后推不动。
     *
     * <p>{@link #isImmobile} 只掐掉 AI，管不着碰撞推挤 —— 那是另一条路：别人的
     * {@code LivingEntity.pushEntities} 用 {@code EntitySelector.pushableBy} 收集周围实体， 而那个谓词问的是**被推者**的 {@code isPushable()}。所以不覆写这一个开关的话， 玩家能把躺在地上、AI 全停的她一路顶着走。
     */
    @Override
    public boolean isPushable() {
        return !this.isDefeated() && !this.isSeated() && super.isPushable();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // 坐着的情况不在这儿处理：isImmobile() 为真时整条 serverAiStep 都不跑，
        // 写在这里的分支永远到不了。那一段归 tickSeatedAnchor。
        if (this.returnHomeTicks > 0) {
            // 已经宣布收场、正在回秋千的路上，这 60t 里不许再锁新目标：
            // 锁了也会在 tickReturnHome 那一刻被回满血抹掉，等于白打一场。
            this.setTarget(null);
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
            this.tickSpearThrow();
        }

        LivingEntity target = this.getTarget();
        boolean combatFlight = (target != null && target.isAlive()) || this.isBusyCombatAction();
        this.setNoGravity(combatFlight);
        if (combatFlight) {
            this.resetFallDistance();
        }
    }

    /** 每场战斗只触发一次半血转阶段。 */
    private void tickPhaseThresholds() {
        if (this.isTransitioning()) {
            return;
        }
        float healthFraction = this.getHealth() / this.getMaxHealth();
        if (!this.isPhaseTwo() && healthFraction <= PHASE_TWO_HEALTH_FRACTION) {
            this.startPhaseTransition(true);
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
        this.cancelSpearThrow();
        this.phaseTransitionTicks = PHASE_TRANSITION_TICKS;
        this.phaseTransitionKnockbackReleased = false;
        this.phaseTransitionWeaponSwapped = false;
        this.phaseTransitionTarget = toPhaseTwo;
        this.entityData.set(TRANSITIONING, true);
        // Ordinary mob fights use normal damage/death and stay silent.  The
        // phase line belongs to a player-facing duel only.
        if (this.isPlayerCombatActive()) {
            this.speakDialogue(WinefoxDialogue.phaseTransition());
        }
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
            entity -> entity.isAlive() && !entity.isSpectator()
                && entity.distanceToSqr(this) <= TRANSITION_KNOCKBACK_RADIUS * TRANSITION_KNOCKBACK_RADIUS)) {
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
        if (currentTarget != null && this.isViableTarget(currentTarget)) {
            // During ordinary mob combat, respect an already valid non-player
            // target instead of replacing it with a nearby player every 10 ticks.
            return;
        }
        // 直接扫 level().players()，不走 getEntitiesOfClass：后者要把 48 格立方体覆盖到的
        // 三百来个 entity section 全走一遍，还得为结果和 stream 各分配一次，
        // 而在线玩家本来就是一张很短的表。
        double rangeSqr = Mth.square(this.getAttributeValue(Attributes.FOLLOW_RANGE));
        Player nearestPlayer = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (Player player : this.level().players()) {
            if (!this.isViableTarget(player)) {
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

    /**
     * 「还能不能打」的唯一出口，{@code targetSelector} 那两条目标 goal 都得过这一关。
     *
     * <p>{@code NearestAttackableTargetGoal} 收的是我们自己传的 {@code isViableTarget}，
     * 但 {@code HurtByTargetGoal} 用的是它自带的 {@code HURT_BY_TARGETING}，够不着那个谓词。
     * 而 {@code TargetingConditions.test} 在 {@code isCombat} 时会问一句 {@code canAttack} ——
     * 覆在这儿两条就都盖住了。
     *
     * <p>不盖的话，被打到 1 点血的玩家只要继续挥刀就能一直把她拉回来：
     * {@code tickBattleOver} 的空目标计数永远清零，她既不收场也不回秋千。
     */
    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        return super.canAttack(target) && this.isViableTarget(target);
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
        if (this.isDefeated() || this.returnHomeTicks > 0) {
            return false;
        }
        // 玩家仍须用星芒短剑邀战；非玩家生物的攻击则唤醒普通生物战斗。
        LivingEntity attacker = mobAttacker(source);
        if (this.isSeated()) {
            if (attacker == null || !this.beginMobCombat(attacker)) {
                return false;
            }
        } else if (attacker != null) {
            // 直写 hurt 的法术/伤害不会经过 LivingChangeTargetEvent；正式挑战已经开场时，
            // 也要让这类非玩家攻击者立即成为普通战斗目标。
            this.beginMobCombat(attacker);
        }
        boolean playerDuelDamage = this.isPlayerDuelDamage(source);
        if (!playerDuelDamage) {
            boolean hurt = super.hurt(source, amount);
            this.beginDefeatIfSubdued(source);
            return hurt;
        }
        // 算一次带着走：判归属要顺 owner 链上溯，一次受击问两遍是白跑一趟。
        boolean maidDamage = isMaidDamage(source);
        float adjustedAmount = amount;
        if (maidDamage) {
            adjustedAmount *= 0.5F;
        }
        if (this.isPhaseTwo()) {
            adjustedAmount *= 0.5F;
        }

        float healthBefore = this.getHealth();
        boolean hurt = super.hurt(source, adjustedAmount);
        this.recordDamageShare(maidDamage, healthBefore - this.getHealth());
        this.beginDefeatIfSubdued(source);
        return hurt;
    }

    /** The boss always keeps one health for her defeat sequence; only challengers receive the same protection. */
    private void beginDefeatIfSubdued(DamageSource source) {
        if (this.getHealth() <= SURVIVAL_HEALTH_FLOOR && !this.level().isClientSide) {
            this.beginDefeat(source);
        }
    }

    /** Resolve a non-player living attacker from direct, projectile, or summon damage. */
    @Nullable
    private static LivingEntity mobAttacker(DamageSource source) {
        for (Entity cause : new Entity[]{source.getEntity(), source.getDirectEntity()}) {
            if (cause instanceof LivingEntity living && !(living instanceof Player)) {
                return living;
            }
            Entity responsible = MaidSpellAllyResolver.resolveResponsibleEntity(cause).orElse(null);
            if (responsible instanceof LivingEntity living && !(living instanceof Player)) {
                return living;
            }
        }
        return null;
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
    private void recordDamageShare(boolean maidDamage, float dealt) {
        if (dealt <= 0.0F || this.level().isClientSide) {
            return;
        }
        this.totalDamageTaken += dealt;
        if (maidDamage) {
            this.maidDamageTaken += dealt;
        }
    }

    /**
     * 真伤改道：不许直写血量，一律折回 {@link #hurt}。
     *
     * <p>不改道的话，真伤会一次性绕过女仆减伤、二阶段减伤、转阶段的 120t 无敌、
     * 1 点血地板，还会让血直接归零走原版死亡 —— 战败演出、血条收起、战利品判定全部跳过。
     *
     * <p>顺带把"用过真伤"这一位记下来：这是判「女仆代打」的两个触发条件之一。
     * 记在这儿而不是在饰品那边，是因为这里能看到<b>所有</b>真伤来源，
     * 包括以后新加的饰品和调试指令。
     */
    @Override
    public boolean maidspell$redirectTrueDamage(float amount, @Nullable LivingEntity attacker) {
        if (this.level().isClientSide || amount <= 0.0F) {
            return false;
        }
        DamageSource source = attacker != null
                              ? this.damageSources().mobAttack(attacker)
                              : this.damageSources().magic();
        boolean previousTrueDamageUsed = this.trueDamageUsed;
        if (!this.isSeated() && !this.isDefeated() && !this.isInvulnerableTo(source)) {
            this.trueDamageUsed = true;
        }
        boolean hurt = this.hurt(source, amount);
        // 记在 hurt 之后：坐着、战败、转场无敌这三种情况下这一击是整个被丢掉的，
        // 一点伤害都没造成。在前面记的话，一次打空的真伤也会把这一场判成受限，
        // 玩家白白丢掉星云核心和两条特殊交易。
        if (!hurt) {
            this.trueDamageUsed = previousTrueDamageUsed;
        }
        return hurt;
    }

    @Override
    public void maidspell$onTrueDamageQueued() {
        if (this.challengerId != null && this.isBattleActive()
            && !this.isTransitioning() && !this.isInvulnerable()) {
            this.trueDamageUsed = true;
        }
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
        if (this.isBattleActive() && !bypassesSurvival(source)
            && this.getHealth() < SURVIVAL_HEALTH_FLOOR) {
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
        // 奖励、交易解锁与胜利台词只归正式挑战中由挑战者（或其女仆）完成的击败。
        // 其他生物可以让她进入同一套战败演出与归位流程，但不能借此刷挑战奖励。
        boolean playerVictory = this.isPlayerDuelDamage(source);
        if (playerVictory && this.challengerId != null) {
            this.postVictoryChatPlayerId = this.challengerId;
        }
        this.entityData.set(DEFEATED, true);
        this.entityData.set(BATTLE_MUSIC, false);
        this.clearDuelHazards();
        // 她倒下了，场上的召唤物没有理由继续打。
        this.recallSummons();
        this.cancelCast();
        this.cancelSpearThrow();
        this.clearAction();
        this.dropWeaponForDefeat();
        this.entityData.set(TRANSITIONING, false);
        this.phaseTransitionTicks = 0;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFlightControl();
        this.setNoGravity(false);
        this.setInvulnerable(true);
        this.setTarget(null);
        this.clearMaidAggro();
        // 打完了，血条收掉；她本人留在场上倒着。
        this.bossEvent.setVisible(false);
        if (playerVictory) {
            // 战败那一刻把这一场的归属结算下来，之后回秋千、发战利品都读它。
            this.entityData.set(RESTRICTED, this.computeRestricted());
            this.dropDefeatRewards(source);
            // 输过一次就开放交易；限制标志刚刚才定下来，旧的交易表作废，下次打开时按新标志现算。
            this.tradingUnlocked |= !this.isRestricted();
            this.speakDialogue(WinefoxDialogue.victory(this.isRestricted(),
                this.trueDamageUsed && Config.winefoxTrueDamageRestrictsReward));
        }
        this.offers = null;
        this.returnHomeTicks = DEFEAT_RETURN_HOME_TICKS;
    }

    /**
     * 发战败奖励。
     *
     * <p><b>必须自己调 {@code dropFromLootTable}。</b>原版只在 {@code LivingEntity.die()}
     * 里发战利品，而她的血被 {@link #SURVIVAL_HEALTH_FLOOR} 钉在 1、{@code die()} 一次都不会进 ——
     * 光把物品写进 {@code loot_tables/entities/stellar_witch.json} 是发不出来的。
     *
     * <p>星云核心不走战利品表而是直接落地：它是「女仆代打」判定的结算结果，
     * 掉不掉取决于这一场的伤害归属，不是随机项，也不作为入场消耗。
     */
    private void dropDefeatRewards(DamageSource source) {
        this.dropFromLootTable(source, this.lastHurtByPlayerTime > 0);
        if (!this.isRestricted()) {
            this.spawnAtLocation(new ItemStack(MaidSpellItems.NEBULA_CORE.get()));
        }
    }

    /**
     * 坐着的时候把她钉在秋千上。
     *
     * <p>两件事都只能在 {@code aiStep} 这一层做，理由和 {@link #tickReturnHome} 一样：
     * {@code SEATED} 默认就是 true，于是 {@link #isImmobile} 从第一 tick 起就为真，
     * {@code customServerAiStep}（唯一调 {@code setNoGravity} 的地方）根本不跑，
     * 而 {@code travel()} 照跑不误 —— 秋千悬在半空，她第一 tick 就会掉下去。
     *
     * <p>{@code homePos} 同理要在这儿兜一次：它只在 {@code finalizeSpawn} 里赋值，
     * 而结构生成走的是 {@code StructureTemplate.placeEntities}，那条路不调
     * {@code finalizeSpawn}。空着的话战败之后回不了秋千。第一次坐定的位置就是她的家。
     */
    private void tickSeatedAnchor() {
        if (!this.isSeated()) {
            return;
        }
        this.setTarget(null);
        if (this.homePos == null) {
            this.homePos = this.blockPosition();
        }
        double seatedY = this.homePos.getY() + SEATED_HOVER_OFFSET;
        if (Math.abs(this.getY() - seatedY) > 1.0E-4D) {
            this.setPos(this.getX(), seatedY, this.getZ());
        }
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFlightControl();
        this.updateSeatedLookAtPlayer();
        // 血条也归这儿收。customServerAiStep 里那句 setVisible(false) 是够不着的 ——
        // 坐着就 isImmobile，那一整条 serverAiStep 都不跑。而 ServerBossEvent 默认可见，
        // 不收的话，玩家走进刚生成的星途终岸就会看见一条满格的 Boss 血条，
        // 而她还坐在秋千上、这一场根本没开始。
        this.bossEvent.setVisible(false);
    }

    /**
     * 坐在秋千上时 {@link #isImmobile()} 会跳过 Mob 的 LookControl，
     * 所以这里直接把身体和头部朝向最近的附近玩家，保持待机时也会跟随来客。
     */
    private void updateSeatedLookAtPlayer() {
        Player player = null;
        double nearestDistanceSqr = SEATED_LOOK_RANGE * SEATED_LOOK_RANGE;
        for (Player candidate : this.level().players()) {
            if (!candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            double distanceSqr = this.distanceToSqr(candidate);
            if (distanceSqr <= nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                player = candidate;
            }
        }
        if (player == null) {
            return;
        }
        this.turnTowards(player.getEyePosition(), BODY_TURN_DEGREES_PER_TICK, BODY_TURN_DEGREES_PER_TICK * 2.0F);
    }

    /**
     * 行礼（{@code curtsy}）期间把身体和头一起转向玩家。
     *
     * <p>行礼这一段是「玩家挑战失败之后、回秋千之前」，整段 {@link #isImmobile()} 为真，
     * 所以 {@link #aiStep()} 里那段「有目标就正对目标」的代码一行都跑不到 ——
     * {@code finishCombat} 已经把目标清成 null 了。没有这一段，她就是照着自己上一刻的
     * 朝向（通常是最后飞出去的方向）鞠完这一躬，看上去像在对空气行礼。
     *
     * <p>优先找挑战者本人：这段演出本来就是放给他看的。挑战者离线、旁观或者跑出
     * {@link #CURTSY_LOOK_RANGE} 时才退到最近的玩家。
     */
    private void tickCurtsyLook() {
        if (!this.isCurtsying()) {
            return;
        }
        Player player = this.challengerId == null ? null : this.level().getPlayerByUUID(this.challengerId);
        if (player == null || !player.isAlive() || player.isSpectator()
            || this.distanceToSqr(player) > CURTSY_LOOK_RANGE * CURTSY_LOOK_RANGE) {
            player = null;
            double nearestDistanceSqr = CURTSY_LOOK_RANGE * CURTSY_LOOK_RANGE;
            for (Player candidate : this.level().players()) {
                if (!candidate.isAlive() || candidate.isSpectator()) {
                    continue;
                }
                double distanceSqr = this.distanceToSqr(candidate);
                if (distanceSqr <= nearestDistanceSqr) {
                    nearestDistanceSqr = distanceSqr;
                    player = candidate;
                }
            }
        }
        if (player == null) {
            return;
        }
        this.turnTowards(player.getEyePosition(), BODY_TURN_DEGREES_PER_TICK, BODY_TURN_DEGREES_PER_TICK * 2.0F);
    }

    /**
     * 把身体、头和视线一起转向某个点。
     *
     * <p>坐着待机和行礼两条路要的是同一个动作，差别只在「找谁」和「找多远」，
     * 所以角度那一段留在这里。头转得比身体快一倍：身体是转身，头是先看过去。
     */
    private void turnTowards(Vec3 target, float bodyStep, float headStep) {
        Vec3 origin = this.getEyePosition();
        double dx = target.x - origin.x;
        double dy = target.y - origin.y;
        double dz = target.z - origin.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < 1.0E-4D) {
            return;
        }

        float wantedYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float wantedPitch = (float) (-(Mth.atan2(dy, horizontalDistance) * Mth.RAD_TO_DEG));
        this.yBodyRot = Mth.approachDegrees(this.yBodyRot, wantedYaw, bodyStep);
        this.setYRot(this.yBodyRot);
        this.yHeadRot = Mth.approachDegrees(this.yHeadRot, wantedYaw, headStep);
        this.setYHeadRot(this.yHeadRot);
        this.setXRot(Mth.approachDegrees(this.getXRot(), wantedPitch, bodyStep));
    }

    /**
     * 这一场算不算「女仆代打」。
     *
     * <p>两个触发条件任一成立即判限制：用过真伤，或者女仆打出的伤害占比超过阈值。
     * 前者是因为真伤本身就绕过了她全部的防御机制（见 {@link ITrueDamageRedirect}），
     * 后者是因为这一场要求玩家自己下场，而不是站在后面看女仆刷。
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
            this.teleportTo(home.getX() + 0.5D, home.getY() + SEATED_HOVER_OFFSET, home.getZ() + 0.5D);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFlightControl();
        this.setNoGravity(true);
        if (!this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        this.setInvulnerable(false);
        this.entityData.set(DEFEATED, false);
        this.entityData.set(CURTSYING, false);
        this.entityData.set(SEATED, true);
        this.entityData.set(BATTLE_MUSIC, false);
        this.challengerId = null;
        this.challengeStartTicks = 0;
        this.clearAction();
        this.setTarget(null);
        this.setLastHurtByMob(null);
        this.removeAllEffects();
        // 回满血、退回一阶段，这一场才算真的翻篇。
        //
        // 少了回血这一句就是一个无限刷战利品的口子：战败时她被钉在 1 点血上，
        // 而 hurt() 里那句「血 <= 地板就 beginDefeat」是无条件判的 ——
        // 再递一颗核心，她带着 1 点血站起来，下一击立刻又走一遍战败流程，
        // 战利品表和星云核心跟着再发一次。
        //
        // 阶段标志也一并撤掉：光回血的话，tickPhaseThresholds 会在她起身那一刻
        // 判定「被治疗回血」而立刻起一段退形转场，下一场开场就是 120t 无敌。
        this.setHealth(this.getMaxHealth());
        this.entityData.set(PHASE_TWO, false);
        this.entityData.set(TRANSITIONING, false);
        this.phaseTransitionTicks = 0;
        this.equipStarMajoGear();
        this.resetBattleTally();
        this.releaseRetiredMaids();
    }

    /**
     * 场上连续 {@link #BATTLE_OVER_GRACE_TICKS} tick 没有目标后收场并回秋千。
     *
     * <p>玩家挑战仍会额外检查挑战者是否已被打到 1 点血，并播放“打服”台词；
     * 普通生物战斗只在目标真正消失后静默收场，不使用残血锁血，也不触发玩家挑战台词。
     */
    private void tickBattleOver() {
        if (this.challengerId != null) {
            Player challenger = this.level().getPlayerByUUID(this.challengerId);
            if (challenger != null && challenger.getHealth() <= SURVIVAL_HEALTH_FLOOR) {
                this.endChallengeLost();
                return;
            }
        }
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.noTargetTicks = 0;
            return;
        }
        if (++this.noTargetTicks < BATTLE_OVER_GRACE_TICKS) {
            return;
        }
        if (this.challengerId != null) {
            this.endChallengeLost();
        } else {
            this.endCombatQuietly();
        }
    }

    void endChallengeLost() {
        if (this.challengerId == null) {
            return;
        }
        this.finishCombat(true);
    }

    /** 普通生物战斗结束时复用归位流程，但不清理玩家状态、不播放挑战台词。 */
    private void endCombatQuietly() {
        this.finishCombat(false);
    }

    private void finishCombat(boolean playerChallenge) {
        if (this.isSeated() || this.isDefeated() || this.returnHomeTicks > 0) {
            return;
        }
        this.noTargetTicks = 0;
        if (playerChallenge) {
            this.clearDuelHazards();
            this.speakDialogue(WinefoxDialogue.playerSubdued());
        }
        // 走和战败一样的归位路径，只是不进战败演出、不结算战利品。
        this.cancelCast();
        this.cancelSpearThrow();
        this.clearAction();
        this.recallSummons();
        this.clearMaidAggro();
        this.getNavigation().stop();
        this.bossEvent.setVisible(false);
        this.entityData.set(CURTSYING, playerChallenge);
        this.returnHomeTicks = playerChallenge ? CURTSY_RETURN_HOME_TICKS : DEFEAT_RETURN_HOME_TICKS;
        this.setTarget(null);
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFlightControl();
        this.entityData.set(BATTLE_MUSIC, false);
    }

    boolean isChallenger(Player player) {
        return this.challengerId != null && this.challengerId.equals(player.getUUID());
    }

    /** Whether this encounter is the formal player-facing duel state. */
    private boolean isPlayerCombatActive() {
        return this.challengerId != null;
    }

    /** Formal duel participants are the player who opened it and that player's maid. */
    boolean isChallengeParticipant(LivingEntity candidate) {
        if (this.challengerId == null || candidate == null) {
            return false;
        }
        if (candidate instanceof Player player) {
            return this.challengerId.equals(player.getUUID());
        }
        if (candidate instanceof EntityMaid maid) {
            return this.challengerId.equals(maid.getOwnerUUID());
        }
        return false;
    }

    /** 玩家普通聊天时，附近有自己的女仆就扩充随机台词池。 */
    private boolean hasNearbyOwnedMaid(Player player) {
        return !this.level().getEntitiesOfClass(EntityMaid.class,
            player.getBoundingBox().inflate(10.0D), maid -> maid.isAlive()
                && player.getUUID().equals(maid.getOwnerUUID())).isEmpty();
    }

    /**
     * A maid that reaches the duel floor is withdrawn in place rather than killed. The player
     * may continue the duel, so this deliberately does not call {@link #finishCombat(boolean)}.
     */
    void retireMaidFromChallenge(EntityMaid maid) {
        if (this.level().isClientSide || maid == null || maid.level() != this.level()) {
            return;
        }
        if (this.getTarget() == maid) {
            this.setTarget(null);
        }
        this.clearMaidCombatState(maid);
        maid.setHealth(SURVIVAL_HEALTH_FLOOR);
        maid.clearFire();
        for (MobEffectInstance effect : List.copyOf(maid.getActiveEffects())) {
            if (effect.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                maid.removeEffect(effect.getEffect());
            }
        }
        CompoundTag persistent = maid.getPersistentData();
        boolean firstRetirement = !persistent.getBoolean(RETIRED_MAID_TAG);
        if (firstRetirement) {
            this.speakDialogue(WinefoxDialogue.maidDefeated());
            persistent.putBoolean(RETIRED_MAID_INVULNERABLE_TAG, maid.getIsInvulnerable());
            persistent.putBoolean(RETIRED_MAID_VANILLA_INVULNERABLE_TAG, maid.isInvulnerable());
            persistent.putBoolean(RETIRED_MAID_NO_GRAVITY_TAG, maid.isNoGravity());
            persistent.putBoolean(RETIRED_MAID_SITTING_TAG, maid.isMaidInSittingPose());
            persistent.putUUID(RETIRED_MAID_BOSS_TAG, this.getUUID());
            persistent.putDouble(RETIRED_MAID_X_TAG, maid.getX());
            persistent.putDouble(RETIRED_MAID_Y_TAG, maid.getY());
            persistent.putDouble(RETIRED_MAID_Z_TAG, maid.getZ());
            persistent.putBoolean(RETIRED_MAID_TAG, true);
        }
        this.retiredMaidIds.add(maid.getUUID());
        setRetiredState(maid, true);
        // 工作模式一并切到「空闲」。
        //
        // <p>只坐下是不够的：弓弩与枪械那几套任务（{@code TaskCrossBowAttack}、
        // 枪械模组自己注册的那些）都是从 {@code IRangedAttackTask} 出发的，坐姿并不在它们的
        // 中止条件里 —— 女仆会被钉在原地继续对着酒狐拉弓 / 开枪，玩家看到的就是
        // 「人已经坐下了，箭还在往外飞」。
        //
        // <p>回到空闲之后 {@code createBrainTasks} 会在下一次 {@code refreshBrain} 里换成
        // 空闲那一套（只剩雪球戏耍），战斗行为连同索敌一起停摆。原来选的是什么模式不记：
        // 这一场结束 {@code restoreRetiredMaid} 会把她放开，让主人自己重新指派 ——
        // 记下来反而会在主人中途改过任务之后把旧模式顶回去。
        this.setIdleWorkMode(maid);
        maid.setEntityInvulnerable(true);
        maid.setInvulnerable(true);
        maid.setInSittingPose(true);
        maid.getNavigation().stop();
        maid.setDeltaMovement(Vec3.ZERO);
        maid.resetFallDistance();
        maid.setNoGravity(true);
    }

    /** Keep defeated maids fixed in place while their owner's challenge is still active. */
    private void tickRetiredMaids() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (EntityMaid maid : serverLevel.getEntitiesOfClass(EntityMaid.class,
            this.getBoundingBox().inflate(128.0D), this::isRetiredByThisBoss)) {
            this.retiredMaidIds.add(maid.getUUID());
        }

        boolean challengeActive = !this.isSeated()
            && (this.isBattleActive() || this.isDefeated() || this.returnHomeTicks > 0);
        Iterator<UUID> iterator = this.retiredMaidIds.iterator();
        while (iterator.hasNext()) {
            UUID maidId = iterator.next();
            Entity entity = serverLevel.getEntity(maidId);
            if (!(entity instanceof EntityMaid maid) || maid.isRemoved()) {
                continue;
            }
            if (!this.isRetiredByThisBoss(maid)) {
                iterator.remove();
                continue;
            }
            if (challengeActive) {
                this.maintainRetiredMaid(maid);
                // 兜一道：已经坐下的女仆不该还是酒狐的目标。正常路径上
                // retireMaidFromChallenge 与 isViableTarget 已经拦住了，这里防的是
                // 别的模组（或读档）绕过索敌直接 setTarget 的情况 —— 目标挂着不清，
                // 战斗 AI 就会对着一个打不动的对象继续挥刀，也不收场。
                if (this.getTarget() == maid) {
                    this.setTarget(null);
                }
            } else {
                this.restoreRetiredMaid(maid);
                iterator.remove();
            }
        }
    }

    private boolean isRetiredByThisBoss(EntityMaid maid) {
        CompoundTag persistent = maid.getPersistentData();
        return persistent.getBoolean(RETIRED_MAID_TAG)
            && persistent.hasUUID(RETIRED_MAID_BOSS_TAG)
            && this.getUUID().equals(persistent.getUUID(RETIRED_MAID_BOSS_TAG));
    }

    /**
     * 这位女仆是不是被这一场劝退、正坐在原地。
     *
     * <p>两个来源都要看：ForgeData 里的 {@code MaidSpellWinefoxRetired} 是服务端的权威标记
     * （随存档走），同步位是给客户端看的镜像 —— 它进 {@code ClientboundAddEntityPacket} 那种
     * 带 NBT 的通道是没有的。少了同步位这一半，客户端就会放行魂符的本地预测，
     * 变成「只掉渲染、实体留下」。
     */
    public static boolean isRetiredMaid(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        if (maid instanceof WinefoxRetiredStateAccessor synced && synced.maidspell$isWinefoxRetired()) {
            return true;
        }
        return maid.getPersistentData().getBoolean(RETIRED_MAID_TAG);
    }

    /**
     * 把劝退状态写进同步位（客户端也读得到的那一份）。Mixin 没应用时静默跳过。
     */
    private static void setRetiredState(EntityMaid maid, boolean retired) {
        if (maid instanceof WinefoxRetiredStateAccessor synced) {
            synced.maidspell$setWinefoxRetired(retired);
        }
    }

    /**
     * 把女仆的工作模式切到「空闲」。
     *
     * <p>幂等且便宜：{@code setTask} 在任务没变时直接早退，所以 {@code maintainRetiredMaid}
     * 每 tick 调一次也不会反复重建 brain。客户端那边由 {@code DATA_TASK} 同步过去，
     * 不需要另外发包。
     */
    private static void setIdleWorkMode(EntityMaid maid) {
        maid.setTask(TaskManager.getIdleTask());
    }

    private void maintainRetiredMaid(EntityMaid maid) {
        setRetiredState(maid, true);
        this.clearMaidCombatState(maid);
        // 万一主人趁这一场还没结束又把任务改回战斗类，这里再按一次。
        setIdleWorkMode(maid);
        maid.setHealth(SURVIVAL_HEALTH_FLOOR);
        maid.setEntityInvulnerable(true);
        maid.setInvulnerable(true);
        maid.setInSittingPose(true);
        maid.setNoGravity(true);
        maid.getNavigation().stop();
        maid.setDeltaMovement(Vec3.ZERO);
        maid.resetFallDistance();

        CompoundTag persistent = maid.getPersistentData();
        if (!persistent.contains(RETIRED_MAID_X_TAG, Tag.TAG_DOUBLE)
            || !persistent.contains(RETIRED_MAID_Y_TAG, Tag.TAG_DOUBLE)
            || !persistent.contains(RETIRED_MAID_Z_TAG, Tag.TAG_DOUBLE)) {
            return;
        }
        Vec3 anchor = new Vec3(persistent.getDouble(RETIRED_MAID_X_TAG),
            persistent.getDouble(RETIRED_MAID_Y_TAG), persistent.getDouble(RETIRED_MAID_Z_TAG));
        if (maid.position().distanceToSqr(anchor) > 1.0E-6D) {
            maid.teleportTo(anchor.x, anchor.y, anchor.z);
        }
    }

    private void restoreRetiredMaid(EntityMaid maid) {
        CompoundTag persistent = maid.getPersistentData();
        this.clearMaidCombatState(maid);
        setRetiredState(maid, false);
        maid.setEntityInvulnerable(persistent.getBoolean(RETIRED_MAID_INVULNERABLE_TAG));
        maid.setInvulnerable(persistent.getBoolean(RETIRED_MAID_VANILLA_INVULNERABLE_TAG));
        maid.setNoGravity(persistent.getBoolean(RETIRED_MAID_NO_GRAVITY_TAG));
        maid.setInSittingPose(persistent.getBoolean(RETIRED_MAID_SITTING_TAG));
        maid.setDeltaMovement(Vec3.ZERO);
        maid.resetFallDistance();
        persistent.remove(RETIRED_MAID_TAG);
        persistent.remove(RETIRED_MAID_BOSS_TAG);
        persistent.remove(RETIRED_MAID_INVULNERABLE_TAG);
        persistent.remove(RETIRED_MAID_VANILLA_INVULNERABLE_TAG);
        persistent.remove(RETIRED_MAID_NO_GRAVITY_TAG);
        persistent.remove(RETIRED_MAID_SITTING_TAG);
        persistent.remove(RETIRED_MAID_X_TAG);
        persistent.remove(RETIRED_MAID_Y_TAG);
        persistent.remove(RETIRED_MAID_Z_TAG);
    }

    /** Restore maids when the challenge ends, including maids discovered after a reload. */
    private void releaseRetiredMaids() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (EntityMaid maid : serverLevel.getEntitiesOfClass(EntityMaid.class,
            this.getBoundingBox().inflate(128.0D), this::isRetiredByThisBoss)) {
            this.retiredMaidIds.add(maid.getUUID());
        }
        Iterator<UUID> iterator = this.retiredMaidIds.iterator();
        while (iterator.hasNext()) {
            Entity entity = serverLevel.getEntity(iterator.next());
            if (entity instanceof EntityMaid maid && this.isRetiredByThisBoss(maid)) {
                this.restoreRetiredMaid(maid);
                iterator.remove();
            }
        }
    }

    /** Remove only the boss-related target memory from one maid. */
    private void clearMaidCombatState(EntityMaid maid) {
        maid.setTarget(null);
        maid.setLastHurtByMob(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getNavigation().stop();
    }

    /** Clear every nearby maid that still remembers this boss after a duel ends. */
    private void clearMaidAggro() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (EntityMaid maid : serverLevel.getEntitiesOfClass(EntityMaid.class,
            this.getBoundingBox().inflate(128.0D), maid -> maid.getTarget() == this
                || maid.getLastHurtByMob() == this)) {
            this.clearMaidCombatState(maid);
        }
    }

    private void clearDuelHazards() {
        Player player = this.challengerId == null ? null : this.level().getPlayerByUUID(this.challengerId);
        if (player != null) {
            player.clearFire();
            player.resetFallDistance();
            for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
                if (effect.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                    player.removeEffect(effect.getEffect());
                }
            }
        }
    }

    public boolean isBattleActive() {
        return !this.isSeated() && !this.isDefeated() && this.returnHomeTicks <= 0;
    }

    public boolean isBattleMusicActive() {
        return this.entityData.get(BATTLE_MUSIC);
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

    /** 玩家挑战结束后、回秋千之前是否正在行礼。 */
    public boolean isCurtsying() {
        return this.entityData.get(CURTSYING);
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
        for (Entity entity : serverLevel.getEntities(this, this.getBoundingBox().inflate(128.0D),
            candidate -> candidate instanceof Projectile
                && MaidSpellAllyResolver.resolveResponsibleEntity(candidate).orElse(null) == this)) {
            entity.discard();
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


    private static boolean isMaidDamage(DamageSource source) {
        return damageFrom(source, EntityMaid.class);
    }

    /**
     * 这一击是不是出自某一类实体 —— 直接打的、它的弹体、或者它召唤出来的东西。
     *
     * <p>召唤物这一层不能漏：伤害源上挂着的是召唤物本身，主人在 owner 链的上游。
     * 漏掉的话，站着不动、让女仆的召唤兽把她磨死会被判成「玩家自己打赢的」，
     * 星云核心和两条特殊交易照发 —— 「女仆代打」判定想防的正是这个。
     *
     * <p>弹体也不必单列：{@code isOwnedBy} 走的那条链本来就把
     * {@code Projectile.getOwner()} 算作一节。
     */
    static boolean damageFrom(DamageSource source, Class<?> type) {
        return MaidSpellAllyResolver.isOwnedBy(source.getDirectEntity(), type)
            || MaidSpellAllyResolver.isOwnedBy(source.getEntity(), type);
    }

    /** Returns true only for damage dealt by the player who opened this duel or that player's maid. */
    private boolean isPlayerDuelDamage(DamageSource source) {
        if (this.challengerId == null) {
            return false;
        }
        Entity[] causes = {source.getEntity(), source.getDirectEntity()};
        for (Entity cause : causes) {
            if (cause instanceof Player player && this.challengerId.equals(player.getUUID())) {
                return true;
            }
            if (cause instanceof EntityMaid maid && this.challengerId.equals(maid.getOwnerUUID())) {
                return true;
            }
            Entity responsible = MaidSpellAllyResolver.resolveResponsibleEntity(cause).orElse(null);
            if (responsible instanceof Player player && this.challengerId.equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WinefoxPhaseTwo", this.isPhaseTwo());
        tag.putBoolean("WinefoxTransitioning", this.isTransitioning());
        tag.putInt("WinefoxTransitionTicks", this.phaseTransitionTicks);
        tag.putBoolean("WinefoxTransitionToPhaseTwo", this.phaseTransitionTarget);
        tag.putBoolean("WinefoxDefeated", this.isDefeated());
        tag.putBoolean("WinefoxCurtsying", this.isCurtsying());
        tag.putBoolean("WinefoxSeated", this.isSeated());
        tag.putBoolean("WinefoxRestricted", this.isRestricted());
        tag.putInt("WinefoxReturnHomeTicks", this.returnHomeTicks);
        tag.putFloat("WinefoxTotalDamageTaken", this.totalDamageTaken);
        tag.putFloat("WinefoxMaidDamageTaken", this.maidDamageTaken);
        tag.putBoolean("WinefoxTrueDamageUsed", this.trueDamageUsed);
        tag.putBoolean("WinefoxTradingUnlocked", this.tradingUnlocked);
        tag.putBoolean("WinefoxHasStartedChallenge", this.hasStartedChallenge);
        tag.putInt("WinefoxChallengeStartTicks", this.challengeStartTicks);
        if (this.challengerId != null) {
            tag.putUUID("WinefoxChallenger", this.challengerId);
        }
        if (this.postVictoryChatPlayerId != null) {
            tag.putUUID("WinefoxPostVictoryChatPlayer", this.postVictoryChatPlayerId);
        }
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
        // 缺键必须按 true 算，不能吃 getBoolean 的默认 false：/summon 不带 NBT 走的就是这条路，
        // 读出 false 她会站起来主动打人，一枚星云核心都没花就能被杀了掏战利品。
        // 这个分支上没有需要兼容的旧存档（1.9.0-alpha 还没发过）。
        this.entityData.set(SEATED, !tag.contains("WinefoxSeated") || tag.getBoolean("WinefoxSeated"));
        this.entityData.set(RESTRICTED, tag.getBoolean("WinefoxRestricted"));
        this.returnHomeTicks = tag.getInt("WinefoxReturnHomeTicks");
        this.totalDamageTaken = tag.getFloat("WinefoxTotalDamageTaken");
        this.maidDamageTaken = tag.getFloat("WinefoxMaidDamageTaken");
        this.trueDamageUsed = tag.getBoolean("WinefoxTrueDamageUsed");
        this.tradingUnlocked = tag.getBoolean("WinefoxTradingUnlocked");
        this.hasStartedChallenge = tag.getBoolean("WinefoxHasStartedChallenge");
        this.challengeStartTicks = tag.getInt("WinefoxChallengeStartTicks");
        this.challengerId = tag.hasUUID("WinefoxChallenger") ? tag.getUUID("WinefoxChallenger") : null;
        this.postVictoryChatPlayerId = tag.hasUUID("WinefoxPostVictoryChatPlayer")
            ? tag.getUUID("WinefoxPostVictoryChatPlayer") : null;
        // 她战败之后是留在场上的，读档得接着躺着，不能爬起来重新开打。
        this.entityData.set(DEFEATED, tag.getBoolean("WinefoxDefeated"));
        this.entityData.set(CURTSYING, tag.getBoolean("WinefoxCurtsying"));
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
        if (this.isSeated()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            this.releaseRetiredMaids();
        }
        super.remove(reason);
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
        return false;
    }

    /**
     * Boss 血条的名字。
     *
     * <p>称号外面套了一层 {@link WinefoxBossBar#NAME_KEY}，这一层在语言文件里就是
     * {@code "%s"}，所以玩家看到的字只有 {@link WinefoxBossBar#TITLE_KEY} 翻译出来的
     * 「星之魔女酒狐」。多这一层是为了让客户端能认出「这条血条是酒狐的」—— 见
     * {@code WinefoxBossBarOverlay}。血条的名字里除了文本什么都没有，没有实体 id、
     * 也没有事件 id 可用，键名是唯一能稳定带过去的标记。
     *
     * <p>血条用的是固定称号，不再跟 {@code getDisplayName()} 走：实体名（头顶名、刷怪蛋、
     * 对话里的自称）仍旧是「星之魔女」，命名牌也只改那一个。原来那个在
     * {@code setCustomName} 里同步血条名的重载因此没了意义 —— 血条名根本不看实体名。
     */
    private Component createBossBarName() {
        return Component.translatable(WinefoxBossBar.NAME_KEY,
                Component.translatable(WinefoxBossBar.TITLE_KEY));
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
     * <p>{@code death} 是 5 秒的 {@code HOLD_LAST_FRAME} 动画，演出结束前实体保持在原地。
     * 原版的 {@code tickDeath} 本来就只在 {@code isDeadOrDying()} 时被调，而她的血永远是 1，
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
