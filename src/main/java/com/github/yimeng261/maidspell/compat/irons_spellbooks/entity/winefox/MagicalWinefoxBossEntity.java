package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.portal.DimensionTransition;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.StellarWitchStarterMaid;
import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.DelayedServerTasks;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.NpcMerchantTrading;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.StarShadowSpearEntity;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.api.IPersistentEncounterEntity;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.api.IBossSyncedDataGuard;
import com.github.yimeng261.maidspell.utils.BossLifecycleAccess;
import com.github.yimeng261.maidspell.utils.PersistentEntityLifecycleGuard;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.network.casting.SyncEntityDataPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.IBossDamageClamp;
import com.github.yimeng261.maidspell.api.ITrueDamageRedirect;
import com.github.yimeng261.maidspell.mixin.accessor.MobFlagsAccessor;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.common.StarglintDaggerItem;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeProgress;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MagicalWinefoxBossEntity extends AbstractSpellCastingMob
    implements Enemy, IMaid, CastingAnimateStateAccessor, ITrueDamageRedirect, Merchant,
        IPersistentEncounterEntity, IBossSyncedDataGuard, IBossDamageClamp, IAuthoritativeHealth {
    static final String RETIRED_MAID_TAG = "MaidSpellWinefoxRetired";
    static final String RETIRED_MAID_BOSS_TAG = "MaidSpellWinefoxRetiredBoss";
    /** {@code Mob.DATA_MOB_FLAGS_ID} 里"关闭 AI"那一位，{@code Mob#setNoAi} 写的就是它。 */
    private static final byte NO_AI_FLAG_BIT = 1;
    private static final String RETIRED_MAID_INVULNERABLE_TAG = "MaidSpellWinefoxRetiredInvulnerable";
    private static final String RETIRED_MAID_VANILLA_INVULNERABLE_TAG = "MaidSpellWinefoxRetiredVanillaInvulnerable";
    private static final String RETIRED_MAID_NO_GRAVITY_TAG = "MaidSpellWinefoxRetiredNoGravity";
    private static final String RETIRED_MAID_SITTING_TAG = "MaidSpellWinefoxRetiredSitting";
    private static final String RETIRED_MAID_X_TAG = "MaidSpellWinefoxRetiredX";
    private static final String RETIRED_MAID_Y_TAG = "MaidSpellWinefoxRetiredY";
    private static final String RETIRED_MAID_Z_TAG = "MaidSpellWinefoxRetiredZ";

    /** 已听过初见台词的玩家；落 NBT 以免区块卸载后重复播报。 */
    private static final String GREETED_PLAYERS_TAG = "WinefoxGreetedPlayers";

    /** 誓约进度、计时、信物和玩家身份必须落 NBT，避免中途卸载后丢失进度。 */
    private static final String VOW_STAGE_TAG = "WinefoxVowStage";
    private static final String VOW_TICKS_TAG = "WinefoxVowTicks";
    private static final String VOW_SEALED_TAG = "WinefoxVowSealed";
    private static final String VOW_CHARM_TAG = "WinefoxVowCharmGiven";
    private static final String VOW_PLAYER_TAG = "WinefoxVowPlayer";

    /** 常规胜利次数落 NBT；旧存档缺键时从零开始。 */
    private static final String PLAYER_WIN_COUNT_TAG = "WinefoxPlayerWinCount";
    private static final String SOLO_WIN_AFTER_THREE_TAG = "WinefoxSoloWinAfterThree";
    private static final EntityDataAccessor<Integer> ACTION =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_SERIAL =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PHASE_TWO =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRANSITIONING =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** 玩家挑战结束后播放的行礼主动画。 */
    private static final EntityDataAccessor<Boolean> CURTSYING =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);

    /** 上一场限制奖励的标记，同步给客户端并落 NBT。 */
    private static final EntityDataAccessor<Boolean> RESTRICTED =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BATTLE_MUSIC =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Float> BOSS_HEALTH =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> BOSS_MAX_HEALTH =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Byte> ENCOUNTER_STATE =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Byte> REWARD_STATE =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Integer> ENCOUNTER_SERIAL =
        SynchedEntityData.defineId(MagicalWinefoxBossEntity.class, EntityDataSerializers.INT);

    /** 战败演出放完到回秋千坐下之间的间隔。 */
    private static final int DEFEAT_RETURN_HOME_TICKS = WinefoxAction.DEFEAT.durationTicks();

    /** 玩家挑战结束后的行礼动画时长。 */
    private static final int CURTSY_RETURN_HOME_TICKS = 6 * 20;

    /** 驯服先需三场非限制胜利，之后再赢一场女仆零伤害的战斗。 */
    private static final int VOW_REQUIRED_PLAYER_WINS = 3;
    private static final int VOW_DIALOGUE_INTERVAL_TICKS = 2 * 20;

    /** 誓约没在演（可以做别的：交易、邀战、闲谈）。 */
    private static final int VOW_STAGE_IDLE = 0;
    /** 第一问：{@code vow_1} 循环中，等玩家再右击一次。 */
    private static final int VOW_STAGE_FIRST = 1;
    /** 第二问：{@code vow_2} 播放中，道具已消耗。 */
    private static final int VOW_STAGE_SECOND = 2;

    /** 信物出手到进入玩家背包的延迟；不生成可丢失的物品实体。 */
    private static final int VOW_CHARM_FLIGHT_TICKS = 12;

    /** 场上连续 30 秒没有可打的目标就收场，见 {@link #tickBattleOver}。 */
    private static final int BATTLE_OVER_GRACE_TICKS = 600;

    /** AI 连段窗口，不随动画时长变化。 */
    private static final int SWORD_COMBO_RESET_TICKS = 40;

    // 阶段时长与动作事件统一从 WinefoxAction 推导。
    private static final int PHASE_TRANSITION_TICKS = WinefoxAction.PHASE_TRANSITION.durationTicks();
    private static final int PHASE_TRANSITION_KNOCKBACK_TICK =
        eventTick(WinefoxAction.PHASE_TRANSITION, WinefoxAction.EventKind.KNOCKBACK);
    private static final int PHASE_TRANSITION_WEAPON_SWAP_TICK =
        eventTick(WinefoxAction.PHASE_TRANSITION, WinefoxAction.EventKind.WEAPON_SWAP);

    /** 半血进入二阶段，治疗不重置本场战斗阶段。 */
    private static final float PHASE_TWO_HEALTH_FRACTION = 0.5F;

    /** 正式表演赛中挑战者与女仆的最低生命；Boss 使用独立生命，可降至零。 */
    static float duelSurvivalFloor() {
        return (float) Config.winefoxDuelSurvivalFloor;
    }
    private static final ResourceLocation MAX_HEALTH_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "winefox_encounter_max_health");
    private static final ResourceLocation COSMETIC_EQUIPMENT_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "winefox_cosmetic_equipment");

    /** 五稿规定动画第二秒发射直线投枪。 */
    private static final int SPEAR_RELEASE_TICKS =
        eventTick(WinefoxAction.SPEAR_THROW, WinefoxAction.EventKind.PROJECTILE);

    /**
     * 身体转向目标的最大角速度。太大就是瞬间贴脸，太小绕圈时会追不上。
     */
    private static final float BODY_TURN_DEGREES_PER_TICK = 15.0F;
    private static final double SEATED_LOOK_RANGE = 12.0D;
    /** 行礼可在整个追击范围内寻找玩家。 */
    private static final double CURTSY_LOOK_RANGE = 48.0D;
    private static final double GREETING_TRIGGER_RANGE = 5.0D;
    private static final double AMBIENT_DIALOGUE_RANGE = 10.0D;
    /** 其它台词会重置的自言自语间隔。 */
    private static final int AMBIENT_DIALOGUE_INTERVAL_TICKS = 60 * 20;
    private static final double SEATED_HOVER_OFFSET = 0.5D;
    /** 邀战和女仆代主人先动手共用的最大距离。 */
    private static final double CHALLENGER_MAX_DISTANCE = 48.0D;
    private static final double PHASE_ONE_VERTICAL_SPEED = 0.04D;
    /** 二阶段的爬升/沉降上限。比一阶段宽一档，追人时不至于被高度拖住。 */
    private static final double PHASE_TWO_VERTICAL_SPEED = 0.25D;
    /** 失去目标后的缓降速度。 */
    private static final double LOST_TARGET_DESCENT_SPEED = 0.08D;
    /** 补偿原版 travel 在缓降时额外施加的重力。 */
    private static final double DESCENT_GRAVITY = 0.08D;
    /** 接近地面后交回原版物理。 */
    private static final double DESCENT_GROUND_SNAP_DISTANCE = 0.6D;
    /** 悬停不动的判定阈值。比她现在的速度略大一档，只认得住「几乎停住」。 */
    private static final double HOVER_EPSILON = 0.05D;

    /** 玩家高于酒狐时触发快速爬升的垂直差值。 */
    private static final double VERTICAL_CHASE_TRIGGER = 20.0D;
    /** 起飞时每 tick 额外补的上升速度，叠上重力与阻尼后净上升约 5 格/秒。 */
    private static final double VERTICAL_CHASE_CLIMB_SPEED = 0.5D;
    /** 起飞那几 tick 里还要交出去的重力，理由与 {@link #DESCENT_GRAVITY} 相同。 */
    private static final double ASCENT_GRAVITY = 0.08D;
    /** 创造模式玩家参与垂直追击判定的范围平方。 */
    private static final double CREATIVE_VERTICAL_CHASE_RANGE_SQR = 16.0D * 16.0D;
    private static final double TRANSITION_KNOCKBACK_RADIUS = 5.0D;
    private static final double TRANSITION_KNOCKBACK_STRENGTH = 4.0D;

    /** 内置 TLM 模型包中的模型 ID。 */
    public static final String MODEL_ID = "touhou_little_maid_spell:stellar_witch";

    private final ServerBossEvent bossEvent = new ServerBossEvent(
        this.createBossBarName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private int nextSwordVariant;
    private int nextStaffVariant;
    private int lastSwordSwingTick = Integer.MIN_VALUE;

    /** 服务端动作起始 tick，用于连段窗口判断。 */
    private int actionStartTick;
    private double flightTargetY;
    /** 每 tick 重算，供后续 travel 的爬升分支读取。 */
    private boolean fastAscending;
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
    private boolean maidDamageOccurred;
    /** 本场有没有人对她用过真伤。「女仆代打」的另一个触发条件。 */
    private boolean trueDamageUsed;
    /** 战败演出结束后回秋千的倒计时，见 {@link #tickReturnHome}。 */
    private int returnHomeTicks;
    /** 连续多少 tick 没有可打的目标了，见 {@link #tickBattleOver}。 */
    private int noTargetTicks;
    /**
     * 上一次<b>有效受击</b>的 {@code tickCount}；{@link Long#MIN_VALUE} 表示这一场还没挨过打。
     *
     * <p>这是我们自己生命系统里的受击间隔，与原地版 {@code invulnerableTime} 无关，二者互不读写：
     * 原版那套是"受到 >= 10 tick 的窗口内减伤"，而本模组有多处主动把 {@code invulnerableTime}
     * 清零（法术、混沌之书、拔刀、梦云水晶），指望它来限频等于没有限频。这里的字段把
     * <b>所有</b>会落到 {@code hurt()} 的伤害统一到同一个间隔上。
     */
    private long lastHitTick = Long.MIN_VALUE;
    /** 她输过至少一次，交易就此开放。落 NBT，不随重新挑战撤销。 */
    private boolean tradingUnlocked;
    /**
     * 玩家以常规手段战胜她的次数，攒够 {@link #VOW_REQUIRED_PLAYER_WINS} 场才谈得上驯服。
     *
     * <p>与 {@link #tradingUnlocked} 同一条件、同一处记账：只有 <b>这一场不是女仆代打</b> 才算数。
     * 分开记而不是拿交易解锁当依据，是因为那个是一位布尔，说不清「赢过几场」。
     */
    private int playerWinCount;
    /** 三场常规胜利之后，是否又赢过一场女仆没有造成伤害的战斗。 */
    private boolean soloWinAfterThree;
    /** 「归星」驯服的进度，取值见 {@code VOW_STAGE_*}。 */
    private int vowStage;
    /**
     * 当前这一段誓约还剩多少 tick。
     *
     * <p>由 {@link #aiStep} 驱动而不是 {@code customServerAiStep}：坐姿时 {@link #isImmobile()} 为真，
     * 那整条 {@code serverAiStep} 都不跑，而这个倒计时必须走 —— 与 {@link #challengeStartTicks} 同一个理由。
     */
    private int vowTicks;
    /** 誓约已成（第二次右击那一下）。不可撤销，也是「已缔结了誓约」那句提示的唯一依据。 */
    private boolean vowSealed;
    /** 魂符已经交出去过。挡住读档补挂 {@code vow_2} 时又重放一次第 120t 那一发。 */
    private boolean vowCharmGiven;
    /**
     * 第二问是谁点出来的 —— 魂符要交到他手上。
     *
     * <p>落 NBT 而不是只留在内存：第 120t 那一发是个延迟任务，中间隔着一整个区块卸载窗口，
     * 读档之后还要知道该给谁。空着时 {@link #throwVowCharm} 直接放弃，不会发给路人。
     */
    @Nullable
    private UUID vowPlayerId;
    private boolean hasStartedChallenge;
    /** Settings taken from the dagger for the currently active formal duel. */
    @Nullable
    private WinefoxChallengeConfig activeChallengeConfig;
    private boolean customChallenge;
    private boolean healthReady;
    /** Server authority; BOSS_HEALTH is only a network mirror. */
    private float authoritativeHealth;
    private boolean authoritativeHealthReady;
    /** Server authority; BOSS_MAX_HEALTH is only a network mirror. */
    private float authoritativeMaxHealth;
    private boolean authoritativeMaxHealthReady;
    /** A low-level duplicate write is expected after an external setHealth call. */
    private boolean expectedExternalHealthWrite;
    private int expectedExternalHealthWriteTick = Integer.MIN_VALUE;
    private boolean loadingLifecycle;
    private UUID encounterId = UUID.randomUUID();
    private UUID rewardTransaction;
    private float unattributedDamage;
    private long lastUnattributedLogTime = Long.MIN_VALUE;
    private long lastBlockedRemovalSync = Long.MIN_VALUE;
    /** 正在跟她做买卖的玩家；交易表现算，见 {@link #getOffers}。 */
    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;
    /**
     * 当前这张 {@link #offers} 是照着哪个 {@code tradingUnlocked} 造出来的。
     *
     * <p>用来判断「报价集合变了没有」：只有这个值与她当前的 {@code tradingUnlocked} 不一致时
     * 才需要重算表（那意味着她刚拿到第一场合格胜利、该多出那一档装备与卷轴）。
     * 光看 {@code offers == null} 不够 —— 那会把「表还在、但集合已经过时」这种情况漏掉。
     */
    private boolean offersBuiltForUnlocked;
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
        this.healthReady = true;
        this.authoritativeHealth = super.getHealth();
        this.authoritativeHealthReady = true;
        // 配置的上限只在新生成时生效，并且**只裁剪、不回填**：她只会被压到新上限，
        // 绝不会因为把配置调大就凭空涨血（那等于免费治疗）。已存在的实体会把上限
        // 连同生命一起写进存档，读档时以存档为准，因此调小配置不会追溯削弱老存档。
        this.maidspell$setMaxHealth((float) Config.winefoxMaxHealth);
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.RESET,
            () -> WinefoxBossHealthController.write(this,
                Math.min(super.getHealth(), this.maidspell$bossMaxHealth())));
        if (!level.isClientSide) {
            this.setPersistenceRequired();
        }
        this.xpReward = 80;
        this.moveControl = new FlyingMoveControl(this, 20, true);
        // 构造时装备可覆盖结构生成等不走 finalizeSpawn 的路径。
        // 仅服务端设置；首次装备同步不会发送空槽位，客户端预设可能无法被纠正。
        if (!level.isClientSide) {
            this.equipStarMajoGear();
        }
    }

    @Override
    public float getHealth() {
        if (!this.healthReady || !this.authoritativeHealthReady) {
            return super.getHealth();
        }
        return this.authoritativeHealth;
    }

    /** Internal health view that is not rewritten by third-party getHealth transformers. */
    @Override
    public float maidspell$authoritativeHealth() {
        return this.authoritativeHealthReady ? this.authoritativeHealth : super.getHealth();
    }

    @Override
    public void setHealth(float health) {
        if (!this.healthReady) {
            super.setHealth(health);
            return;
        }
        WinefoxBossHealthController.write(this, health);
    }

    @Override
    public void tick() {
        this.maidspell$clearExpectedHealthWrite();
        super.tick();
    }

    /**
     * 她永远不会失去 AI。
     *
     * <p>{@code noAi} 是单向开关：一旦为真就无法恢复，而她的收场与归位都挂在 {@code aiStep} 上，
     * 被关掉就卡在"打不完、也重开不了"。要她静止有 {@link #isImmobile()} 与
     * {@link WinefoxEncounterState} 两条可撤销的途径。
     *
     * <p>同步字段层的直写由 {@link #maidspell$protectSyncedDataWrite} 纠正，否则两端认知不一致。
     */
    @Override
    public void setNoAi(boolean noAi) {
        if (noAi) {
            com.github.yimeng261.maidspell.Global.LOGGER.debug(
                "Ignored setNoAi(true) for the Stellar Witch {}: her AI must stay enabled", this.getUUID());
        }
    }

    boolean maidspell$isLoadingLifecycle() {
        return this.loadingLifecycle;
    }

    long maidspell$lastEffectiveHitTick() {
        return this.lastHitTick;
    }

    /** 记下这一次有效受击，作为受击间隔的起点。只由 {@link WinefoxBossHealthController} 调用。 */
    void maidspell$markEffectiveHit() {
        this.lastHitTick = this.tickCount;
    }

    void maidspell$resetHitInterval() {
        this.lastHitTick = Long.MIN_VALUE;
    }

    /** 供控制器判断是否叠加二阶段减伤；只有正式玩家挑战会走倍率链。 */
    boolean maidspell$isPlayerCombatPhaseTwo() {
        return this.isPlayerCombatActive() && this.isPhaseTwo();
    }

    /**
     * 跑一遍原版伤害结算链：复用护甲、抗性、吸收与 Forge 事件。
     * 链内的生命读取由 Mixin 改为权威值，{@code setHealth} 仍回到控制器的 {@code write}。
     *
     * <p>只由 {@link WinefoxBossHealthController#applyDamage} 在 {@code DAMAGE} 上下文内调用。
     */
    void maidspell$runVanillaDamagePipeline(DamageSource source, float amount) {
        super.hurt(source, amount);
    }

    /**
     * 单次受击上限的兜底出口。正常伤害在 {@code write()} 里就已经被限住，这里只兜"绕过
     * {@code hurt()} 直接调 {@code actuallyHurt}"那条路。两条路径共用同一份配置。
     */
    @Override
    public float maidspell$maxDamagePerHit(float finalDamage) {
        if (this.maidspell$encounterState() != WinefoxEncounterState.COMBAT) {
            return IBossDamageClamp.NO_DAMAGE_CAP;
        }
        return WinefoxBossHealthController.damageCap(this);
    }

    @Override
    public boolean maidspell$protectSyncedDataWrite(EntityDataAccessor<?> accessor, Object value) {
        if (!this.healthReady || this.level().isClientSide || BossLifecycleAccess.canWriteData(this)) {
            return false;
        }
        if (accessor == BOSS_HEALTH && value instanceof Float requested) {
            WinefoxBossHealthController.handleExternalHealthWrite(this, requested);
            return true;
        }
        if (accessor == BOSS_MAX_HEALTH && value instanceof Float) {
            WinefoxBossHealthController.handleExternalMaxHealthWrite(this);
            return true;
        }
        if (accessor == LivingEntityHealthAccessor.maidspell$getHealthAccessor()) {
            BossLifecycleAccess.withDataWrite(this,
                () -> this.entityData.set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), 1.0F));
            return true;
        }
        // 方法层的 setNoAi 覆写挡不住"直接写同步字段"这条路。若只挡方法层，
        // 服务端会保持有 AI 而客户端收到 true，或反过来 —— 两端对 noAi 的认知不一致。
        // 这里一律纠正为 false，与 setNoAi(boolean) 保持同一语义。
        if (accessor == MobFlagsAccessor.maidspell$getMobFlagsAccessor()) {
            if (value instanceof Byte flags && (flags & NO_AI_FLAG_BIT) != 0) {
                byte corrected = (byte) (flags & ~NO_AI_FLAG_BIT);
                BossLifecycleAccess.withDataWrite(this,
                    () -> this.entityData.set(MobFlagsAccessor.maidspell$getMobFlagsAccessor(), corrected));
            }
            return true;
        }
        return accessor == BOSS_MAX_HEALTH || accessor == ENCOUNTER_STATE
            || accessor == REWARD_STATE || accessor == ENCOUNTER_SERIAL;
    }

    @Override
    public void maidspell$onSyncedDataUpdated(EntityDataAccessor<?> accessor, Object value) {
        if (accessor == LivingEntityHealthAccessor.maidspell$getHealthAccessor()) {
            if (!BossLifecycleAccess.canWriteData(this)) {
                BossLifecycleAccess.withDataWrite(this,
                    () -> this.entityData.set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), 1.0F));
            }
            return;
        }
        if (accessor == BOSS_HEALTH && value instanceof Float requested) {
            if (this.level().isClientSide) {
                float mirroredHealth = Float.isFinite(requested) ? Math.max(0.0F, requested) : 0.0F;
                if (this.authoritativeMaxHealthReady) {
                    mirroredHealth = Math.min(mirroredHealth, this.maidspell$bossMaxHealth());
                }
                this.maidspell$setAuthoritativeHealth(mirroredHealth);
            } else if (this.healthReady && !BossLifecycleAccess.canWriteData(this)) {
                WinefoxBossHealthController.handleExternalHealthWrite(this, requested);
            }
            return;
        }
        if (accessor == BOSS_MAX_HEALTH && value instanceof Float requested) {
            if (this.level().isClientSide) {
                this.authoritativeMaxHealth = Float.isFinite(requested) && requested > 0.0F
                    ? requested : this.getMaxHealth();
                this.authoritativeMaxHealthReady = true;
                if (this.authoritativeHealthReady) {
                    this.authoritativeHealth = Math.min(this.authoritativeHealth, this.authoritativeMaxHealth);
                }
            } else if (this.healthReady && !BossLifecycleAccess.canWriteData(this)) {
                WinefoxBossHealthController.handleExternalMaxHealthWrite(this);
            }
            return;
        }
        // Some health systems keep an auxiliary float modifier in synced data
        // and apply it around getHealth(). Bound only the values that would
        // otherwise make this authority report a negative effective health.
        if (value instanceof Float modifier && this.getHealth() < 0.0F) {
            float lowerBound = -this.maidspell$bossMaxHealth();
            float corrected = Float.isFinite(modifier) ? Math.max(modifier, lowerBound) : 0.0F;
            if (Float.compare(modifier, corrected) != 0) {
                BossLifecycleAccess.withDataWrite(this,
                    () -> this.maidspell$setSyncedFloat(accessor, corrected));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void maidspell$setSyncedFloat(EntityDataAccessor<?> accessor, float value) {
        this.entityData.set((EntityDataAccessor<Float>) accessor, value);
    }

    void maidspell$setAuthoritativeHealth(float health) {
        this.authoritativeHealth = health;
        this.authoritativeHealthReady = true;
    }

    void maidspell$expectExternalHealthWrite() {
        this.expectedExternalHealthWrite = true;
        this.expectedExternalHealthWriteTick = this.tickCount;
    }

    boolean maidspell$consumeExpectedHealthWrite() {
        boolean matches = this.expectedExternalHealthWrite
            && this.expectedExternalHealthWriteTick == this.tickCount;
        if (matches) {
            this.expectedExternalHealthWrite = false;
            this.expectedExternalHealthWriteTick = Integer.MIN_VALUE;
        }
        return matches;
    }

    private void maidspell$clearExpectedHealthWrite() {
        if (this.expectedExternalHealthWriteTick != this.tickCount) {
            this.expectedExternalHealthWrite = false;
            this.expectedExternalHealthWriteTick = Integer.MIN_VALUE;
        }
    }

    void maidspell$syncMaxHealthMirror() {
        if (this.level().isClientSide || !this.authoritativeMaxHealthReady) {
            return;
        }
        BossLifecycleAccess.withDataWrite(this,
            () -> this.entityData.set(BOSS_MAX_HEALTH, this.authoritativeMaxHealth));
    }

    @Override
    public void heal(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F) {
            return;
        }
        if (!this.healthReady) {
            super.heal(amount);
            return;
        }
        float applied = EventHooks.onLivingHeal(this, amount);
        if (!Float.isFinite(applied) || applied <= 0.0F || this.level().isClientSide) {
            return;
        }
        float current = this.maidspell$authoritativeHealth();
        if (current <= 0.0F) {
            return;
        }
        float requested = Math.min(this.maidspell$bossMaxHealth(), current + applied);
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.HEAL,
            () -> this.setHealth(requested));
    }

    float maidspell$bossMaxHealth() {
        if (this.authoritativeMaxHealthReady
                && Float.isFinite(this.authoritativeMaxHealth)
                && this.authoritativeMaxHealth > 0.0F) {
            return this.authoritativeMaxHealth;
        }
        float value = this.entityData.get(BOSS_MAX_HEALTH);
        return Float.isFinite(value) && value > 0.0F ? value : this.getMaxHealth();
    }

    void maidspell$setMaxHealth(float requested) {
        float max = Float.isFinite(requested) && requested >= 1.0F ? requested : 600.0F;
        this.authoritativeMaxHealth = max;
        this.authoritativeMaxHealthReady = true;
        AttributeInstance attribute = this.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
            double difference = max - attribute.getValue();
            if (difference != 0.0D) {
                attribute.addTransientModifier(new AttributeModifier(MAX_HEALTH_MODIFIER_ID,
                    difference, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (!this.level().isClientSide) {
            BossLifecycleAccess.withDataWrite(this, () -> this.entityData.set(BOSS_MAX_HEALTH, max));
        }
    }

    WinefoxEncounterState maidspell$encounterState() {
        return WinefoxEncounterState.fromId(this.entityData.get(ENCOUNTER_STATE));
    }

    private boolean maidspell$setEncounterState(WinefoxEncounterState state) {
        WinefoxEncounterState previous = this.maidspell$encounterState();
        if (!this.loadingLifecycle && !previous.canTransitionTo(state)) {
            com.github.yimeng261.maidspell.Global.LOGGER.warn(
                "Rejected Winefox encounter transition {} -> {} for {}", previous, state, this.getUUID());
            return false;
        }
        BossLifecycleAccess.withDataWrite(this,
            () -> this.entityData.set(ENCOUNTER_STATE, (byte) state.ordinal()));
        return true;
    }

    private WinefoxRewardState maidspell$rewardState() {
        return WinefoxRewardState.fromId(this.entityData.get(REWARD_STATE));
    }

    private void maidspell$setRewardState(WinefoxRewardState state) {
        BossLifecycleAccess.withDataWrite(this,
            () -> this.entityData.set(REWARD_STATE, (byte) state.ordinal()));
    }

    private boolean maidspell$beginEncounter(WinefoxEncounterState state) {
        if (this.maidspell$rewardState() == WinefoxRewardState.GRANTING) {
            com.github.yimeng261.maidspell.Global.LOGGER.warn(
                "Winefox {} cannot start another encounter while reward {} needs review",
                this.getUUID(), this.rewardTransaction);
            return false;
        }
        if (this.maidspell$encounterState() != WinefoxEncounterState.SEATED
                && this.maidspell$encounterState() != WinefoxEncounterState.CHALLENGE_START) {
            return false;
        }
        if (!this.maidspell$setEncounterState(state)) {
            return false;
        }
        BossLifecycleAccess.withDataWrite(this,
            () -> this.entityData.set(ENCOUNTER_SERIAL, this.entityData.get(ENCOUNTER_SERIAL) + 1));
        this.unattributedDamage = 0.0F;
        this.rewardTransaction = null;
        this.customChallenge = false;
        // 受击间隔按场重置：上一场残留的时间戳不该让新挑战的第一击被挡下。
        this.maidspell$resetHitInterval();
        this.maidspell$setRewardState(WinefoxRewardState.NONE);
        return true;
    }

    void maidspell$recordUnattributedDamage(float amount) {
        this.unattributedDamage += amount;
        long now = this.level().getGameTime();
        if (this.lastUnattributedLogTime != Long.MIN_VALUE && now - this.lastUnattributedLogTime < 100L) {
            return;
        }
        this.lastUnattributedLogTime = now;
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String caller = java.util.Arrays.stream(stack)
            .filter(frame -> !frame.getClassName().equals(Thread.class.getName())
                && !frame.getClassName().equals(WinefoxBossHealthController.class.getName())
                && !frame.getClassName().equals(MagicalWinefoxBossEntity.class.getName())
                && !frame.getClassName().contains("SynchedEntityDataHealthMixin")
                && !frame.getClassName().equals(SynchedEntityData.class.getName()))
            .findFirst().map(StackTraceElement::toString).orElse("unknown");
        com.github.yimeng261.maidspell.Global.LOGGER.warn(
            "Unattributed Winefox health write entity={} encounter={}#{} lost={} caller={}",
            this.getUUID(), this.encounterId, this.entityData.get(ENCOUNTER_SERIAL), amount, caller);
    }

    /** 正式玩家挑战才判战败；待机、开场和普通生物战斗均按脱战归位。 */
    private boolean isOutOfCombat() {
        WinefoxEncounterState state = this.maidspell$encounterState();
        if (state == WinefoxEncounterState.DEFEATED || state == WinefoxEncounterState.RETURNING) {
            return false;
        }
        return state != WinefoxEncounterState.COMBAT || !this.isPlayerCombatActive();
    }

    /** 脱战致命伤走共用归位清理；坐姿先切到合法的 RETURNING 状态。 */
    private void retreatHomeInsteadOfDefeat() {
        if (this.maidspell$encounterState() == WinefoxEncounterState.SEATED) {
            this.maidspell$forceReturningForRepair();
        }
        this.maidspell$returnAuthorized();
        this.returnHomeTicks = 1;
    }

    /** 处理绕过 hurt 的直写生命值，补上另一条致命伤入口。 */
    void maidspell$handleUnattributedDefeat() {
        if (this.isOutOfCombat()) {
            this.retreatHomeInsteadOfDefeat();
        } else {
            this.beginDefeat(null);
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
            // 归位时屏蔽控制器残留输入，保留重力供战败落地。
            super.travel(Vec3.ZERO);
            return;
        }
        if (this.isNoGravity() && this.getTarget() != null && this.getTarget().isAlive()) {
            if (this.isBusyCombatAction()) {
                // 独占动作期间清除 MoveControl 的旧路点输入。
                this.setDeltaMovement(Vec3.ZERO);
                return;
            }
            // Keep horizontal steering; handle altitude directly.
            Vec3 delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            super.travel(new Vec3(travelVector.x, 0.0D, travelVector.z));
            double vertical = this.fastAscending
                             // 玩家被甩在头顶时不再理那套驻留高度，直接按爬升速度飞上去。
                             ? VERTICAL_CHASE_CLIMB_SPEED + ASCENT_GRAVITY
                             : Mth.clamp(this.flightTargetY - this.getY(),
                                 -this.maxVerticalSpeed(), this.maxVerticalSpeed());
            this.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.0D, vertical, 0.0D));
            delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, 0.0D, delta.z);
            return;
        }
        super.travel(travelVector);
    }

    /** 战斗飞行时每 tick 允许的垂直速度：二阶段比一阶段快一档。 */
    private double maxVerticalSpeed() {
        return this.isPhaseTwo() ? PHASE_TWO_VERTICAL_SPEED : PHASE_ONE_VERTICAL_SPEED;
    }

    /** 重力意外恢复且悬停目标丢失时恒速缓降，接近地面后交回原版物理。 */
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

    /** 战斗尚未收场但目标暂失时，清空导航与移动输入并原地悬停。 */
    private void tickHoverHold() {
        this.setNoGravity(true);
        this.getNavigation().stop();
        this.resetFlightControl();
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = false;
        this.resetFallDistance();
    }

    /** 按附近玩家的最大高度差决定是否快速起飞，实际位移由 travel 处理。 */
    private void tickAscent() {
        this.fastAscending = this.verticalChaseGap() > VERTICAL_CHASE_TRIGGER;
    }

    /**
     * 附近所有可交互玩家里，站在她上方最高的那一位高出来多少格。
     *
     * <p>只取正值：她本来就习惯待在玩家上方。观众跳过，创造玩家只在远处跳过 —— 近处的创造
     * 玩家仍能递短剑开正式挑战（见 {@link #mobInteract}），那种情况她该跟着上去。
     *
     * <p>范围复用 {@code FOLLOW_RANGE}（与 {@code prioritizePlayerTarget} 同口径）：更远的玩家
     * 本来就不在她战斗范围内，为他们起飞只会让她被路过的观众牵走。
     */
    private double verticalChaseGap() {
        double followRangeSqr = Mth.square(this.getAttributeValue(Attributes.FOLLOW_RANGE));
        double largestGap = 0.0D;
        for (Player player : this.level().players()) {
            if (player.isSpectator() || player.isRemoved() || !player.isAlive()) {
                continue;
            }
            double distanceSqr = this.distanceToSqr(player);
            if (distanceSqr > followRangeSqr) {
                continue;
            }
            if (player.isCreative() && distanceSqr > CREATIVE_VERTICAL_CHASE_RANGE_SQR) {
                continue;
            }
            double gap = player.getY() - this.getY();
            if (gap > largestGap) {
                largestGap = gap;
            }
        }
        return largestGap;
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

    /** 清除移动目标与输入；isImmobile 会跳过控制器 tick，残留 yya 不会自行复位。 */
    private void resetFlightControl() {
        this.flightTargetY = this.getY();
        this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setZza(0.0F);
        this.setSpeed(0.0F);
        this.setJumping(false);
    }

    /** 飞行与战败落地不受摔落伤害；仍调用 super 以发送 Forge 落地事件。 */
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
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData);
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
        // 装备只管外观，数值已计入基础属性。属性组件为空时 NeoForge 会回退到物品自带的默认修饰符，
        // 所以放一条 0 值占位让组件非空。
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
            .add(Attributes.ARMOR, new AttributeModifier(COSMETIC_EQUIPMENT_MODIFIER_ID, 0.0D,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY)
            .build());
        return stack;
    }

    /** 独立投枪动作，不与剑牢法术共用弹体。 */
    public void startSpearThrow(LivingEntity target) {
        this.cancelCast();
        // 投枪前清除旧路点，避免后退传送后控制器又飞回目标身边。
        this.resetFlightControl();
        this.setDeltaMovement(Vec3.ZERO);
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
            new ItemStack(IronsSpellbooksCompatItems.STAR_SHADOW_SPEAR.get()));
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
        NpcMerchantTrading.releaseAbsentTrader(this);
        // 每 tick 清除燃烧状态，不影响火系法术的直接伤害。
        if (this.getRemainingFireTicks() > 0) {
            this.clearFire();
        }
        // 这两条必须挂在 aiStep 而不是 customServerAiStep 上。
        // LivingEntity.aiStep 里 isImmobile() 为真时整条 serverAiStep 都不跑，
        // 而她恰恰在「坐着」和「战败」这两种状态下都是 immobile ——
        // 台词播报和回秋千的倒计时放那边会永远停在第一 tick。
        // 誓约那一段同样全程坐在秋千上（誓约不改遭遇状态，见 startVowFirst），所以也挂在这儿。
        this.dialogue.tick(this);
        this.tickActionEvents();
        this.tickChallengeStart();
        this.tickVow();
        this.tickGreeting();
        this.tickRetiredMaids();
        this.tickSeatedAnchor();
        this.tickReturnHome();
        // 行礼那段和坐着一样是 immobile，正对目标的逻辑在下面（要 getTarget() 非空）够不着，
        // 所以单独在这儿补一次朝向，见 tickCurtsyLook。
        this.tickCurtsyLook();
        this.entityData.set(BATTLE_MUSIC, !this.isRemoved() && this.isBattleActive());
        if (this.isDefeated()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            // 兜底：正常情况下"脱战没目标"这一段由 tickHoverHold 悬停接管，重力根本没合上，
            // 这里第一句就不成立。留着是防别的路径把重力重新打开（见 tickDescent 的说明）。
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOSS_HEALTH, -1.0F);
        builder.define(BOSS_MAX_HEALTH, -1.0F);
        builder.define(ENCOUNTER_STATE, (byte) WinefoxEncounterState.SEATED.ordinal());
        builder.define(REWARD_STATE, (byte) WinefoxRewardState.NONE.ordinal());
        builder.define(ENCOUNTER_SERIAL, 0);
        builder.define(CURTSYING, false);
        builder.define(ACTION, WinefoxAction.NONE.id());
        builder.define(ACTION_SERIAL, 0);
        builder.define(PHASE_TWO, false);
        builder.define(TRANSITIONING, false);
        builder.define(RESTRICTED, false);
        builder.define(BATTLE_MUSIC, false);
    }

    /**
     * 她还坐在秋千上，没有接受挑战。
     */
    public boolean isSeated() {
        return this.maidspell$encounterState() == WinefoxEncounterState.SEATED
            || this.maidspell$encounterState() == WinefoxEncounterState.CHALLENGE_START;
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
        // 这条会继续从附近的普通生物中选出可打对象。
        //
        // 但正式玩家挑战（challengerId 非空）期间整条关掉：那一场是她和挑战者之间的事，
        // 擂台边上路过的怪不该被她点名。被怪打仍然会还手 —— 那是下面 goal 4 的
        // HurtByTargetGoal，走 canAttack/isViableTarget，和这条「主动挑对象」是两回事。
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class,
            10, true, false, candidate -> this.challengerId == null
                && !(candidate instanceof Player)
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

    /** 坐姿不选目标；正式挑战中到达生命地板的玩家也不再是有效目标。 */
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
                return candidate.getHealth() > duelSurvivalFloor();
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
     * 非玩家生物可开启普通战斗，不享受挑战保护与奖励。
     * @return 这一击能否生效
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
            || this.isDefeated() || this.returnHomeTicks > 0
            // 开场行礼期间不受理：这一段是「挑战已经谈定、只是还没演完」，被路过的怪打断
            // 就等于把一个已经成交的挑战跳过去。她这会儿 isInvulnerableTo 为真，挡下这一击
            // 本来也不会少吃伤害；与 isViableTarget 里同样的 challengeStartTicks 守卫同源。
            || this.challengeStartTicks > 0) {
            return false;
        }
        if (MaidSpellAllyResolver.isOwnedBy(attacker, EntityMaid.class)) {
            // 主人取不到（离线之类）时连这一击都不算数：普通生物战斗是留给普通生物的，
            // 女仆不论如何都不该被拖进去。
            Player maidOwner = MaidSpellAllyResolver.maidOwningPlayer(attacker);
            return maidOwner != null && this.beginMaidOwnerCombat(maidOwner, attacker);
        }
        if (this.isSeated()) {
            if (!this.maidspell$beginEncounter(WinefoxEncounterState.COMBAT)) {
                return false;
            }
            this.resetBattleTally();
            this.challengeStartTicks = 0;
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
     * 坐姿时拒绝女仆伤害；战斗中仅在主人可参与时接纳女仆并记下挑战者。
     * @return 这一击能否生效
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
            && (owner.isCreative() || owner.getHealth() > duelSurvivalFloor())
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
        WinefoxChallengeProgress.markChallengeActive(this.getServer(), owner.getUUID());
        this.hasStartedChallenge = true;
        this.resetBattleTally();
        this.entityData.set(RESTRICTED, false);
        this.entityData.set(BATTLE_MUSIC, true);
        this.bossEvent.setVisible(true);
        // 这里原先会把交易表置空，「限制标志刚被清零、旧表作废」。但限制标志影响的只是补给那一列的
        // 价格档，<b>报价集合本身没变</b>；置空的真正后果是把当天的限购次数一起抹掉 ——
        // 买完法帽再邀战一次、再买一顶。现在表由 getOffers() 按 tradingUnlocked 的变化决定要不要重算，
        // 这里只把交易界面收掉。
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
            && player.getHealth() <= duelSurvivalFloor()) {
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

    /** 按 WinefoxAction 的事件时间派发当前 tick 的副作用。 */
    private void tickActionEvents() {
        int elapsed = this.tickCount - this.actionStartTick;
        WinefoxAction action = this.currentAction();
        for (WinefoxAction.Event event : action.events()) {
            if (elapsed != event.tick()) {
                continue;
            }
            switch (event.kind()) {
                case SOUND -> {
                    SoundEvent sound = com.github.yimeng261.maidspell.sound.MaidSpellSounds.getWinefoxSound(event.sound());
                    if (sound != null) this.playSound(sound, 1.0F, 1.0F);
                }
                case SOUL_CHARM -> this.throwVowCharm();
                default -> {
                }
            }
        }
    }

    /** 玩家挑战结束时播放胜负提示音；普通生物战斗静默收场。 */
    private void playCombatEndCue() {
        // 只放服务端：客户端自己也有这只实体，两端都放等于听两遍。
        if (this.level().isClientSide) {
            return;
        }
        this.level().playSound(null, this.blockPosition(),
            SoundEvents.BELL_BLOCK, this.getSoundSource(), 1.0F, 1.0F);
    }

    /** 避免未结束的动作反复重载动画；只限制新动画，不限制攻击伤害。 */
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

    /** 法术失败时的远程兜底；不调用 swing，以免播放近战动画。 */
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, distanceFactor, null);
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

    /**
     * 投枪前至少要退开这么多格，见 {@link #teleportAwayFrom(LivingEntity, double, double)}。
     *
     * <p>投枪是远程点名技，贴脸甩出去就只是白白挨一刀换一下普攻。场地狭窄（房间、地道、
     * 建筑群）时 15 格外的落点可能全被占住，这时候退到 3 格也照投 —— 退不开就不投，
     * 见 {@code WinefoxCombatGoal} 里那一处。
     */
    static final double SPEAR_MIN_RETREAT_DISTANCE = 3.0D;

    boolean teleportAwayFrom(LivingEntity target, double distance) {
        return this.teleportAwayFrom(target, distance, 0.0D);
    }

    /**
     * 往远离目标的方向传送，优先落在 {@code distance} 格远处。
     *
     * <p>落点按 {@code distance} 的 100%/85%/70%/55%/40% 逐级降格去找，最后再试
     * {@code minDistance} —— 场地里摆着方块时，命中率靠的就是这一串降级尝试。
     * 所有距离 × 所有高度差都被占住才返回 {@code false}，调用方可以据此决定要不要放弃这一手。
     *
     * @param minDistance 最低退距；传 0 表示允许一路降到 {@code distance} 的 40%
     */
    boolean teleportAwayFrom(LivingEntity target, double distance, double minDistance) {
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
        double previousDistance = Double.MAX_VALUE;
        for (double distanceScale : distanceScales) {
            // 降级也不许退到 minDistance 以内：那一档对调用方等于没退开，
            // 与其投在人家脸上，不如干脆报失败，让调用方另想办法。
            double fallbackDistance = Math.max(distance * distanceScale, minDistance);
            if (fallbackDistance <= 0.0D || fallbackDistance >= previousDistance) {
                // 第二条件是给 minDistance 兜底那一档去重（它会让好几个 scale 算出同一个点）。
                continue;
            }
            previousDistance = fallbackDistance;
            Vec3 horizontal = away.scale(fallbackDistance);
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

    /** 战败后在 isImmobile 层停掉整套 AI，同时保留 travel 的重力。 */
    @Override
    protected boolean isImmobile() {
        return this.isDefeated() || this.isSeated() || this.returnHomeTicks > 0 || super.isImmobile();
    }

    /** 坐姿时接受「归星」驯服、星芒短剑邀战、日记兑换和普通交易。 */
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
        // 「归星」插在邀战之前：它只看手里拿着什么，与短剑、日记、绿宝石三条路互不相交，
        // 放前面只是让驯服的两个手势在源码里挨着，读起来是一件事。
        if (held.is(MaidSpellItems.RETURNING_STAR.get())) {
            if (!this.level().isClientSide && !player.isSpectator()) {
                this.handleReturningStar(player, held);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (held.is(MaidSpellItems.STARGLINT_DAGGER.get())) {
            if (player.isShiftKeyDown()) {
                if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    StarglintDaggerItem.openChallengeConfig(serverPlayer, hand, held);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (!this.level().isClientSide && !player.isSpectator()
                && (player.isCreative() || player.getHealth() > duelSurvivalFloor())) {
                this.acceptChallenge(player, held);
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
            } else if (this.vowSealed) {
                // 誓约已成之后她换了个语气说话：闲谈那一池让位给新台词。
                // 排在 postVictoryChatPlayerId 之后 —— 那一条是"刚打完还没回过味来"的一次性话，
                // 该先说掉；两者本来也不冲突，谁先点谁先听到。
                line = WinefoxDialogue.tamedChatLine();
            } else {
                line = WinefoxDialogue.randomChatLine(this.hasNearbyOwnedMaid(player));
            }
            WinefoxDialogue.sendToPlayer(player, line);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ==================== 归星与第一次驯服 ====================

    /** 处理归星右击：检查胜利资格，推进誓约阶段，并在动作栏提示结果。 */
    private void handleReturningStar(Player player, ItemStack held) {
        if (this.vowSealed) {
            this.hint(player, "entity.touhou_little_maid_spell.stellar_witch.vow_already_bound");
            return;
        }
        if (this.playerWinCount < VOW_REQUIRED_PLAYER_WINS || !this.soloWinAfterThree) {
            this.hint(player, "entity.touhou_little_maid_spell.stellar_witch.vow_conditions_unmet");
            return;
        }
        if (this.vowStage == VOW_STAGE_IDLE) {
            this.startVowFirst();
            return;
        }
        if (this.vowStage == VOW_STAGE_FIRST) {
            this.startVowSecond(player, held);
        }
        // 第二问正在演：那 9 秒里再点几下都不作数，道具也不会被多扣一枚。
    }

    /** 一句落到屏幕中下方的提示，见 {@link #handleReturningStar}。 */
    private void hint(Player player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), true);
    }

    /** 第一问保持坐姿并启动 vow_1；计时由 aiStep 推进。 */
    private void startVowFirst() {
        this.vowStage = VOW_STAGE_FIRST;
        this.vowTicks = WinefoxAction.VOW_1.durationTicks();
        this.beginAction(WinefoxAction.VOW_1);
        this.speakDialogue(WinefoxDialogue.vowFirstLines(), VOW_DIALOGUE_INTERVAL_TICKS);
        this.level().playSound(null, this.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME, this.getSoundSource(), 1.0F, 0.8F);
    }

    /**
     * 第二问：收下那枚「归星」、起 {@code vow_2}，誓约当场成立，魂符在第 120t 交出去。
     *
     * <p>誓约在<b>这一下</b>就落定（{@link #vowSealed}），不等 9 秒演完：道具已经付了，
     * 后面那段表演只是把她说过的话演出来，中途被打断（普通生物偷袭、管理员指令）
     * 不该让玩家再掏一枚归星。
     *
     * <p>只有从第一问的窗口里才进得来，所以这里不再查胜利场次。
     */
    private void startVowSecond(Player player, ItemStack held) {
        this.vowSealed = true;
        this.vowCharmGiven = false;
        this.vowPlayerId = player.getUUID();
        this.vowStage = VOW_STAGE_SECOND;
        this.vowTicks = WinefoxAction.VOW_2.durationTicks();
        this.beginAction(WinefoxAction.VOW_2);
        this.speakDialogue(WinefoxDialogue.vowSecondLines(), VOW_DIALOGUE_INTERVAL_TICKS);
        // 排在最后：上面几句都只是内存与同步写，不会失败；道具扣掉之后再出事，
        // 玩家至少已经拿到了誓约（vowSealed 与 vowPlayerId 都落 NBT）。
        // 直接改手里那一份 ItemStack，与日记兑换同一写法：服务端拿到的是背包里那个对象。
        held.shrink(1);
    }

    /**
     * 誓约进行中每 tick 走一次，数到点就收场。
     *
     * <p>只认「还坐在秋千上」这一种。她要是被拖进战斗（普通生物偷袭、玩家递上短剑），
     * 遭遇状态就离开了 {@code SEATED}，整段誓约当场作废 —— 不然一段 15 秒的循环动画会跟着她
     * 一起飞进战场，而 {@code magic_casting} 通道那时正该让给施法。
     * 作废<b>不撤销</b> {@link #vowSealed}：第二问那一枚道具已经花掉了。
     */
    private void tickVow() {
        if (this.vowStage == VOW_STAGE_IDLE) {
            return;
        }
        if (this.maidspell$encounterState() != WinefoxEncounterState.SEATED) {
            this.cancelVow();
            return;
        }
        if (this.vowTicks <= 0 || --this.vowTicks > 0) {
            return;
        }
        // 第一问的 15 秒窗口到此为止：她收回这番心意，动作停掉（循环动画只有从 CASTING 落到 NONE
        // 才停得住，见 WinefoxActionAnimationProvider），玩家可以重新递一枚归星再来一次。
        // 第二问演完则什么都不必做：vowSealed 早在第二次右击那一下就落下了。
        this.cancelVow();
    }

    /** 收掉正在演的那一段誓约，回到「没在演」。 */
    private void cancelVow() {
        if (this.vowStage == VOW_STAGE_IDLE) {
            return;
        }
        // 第二问被打断在第 120t 之前的话，魂符还没交出去，而道具早就扣了 —— 由这里补上，
        // 不然玩家会为一枚根本没到手的魂符白掏一枚归星。已经交过就什么都不做（vowCharmGiven 拦着）。
        if (this.vowSealed && !this.vowCharmGiven) {
            this.throwVowCharm();
        }
        this.vowStage = VOW_STAGE_IDLE;
        this.vowTicks = 0;
        // 只收自己那两条轨道：被邀战顶掉时当前动作已经是 curtsy_combat，收错了就把开场行礼也掐了。
        WinefoxAction action = this.currentAction();
        if (action == WinefoxAction.VOW_1 || action == WinefoxAction.VOW_2) {
            this.clearAction();
        }
    }

    /**
     * 誓约动画出手后延迟发放已绑定主人的魂符和蛋糕。
     * 延迟任务按 UUID 重新查玩家，避免捕获可能卸载的实体。
     */
    private void throwVowCharm() {
        if (this.level().isClientSide || this.vowCharmGiven || this.vowPlayerId == null) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.vowCharmGiven = true;
        UUID playerId = this.vowPlayerId;
        // 出手的观感：信物从她手边飞出，散一段星尘；到手时在玩家身上再补一次。
        serverLevel.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(0.8D), this.getZ(),
            12, 0.25D, 0.25D, 0.25D, 0.02D);
        this.playSound(SoundEvents.ENDER_EYE_LAUNCH, 1.0F, 1.4F);
        DelayedServerTasks.schedule(serverLevel, VOW_CHARM_FLIGHT_TICKS, () -> {
            // 玩家可能在延迟期间下线。
            ServerPlayer receiver = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (receiver == null) {
                return;
            }
            ItemStack soulCharm = StellarWitchStarterMaid.createSoulCharm(playerId);
            if (!soulCharm.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(receiver, soulCharm);
            } else {
                // 模板缺失时给替代信物并提示玩家。
                ItemHandlerHelper.giveItemToPlayer(receiver, new ItemStack(InitItems.SMART_SLAB_EMPTY.get()));
                receiver.displayClientMessage(Component.translatable(
                        "entity.touhou_little_maid_spell.stellar_witch.vow_gift_missing"), false);
            }
            // 蛋糕始终发放；模板缺失时上面的空魂符代替已装魂符。
            ItemHandlerHelper.giveItemToPlayer(receiver, new ItemStack(Items.CAKE));
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                receiver.getX(), receiver.getY(0.9D), receiver.getZ(),
                16, 0.3D, 0.4D, 0.3D, 0.02D);
        });
    }

    // ==================== 战败之后的交易 ====================

    private void startTrading(Player player) {
        WinefoxTrades.refreshDailyQuota(this.getOffers());
        NpcMerchantTrading.startTrading(this, player, 1);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    /** 授权清除、卸载或换维度后，旧实体不能再成交。 */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        this.setTradingPlayer(null);
    }

    /** 缓存有状态的报价表；解锁新报价时重建并保留已有使用次数。 */
    @Override
    public @NotNull MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = WinefoxTrades.build(this.tradingUnlocked);
            this.offersBuiltForUnlocked = this.tradingUnlocked;
        } else if (this.offersBuiltForUnlocked != this.tradingUnlocked) {
            // 解锁新报价时不能重置旧商品的限购次数。
            this.offers = WinefoxTrades.rebuildKeepingUses(this.offers, this.tradingUnlocked);
            this.offersBuiltForUnlocked = this.tradingUnlocked;
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
     *
     * <p>入场先演一段 {@code curtsy_combat}（见 {@link WinefoxAction#CURTSY_COMBAT}）：倒计时长度直接取枚举里那一份，
     * 于是「动画播完」和「切进 COMBAT」落在同一个 tick 上 —— 表演没完就开打，或者演完了她还坐着，都不会发生。
     * 动画走 {@code magic_casting} 通道，压得住她这会儿 {@code main} 通道上的坐姿，所以座位锚定照旧（见 {@link #tickSeatedAnchor}），
     * 不必为了这段表演把她从秋千上放下来。
     */
    private void acceptChallenge(Player challenger, ItemStack invitation) {
        if (!this.maidspell$beginEncounter(WinefoxEncounterState.CHALLENGE_START)) {
            return;
        }
        this.customChallenge = WinefoxChallengeConfig.hasPendingConfig(invitation);
        this.activeChallengeConfig = WinefoxChallengeConfig.takeFromItem(invitation);
        challenger.getInventory().setChanged();
        this.maidspell$setMaxHealth((float) this.activeChallengeConfig.maxHealth());
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.RESET,
                () -> this.setHealth(this.maidspell$bossMaxHealth()));
        com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxSpellPowerBonus.apply(this);
        this.speakDialogue(WinefoxDialogue.challengeAccepted(!this.hasStartedChallenge));
        this.hasStartedChallenge = true;
        this.resetBattleTally();
        this.entityData.set(RESTRICTED, false);
        this.entityData.set(CURTSYING, false);
        // 同 adoptChallengeFrom：置空会连当天限购次数一起抹掉，而报价集合并没有变。
        this.setTradingPlayer(null);
        this.challengerId = challenger.getUUID();
        WinefoxChallengeProgress.markChallengeActive(this.getServer(), challenger.getUUID());
        this.challengeStartTicks = WinefoxAction.CURTSY_COMBAT.durationTicks();
        this.beginAction(WinefoxAction.CURTSY_COMBAT);
        this.level().playSound(null, this.blockPosition(),
            SoundEvents.BEACON_ACTIVATE, this.getSoundSource(), 1.0F, 1.2F);
    }

    private void tickChallengeStart() {
        // 只认「还停在开场状态」这一种。管理员指令、未归属伤害结算（见 maidspell$handleUnattributedDefeat）
        // 都可能在这一段里直接把她推进 RETURNING，那时候倒计时要是照走，走到 0 只会去撞一条
        // RETURNING -> COMBAT 的非法转移（状态机拒掉并记 WARN），开场动作也早该跟着收掉。
        if (this.maidspell$encounterState() != WinefoxEncounterState.CHALLENGE_START) {
            this.challengeStartTicks = 0;
            if (this.currentAction() == WinefoxAction.CURTSY_COMBAT) {
                this.clearAction();
            }
            return;
        }
        if (this.challengeStartTicks <= 0 || --this.challengeStartTicks > 0) {
            return;
        }
        Player challenger = this.challengerId == null ? null : this.level().getPlayerByUUID(this.challengerId);
        if (challenger == null || !challenger.isAlive() || challenger.isSpectator()
            || this.distanceToSqr(challenger) > CHALLENGER_MAX_DISTANCE * CHALLENGER_MAX_DISTANCE
            || challenger.getHealth() <= duelSurvivalFloor()) {
            WinefoxChallengeProgress.clearChallengeActive(this.getServer(), this.challengerId);
            this.challengerId = null;
            // 表演中途作废也要把动作收掉：动作是同步值，挂着不放，magic_casting 通道就会继续拿这段行礼
            // 盖住她回坐姿后的 sit，玩家看到的是一边坐着一边鞠躬。
            this.clearAction();
            this.maidspell$setEncounterState(WinefoxEncounterState.SEATED);
            this.activeChallengeConfig = null;
            this.customChallenge = false;
            this.maidspell$setMaxHealth((float) Config.winefoxMaxHealth);
            com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxSpellPowerBonus.apply(this);
            return;
        }
        this.maidspell$setEncounterState(WinefoxEncounterState.COMBAT);
        // 开场动作到此为止：不清的话，通道会一直拿它当「当前动作」，战斗第一条动作要等序号变化才抢得回来。
        this.clearAction();
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

    private void speakDialogue(List<Component> lines, int intervalTicks) {
        this.resetAmbientDialogueCooldown();
        this.dialogue.speak(lines, intervalTicks);
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
     * 这位玩家听过初见了没有。
     *
     * <p>初见记录本身是私有的；这里开一个只读出口，便于外部（调试、集成检查）确认
     * 「存档往返之后记录还在不在」，也就是玩家跨维度或重登后会不会再听一遍。
     */
    public boolean maidspell$hasGreetedPlayer(UUID playerId) {
        return this.greetedPlayers.contains(playerId);
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
        boolean wasCasting = this.isCasting();
        int castTicksRemaining = this.getMagicData().getCastDurationRemaining();
        super.customServerAiStep();
        // AbstractSpellCastingMob performs the spell's onCast callback and
        // clears its MagicData in this method. A transition from one tick of
        // casting to idle therefore marks a real release, while a cancelled
        // long cast still has more than one tick remaining and is ignored.
        if (wasCasting && !this.isCasting() && castTicksRemaining <= 1
                && this.isBattleActive() && !this.isTransitioning()) {
            WinefoxSpellMimic.afterSpell(this);
        }
        // 坐着的情况不在这儿处理：isImmobile() 为真时整条 serverAiStep 都不跑，
        // 写在这里的分支永远到不了。那一段归 tickSeatedAnchor。
        if (this.returnHomeTicks > 0) {
            // 已经宣布收场、正在回秋千的路上，这 60t 里不许再锁新目标：
            // 锁了也会在 tickReturnHome 那一刻被回满血抹掉，等于白打一场。
            this.setTarget(null);
            return;
        }
        this.bossEvent.setProgress(this.maidspell$authoritativeHealth() / this.maidspell$bossMaxHealth());
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
        // 战斗暂失目标时悬停；转入 RETURNING 后恢复重力供行礼落地。
        boolean hoverHold = !combatFlight && this.isBattleActive();
        this.setNoGravity(combatFlight || hoverHold);
        // tickAscent 必须在 setNoGravity 之后算：她只在无重力时读取 fastAscending，
        // 而这一位是 travel 的垂直分支要用的，得在本 tick 的 travel 之前定下来。
        if (combatFlight) {
            this.tickAscent();
            this.resetFallDistance();
        } else {
            this.fastAscending = false;
            if (hoverHold) {
                this.tickHoverHold();
            }
        }
    }

    /** 每场战斗只触发一次半血转阶段。 */
    private void tickPhaseThresholds() {
        if (this.isTransitioning()) {
            return;
        }
        float healthFraction = this.maidspell$authoritativeHealth() / this.maidspell$bossMaxHealth();
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

    /** 覆盖主动索敌与受击还击，防止到达生命地板的玩家重新成为目标。 */
    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        return this.isBattleActive() && super.canAttack(target) && this.isViableTarget(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(this.isBattleActive() ? target : null);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return this.maidspell$encounterState() != WinefoxEncounterState.COMBAT
            || this.isTransitioning() || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved();
    }

    @Override
    public boolean isDeadOrDying() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return this.isBattleActive() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && !this.isRemoved();
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return this.isBattleActive() && super.canBeAffected(effect);
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    @Override
    @Nullable
    public Entity changeDimension(DimensionTransition transition) {
        return null;
    }

    @Override
    public boolean teleportTo(ServerLevel destination, double x, double y, double z,
                              Set<net.minecraft.world.entity.RelativeMovement> relative,
                              float yaw, float pitch) {
        return destination == this.level()
            && super.teleportTo(destination, x, y, z, relative, yaw, pitch);
    }

    @Override
    public void kill() {
        if (!this.level().isClientSide) {
            this.maidspell$returnAuthorized();
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            this.maidspell$returnAuthorized();
        }
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    }

    @Override
    protected void dropEquipment() {
    }

    @Override
    protected void dropExperience(@Nullable Entity attacker) {
    }

    /** 正常伤害走原版结算，再用权威 Boss 生命的归零结果提交自定义战败。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || !Float.isFinite(amount) || amount <= 0.0F) {
            return false;
        }
        // 仅无归属的 out_of_world 伤害视为意外掉出世界；法术借用此类型但带施法者。
        boolean unattributed = source.getEntity() == null;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) && unattributed
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && unattributed) {
            this.maidspell$returnAuthorized();
            return false;
        }
        if (this.isDefeated() || this.returnHomeTicks > 0) {
            return false;
        }
        LivingEntity attacker = mobAttacker(source);
        if (this.isSeated()) {
            if (attacker == null || !this.beginMobCombat(attacker)) {
                return false;
            }
        } else if (attacker != null) {
            this.beginMobCombat(attacker);
        }
        boolean playerDuelDamage = this.isPlayerDuelDamage(source);
        boolean anyMaidDamage = isMaidDamage(source);
        boolean maidDamage = playerDuelDamage && anyMaidDamage;
        // 受击间隔在 super.hurt 前判定，避免被挡伤害仍触发动画与击退。
        if (WinefoxBossHealthController.wouldBlockHitInterval(this)) {
            return false;
        }
        float healthBefore = this.maidspell$authoritativeHealth();
        // 倍率、单次上限与受击间隔都由控制器执行 —— 那是所有写血路径的汇合点，
        // 放在这里只是上游又一道可以被绕过的检查。这里只负责开上下文与读结果。
        final boolean[] vanillaApplied = new boolean[1];
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.DAMAGE,
            () -> vanillaApplied[0] = WinefoxBossHealthController.applyDamage(this, source, amount, maidDamage));
        boolean dealt = this.maidspell$authoritativeHealth() < healthBefore;
        if (dealt && anyMaidDamage) {
            this.maidDamageOccurred = true;
        }
        if (playerDuelDamage) {
            this.recordDamageShare(maidDamage, healthBefore - this.maidspell$authoritativeHealth());
        }
        if (this.maidspell$authoritativeHealth() <= 0.0F) {
            // 脱战时的「生命归零」不是战败：她是被路过的伤害打空的，没人打赢这一场。
            // 判战败会白播一遍战败演出、把结算记成 NOT_ELIGIBLE，还得再等一个归位倒计时；
            // 直接走归位，当tick回到秋千、坐好、满血、退回一阶段。
            //
            // 这一处覆盖两条写血路径：原版伤害链（super.hurt → setHealth → 控制器 write）
            // 与真伤回流（{@link #maidspell$redirectTrueDamage} 最终也是调本方法）。
            if (this.isOutOfCombat()) {
                this.retreatHomeInsteadOfDefeat();
            } else {
                this.beginDefeat(source);
            }
        }
        // 被受击间隔挡下时这一击没有产生任何效果，必须报 false：
        // 真伤据此回滚"本场用过真伤"标记，否则一次打空的真伤会白白剥夺奖励。
        return vanillaApplied[0] && dealt;
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

    /** 真伤经 hurt 结算，保留减伤、归属和战败规则，并记录本场真伤使用。 */
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

    /** 保留原版护甲、抗性和吸收结算；生命写入由权威控制器接管。 */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (WinefoxBossHealthController.currentCause(this) == WinefoxBossHealthController.Cause.DAMAGE) {
            super.actuallyHurt(source, amount);
            return;
        }
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.EXTERNAL_UNKNOWN,
            () -> super.actuallyHurt(source, amount));
    }

    /**
     * 收场的**唯一**共用清理例程：战败与异常回归都必须经过它，两处不得各写一份。每一项幂等。
     *
     * <p>只放"任何一次战斗结束都要做"的事。以下三项属于各自的收场语义，刻意留在调用点：
     * 收武器与加无敌（只有战败演出需要躺在地上）、{@code setNoGravity}（与前者配套）、
     * 行礼位与归位倒计时（两种演出的时长与台词不同）。
     */
    private void maidspell$teardownCombat() {
        this.cancelCast();
        this.cancelSpearThrow();
        this.clearAction();
        this.recallSummons();
        this.clearMaidAggro();
        this.releaseRetiredMaids();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFlightControl();
        this.setTarget(null);
        this.entityData.set(BATTLE_MUSIC, false);
        this.bossEvent.setVisible(false);
    }

    /** 权威生命归零后提交战败；动画由同步遭遇状态驱动而非原版死亡计时。 */
    private void beginDefeat(@Nullable DamageSource source) {
        if (this.isDefeated() || this.maidspell$encounterState() != WinefoxEncounterState.COMBAT) {
            return;
        }
        boolean playerVictory = source != null && this.isPlayerDuelDamage(source);
        if (playerVictory && this.challengerId != null) {
            this.postVictoryChatPlayerId = this.challengerId;
            WinefoxChallengeProgress.markDefeated(this.getServer(), this.challengerId);
        }
        this.maidspell$setEncounterState(WinefoxEncounterState.DEFEATED);
        // 她打输了 —— 与「玩家打输」那一路各放一次提示音，见 playCombatEndCue。
        this.playCombatEndCue();
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.SCRIPTED_TRANSITION,
            () -> this.setHealth(0.0F));
        this.dead = false;
        this.deathTime = 0;
        this.entityData.set(TRANSITIONING, false);
        this.phaseTransitionTicks = 0;
        // 她输了，挑战者身上的火和负面效果一并收掉：这一场已经结束，
        // 不该让残留在人身上的火在战败演出里把人烧死。
        this.clearDuelHazards();
        this.maidspell$teardownCombat();
        // 只有战败演出要躺在地上：收起武器、落到地面、进入无敌。
        // 这些不进共用清理，否则玩家打赢那一路也会把她按在地上。
        this.dropWeaponForDefeat();
        this.setNoGravity(false);
        this.setInvulnerable(true);
        if (playerVictory && this.maidspell$rewardState() == WinefoxRewardState.NONE) {
            this.entityData.set(RESTRICTED, this.computeRestricted());
            // 这一场是不是「常规手段赢的」就在这一行定案，驯服的门槛与交易解锁共用同一个判据。
            // 记在场次判定的<b>当时</b>而不是奖励发完之后：奖励那一整段（掉战利品、说战败台词）
            // 任何一句出错都会跳到下面的 catch，而赢就是赢，不该因为发奖失败少算一场。
            if (!this.isRestricted()) {
                if (this.playerWinCount >= VOW_REQUIRED_PLAYER_WINS && !this.maidDamageOccurred) {
                    this.soloWinAfterThree = true;
                }
                this.playerWinCount++;
            }
            this.rewardTransaction = UUID.randomUUID();
            this.maidspell$setRewardState(WinefoxRewardState.GRANTING);
            try {
                this.dropDefeatRewards(source);
                this.maidspell$setRewardState(WinefoxRewardState.GRANTED);
                this.tradingUnlocked |= !this.isRestricted();
                this.speakDialogue(WinefoxDialogue.victory(this.isRestricted(),
                    this.trueDamageUsed && Config.winefoxTrueDamageRestrictsReward));
            } catch (RuntimeException exception) {
                com.github.yimeng261.maidspell.Global.LOGGER.error(
                    "Winefox reward {} needs administrator review", this.rewardTransaction, exception);
            }
        } else if (this.maidspell$rewardState() == WinefoxRewardState.NONE) {
            this.maidspell$setRewardState(WinefoxRewardState.NOT_ELIGIBLE);
        }
        // 不置空交易表：这场胜利可能刚把 tradingUnlocked 翻成 true（上面那一行 |=），
        // 而 getOffers() 会自己发现这个变化并把「战胜后才摆出来」的那一档补上，
        // 同时保住已经卖掉的次数。以前在这里置空 = 每打赢一场就把当天限购刷一遍。
        this.returnHomeTicks = DEFEAT_RETURN_HOME_TICKS;
    }

    /**
     * 发战败奖励。
     *
     * <p><b>必须自己调 {@code dropFromLootTable}。</b>原版死亡流程已被禁用，
     * 只有有归属的合法战败才能调用这个奖励入口。
     *
     * <p>星云核心不走战利品表而是直接落地；女仆代打或使用自定义配置的挑战均不掉落。
     */
    private void dropDefeatRewards(DamageSource source) {
        this.dropFromLootTable(source, this.lastHurtByPlayerTime > 0);
        if (!this.isRestricted() && !this.customChallenge) {
            this.spawnAtLocation(new ItemStack(MaidSpellItems.NEBULA_CORE.get()));
        }
    }

    /** 坐姿在 aiStep 锚定；结构生成可能跳过 finalizeSpawn，首次坐定时补记 homePos。 */
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

    /** 行礼时目标已清空，优先朝向挑战者，找不到时朝向附近玩家。 */
    private void tickCurtsyLook() {
        // 开场用剩下的倒计时判定而不是当前动作：动作是同步值，读档补挂之前有半帧是 NONE，
        // 而倒计时是服务端的权威计时，与 acceptChallenge 同时起步。
        if (!this.isCurtsying() && this.challengeStartTicks <= 0) {
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

    /** 战败演出后传送回秋千并切回坐姿；DEFEATED 只是演出状态。 */
    private void tickReturnHome() {
        if (this.returnHomeTicks <= 0 || --this.returnHomeTicks > 0) {
            return;
        }
        if (this.isDefeated()) {
            this.maidspell$setEncounterState(WinefoxEncounterState.RETURNING);
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
        this.maidspell$setEncounterState(WinefoxEncounterState.SEATED);
        this.entityData.set(CURTSYING, false);
        this.entityData.set(BATTLE_MUSIC, false);
        WinefoxChallengeProgress.clearChallengeActive(this.getServer(), this.challengerId);
        this.challengerId = null;
        this.challengeStartTicks = 0;
        this.clearAction();
        this.setTarget(null);
        this.setLastHurtByMob(null);
        this.removeAllEffects();
        // 回满权威生命并退回一阶段，下一场挑战才从完整状态开始。
        this.activeChallengeConfig = null;
        this.maidspell$setMaxHealth((float) Config.winefoxMaxHealth);
        com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxSpellPowerBonus.apply(this);
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.RESET,
            () -> this.setHealth(this.maidspell$bossMaxHealth()));
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
            if (challenger != null && challenger.getHealth() <= duelSurvivalFloor()) {
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
        if (this.maidspell$encounterState() == WinefoxEncounterState.RETURNING || this.isDefeated()) {
            return;
        }
        this.maidspell$setEncounterState(WinefoxEncounterState.RETURNING);
        if (this.maidspell$rewardState() == WinefoxRewardState.NONE) {
            this.maidspell$setRewardState(WinefoxRewardState.NOT_ELIGIBLE);
        }
        this.noTargetTicks = 0;
        if (playerChallenge) {
            // 玩家打输了。这是「分出胜负」两种收场之一，另一路是她自己倒下（见 beginDefeat）；
            // playerChallenge 为 false 的静默收场刻意不放，理由写在 playCombatEndCue。
            this.playCombatEndCue();
            this.clearDuelHazards();
            this.speakDialogue(WinefoxDialogue.playerSubdued());
        }
        // 与战败共用同一份清理（这里是"打服/收场"，她仍站着，所以不收武器、不加无敌、
        // 不动重力），差别只在演出时长与台词。
        this.maidspell$teardownCombat();
        this.entityData.set(CURTSYING, playerChallenge);
        this.returnHomeTicks = playerChallenge ? CURTSY_RETURN_HOME_TICKS : DEFEAT_RETURN_HOME_TICKS;
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
        maid.setHealth(duelSurvivalFloor());
        maid.clearFire();
        for (MobEffectInstance effect : List.copyOf(maid.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
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
        // 坐下时切到空闲，停止不受坐姿约束的远程任务。
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
        maid.setHealth(duelSurvivalFloor());
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
                if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                    player.removeEffect(effect.getEffect());
                }
            }
        }
    }

    public boolean isBattleActive() {
        return this.maidspell$encounterState() == WinefoxEncounterState.COMBAT;
    }

    public boolean isChallengeOngoingFor(Player player) {
        WinefoxEncounterState state = this.maidspell$encounterState();
        return this.challengerId != null && this.challengerId.equals(player.getUUID())
                && (state == WinefoxEncounterState.CHALLENGE_START || state == WinefoxEncounterState.COMBAT);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!this.level().isClientSide) {
            this.restoreChallengeActiveMark();
        }
    }

    /**
     * 实体加入世界时补记挑战中标记。挑战者和阶段随实体存档，进度存档里的标记可能对不上：
     * 旧版本记在玩家身上的标记升级时已丢弃，崩服时实体区块和进度存档也可能不同步。
     */
    private void restoreChallengeActiveMark() {
        WinefoxEncounterState state = this.maidspell$encounterState();
        if (this.challengerId != null
                && (state == WinefoxEncounterState.CHALLENGE_START || state == WinefoxEncounterState.COMBAT)) {
            WinefoxChallengeProgress.markChallengeActive(this.getServer(), this.challengerId);
        }
    }

    @Nullable
    WinefoxChallengeConfig maidspell$challengeConfig() {
        return this.activeChallengeConfig;
    }

    @Nullable
    Player maidspell$challenger() {
        return this.challengerId == null ? null : this.level().getPlayerByUUID(this.challengerId);
    }

    public double maidspell$damageMultiplier() {
        return this.activeChallengeConfig != null && this.isPlayerCombatActive()
                ? this.activeChallengeConfig.damageMultiplier() : Config.winefoxDamageMultiplier;
    }

    public double maidspell$spellPowerMultiplier() {
        return this.activeChallengeConfig != null && this.isPlayerCombatActive()
                ? this.activeChallengeConfig.spellPowerMultiplier() : Config.winefoxSpellPowerMultiplier;
    }

    public double maidspell$hitDamageCapRatio() {
        return this.activeChallengeConfig != null && this.isPlayerCombatActive()
                ? this.activeChallengeConfig.hitDamageCapRatio() : Config.winefoxHitDamageCapRatio;
    }

    public int maidspell$hitIntervalTicks() {
        return this.activeChallengeConfig != null && this.isPlayerCombatActive()
                ? this.activeChallengeConfig.hitIntervalTicks() : Config.winefoxHitIntervalTicks;
    }

    public double maidspell$maidDamageMultiplier() {
        return this.activeChallengeConfig != null && this.isPlayerCombatActive()
                ? this.activeChallengeConfig.maidDamageMultiplier() : Config.winefoxMaidDamageMultiplier;
    }

    public double maidspell$damageToMaidMultiplier() {
        return this.activeChallengeConfig != null && this.isPlayerCombatActive()
                ? this.activeChallengeConfig.damageToMaidMultiplier() : Config.winefoxDamageToMaidMultiplier;
    }

    public double maidspell$phaseTwoDamageMultiplier() {
        return this.activeChallengeConfig != null && this.isPlayerCombatActive()
                ? this.activeChallengeConfig.phaseTwoDamageMultiplier() : Config.winefoxPhaseTwoDamageMultiplier;
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
        this.maidDamageOccurred = false;
        this.trueDamageUsed = false;
    }

    /** 读档后修正旧版战败实体的持物、重力和生命值状态。 */
    private void normalizeDefeatState() {
        this.dropWeaponForDefeat();
        this.setNoGravity(false);
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.LOAD,
            () -> this.setHealth(0.0F));
    }

    /** TLM 按真实 ItemStack 渲染持物，战败时须清空主手。 */
    private void dropWeaponForDefeat() {
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /**
     * 战败演出已经开始（含已放完等待移除）。客户端也要能判，动画靠它切 {@code defeat}。
     */
    public boolean isDefeated() {
        return this.maidspell$encounterState() == WinefoxEncounterState.DEFEATED;
    }

    /** 玩家挑战结束后、回秋千之前是否正在行礼。 */
    public boolean isCurtsying() {
        return this.entityData.get(CURTSYING);
    }

    /**
     * 清理召唤物与 recast 记录。ISS 的 removeAll 会以 null 玩家调用召唤法术回调，
     * 而怪物的 recast 记录也不会自行过期。
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

    /** 伤害归属沿弹体和召唤物的主人链追溯，用于识别女仆代打。 */
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
        CompoundTag lifecycle = new CompoundTag();
        lifecycle.putInt("Version", 1);
        lifecycle.putFloat("BossHealth", this.maidspell$authoritativeHealth());
        lifecycle.putFloat("BossMaxHealth", this.maidspell$bossMaxHealth());
        lifecycle.putByte("EncounterState", (byte) this.maidspell$encounterState().ordinal());
        lifecycle.putByte("RewardState", (byte) this.maidspell$rewardState().ordinal());
        lifecycle.putUUID("EncounterId", this.encounterId);
        lifecycle.putInt("EncounterSerial", this.entityData.get(ENCOUNTER_SERIAL));
        if (this.rewardTransaction != null) {
            lifecycle.putUUID("RewardTransaction", this.rewardTransaction);
        }
        tag.put("MaidSpellWinefoxLifecycle", lifecycle);
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
        tag.putBoolean("WinefoxMaidDamageOccurred", this.maidDamageOccurred);
        tag.putBoolean("WinefoxTrueDamageUsed", this.trueDamageUsed);
        tag.putBoolean("WinefoxTradingUnlocked", this.tradingUnlocked);
        // 报价使用次数随 Offers 落 NBT，避免重登后重置每日限购。
        if (this.offers != null && !this.offers.isEmpty()) {
            MerchantOffers.CODEC.encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.offers)
                .resultOrPartial(error -> com.github.yimeng261.maidspell.Global.LOGGER.warn("Failed to save Winefox offers: {}", error))
                .ifPresent(encoded -> tag.put("Offers", encoded));
        }
        tag.putInt(PLAYER_WIN_COUNT_TAG, this.playerWinCount);
        tag.putBoolean(SOLO_WIN_AFTER_THREE_TAG, this.soloWinAfterThree);
        tag.putInt(VOW_STAGE_TAG, this.vowStage);
        tag.putInt(VOW_TICKS_TAG, this.vowTicks);
        tag.putBoolean(VOW_SEALED_TAG, this.vowSealed);
        tag.putBoolean(VOW_CHARM_TAG, this.vowCharmGiven);
        if (this.vowPlayerId != null) {
            tag.putUUID(VOW_PLAYER_TAG, this.vowPlayerId);
        }
        tag.putBoolean("WinefoxHasStartedChallenge", this.hasStartedChallenge);
        tag.putInt("WinefoxChallengeStartTicks", this.challengeStartTicks);
        if (this.activeChallengeConfig != null) {
            tag.put("WinefoxActiveChallengeConfig", this.activeChallengeConfig.toTag());
        }
        tag.putBoolean("WinefoxCustomChallenge", this.customChallenge);
        if (this.challengerId != null) {
            tag.putUUID("WinefoxChallenger", this.challengerId);
        }
        if (this.postVictoryChatPlayerId != null) {
            tag.putUUID("WinefoxPostVictoryChatPlayer", this.postVictoryChatPlayerId);
        }
        if (!this.greetedPlayers.isEmpty()) {
            ListTag greeted = new ListTag();
            for (UUID playerId : this.greetedPlayers) {
                greeted.add(NbtUtils.createUUID(playerId));
            }
            tag.put(GREETED_PLAYERS_TAG, greeted);
        }
        if (this.homePos != null) {
            tag.put("WinefoxHomePos", NbtUtils.writeBlockPos(this.homePos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.loadingLifecycle = true;
        try {
        super.readAdditionalSaveData(tag);
        this.entityData.set(PHASE_TWO, tag.getBoolean("WinefoxPhaseTwo"));
        NbtUtils.readBlockPos(tag, "WinefoxHomePos").ifPresent(pos -> this.homePos = pos);
        // 缺键必须按 true 算，不能吃 getBoolean 的默认 false：/summon 不带 NBT 走的就是这条路，
        // 读出 false 她会站起来主动打人，一枚星云核心都没花就能被杀了掏战利品。
        // 这个分支上没有需要兼容的旧存档（1.9.0-alpha 还没发过）。
        this.entityData.set(RESTRICTED, tag.getBoolean("WinefoxRestricted"));
        this.returnHomeTicks = tag.getInt("WinefoxReturnHomeTicks");
        this.totalDamageTaken = tag.getFloat("WinefoxTotalDamageTaken");
        this.maidDamageTaken = tag.getFloat("WinefoxMaidDamageTaken");
        this.maidDamageOccurred = tag.getBoolean("WinefoxMaidDamageOccurred") || this.maidDamageTaken > 0.0F;
        this.trueDamageUsed = tag.getBoolean("WinefoxTrueDamageUsed");
        this.tradingUnlocked = tag.getBoolean("WinefoxTradingUnlocked");
        // 交易表读回来时<b>按当前代码重造、只把用过的次数搬回去</b>，而不是照用存档里那张。
        // 两个理由：反序列化出来的是普通 MerchantOffers，外层那个负责跨天清零的 DailyQuotaTable
        // 实例身份丢了（不重包就会静默失去跨天恢复）；而且照用旧表的话，
        // 这一版新加的「1 级三矢连星卷轴 / 1 级伴星黑洞卷轴」永远进不了老存档。
        // 键名 {@code Offers} 与写入端、以及 ISS 的 IMerchantWizard 都一致。见 WinefoxTrades#rebuildKeepingUses。
        if (tag.contains("Offers")) {
            MerchantOffers.CODEC.parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get("Offers"))
                .resultOrPartial(error -> com.github.yimeng261.maidspell.Global.LOGGER.warn("Failed to load Winefox offers: {}", error))
                .ifPresent(savedOffers -> {
                    this.offers = WinefoxTrades.rebuildKeepingUses(savedOffers, this.tradingUnlocked);
                    this.offersBuiltForUnlocked = this.tradingUnlocked;
                });
        }
        // 老存档没有这几个键：胜利场次读出 0（那三场得补，本来就没数过），誓约一律回到「没开始」。
        // 两个默认值都是保守的那一侧 —— 缺键绝不会让一只没被驯服过的她凭空带上誓约。
        this.playerWinCount = tag.getInt(PLAYER_WIN_COUNT_TAG);
        this.soloWinAfterThree = tag.getBoolean(SOLO_WIN_AFTER_THREE_TAG);
        this.vowSealed = tag.getBoolean(VOW_SEALED_TAG);
        this.vowCharmGiven = tag.getBoolean(VOW_CHARM_TAG);
        this.vowPlayerId = tag.hasUUID(VOW_PLAYER_TAG) ? tag.getUUID(VOW_PLAYER_TAG) : null;
        // 演出进行到一半被卸载的，这里按剩下的 tick 接着演：倒计时是服务端权威，动画是同步值。
        // 越界、或者读档时窗口已经走完，都收敛成「没在演」—— 下面补挂动画的分支就不会再起动作。
        int storedVowStage = tag.getInt(VOW_STAGE_TAG);
        this.vowTicks = Math.max(0, tag.getInt(VOW_TICKS_TAG));
        this.vowStage = this.vowTicks > 0
            && (storedVowStage == VOW_STAGE_FIRST || storedVowStage == VOW_STAGE_SECOND)
            ? storedVowStage : VOW_STAGE_IDLE;
        if (this.vowStage == VOW_STAGE_IDLE) {
            this.vowTicks = 0;
        }
        this.hasStartedChallenge = tag.getBoolean("WinefoxHasStartedChallenge");
        this.challengeStartTicks = tag.getInt("WinefoxChallengeStartTicks");
        this.activeChallengeConfig = tag.contains("WinefoxActiveChallengeConfig", Tag.TAG_COMPOUND)
                ? WinefoxChallengeConfig.fromTag(tag.getCompound("WinefoxActiveChallengeConfig")) : null;
        this.customChallenge = tag.getBoolean("WinefoxCustomChallenge");
        this.challengerId = tag.hasUUID("WinefoxChallenger") ? tag.getUUID("WinefoxChallenger") : null;
        if (this.activeChallengeConfig != null && this.challengerId != null) {
            this.maidspell$setMaxHealth((float) this.activeChallengeConfig.maxHealth());
            com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxSpellPowerBonus.apply(this);
        }
        this.postVictoryChatPlayerId = tag.hasUUID("WinefoxPostVictoryChatPlayer")
            ? tag.getUUID("WinefoxPostVictoryChatPlayer") : null;
        // 老存档没有这个键，读出来是空表 —— 那些玩家会再听一次初见，可以接受。
        this.greetedPlayers.clear();
        for (Tag greeted : tag.getList(GREETED_PLAYERS_TAG, Tag.TAG_INT_ARRAY)) {
            this.greetedPlayers.add(NbtUtils.loadUUID(greeted));
        }
        // 她战败之后是留在场上的，读档得接着躺着，不能爬起来重新开打。
        this.entityData.set(CURTSYING, tag.getBoolean("WinefoxCurtsying"));
        this.maidspell$readLifecycle(tag);
        this.setPersistenceRequired();
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
        // 开场的行礼同样要补挂：当前动作只在同步值里，不落 NBT，而倒计时是落 NBT 的。
        // 不补这一句，读档（区块卸载再回来、重登）之后的她就只剩坐姿，那段表演整段丢掉；
        // 与 {@code phase_transition} 同一种处理：动画从头播，剩下的倒计时照走。
        if (this.challengeStartTicks > 0) {
            this.beginAction(WinefoxAction.CURTSY_COMBAT);
        }
        // 誓约那两段同理：动作只在同步值里，倒计时才落 NBT。补挂之后 {@code vow_2} 是从第 0 帧重播的，
        // 也就是说第 120t 那一发会再走一遍 —— {@link #vowCharmGiven} 正是为这一幕准备的，
        // 落过 NBT 就不会因为一次区块卸载多发一枚魂符。
        if (this.vowStage == VOW_STAGE_FIRST) {
            this.beginAction(WinefoxAction.VOW_1);
        } else if (this.vowStage == VOW_STAGE_SECOND) {
            this.beginAction(WinefoxAction.VOW_2);
        }
        if (this.isSeated()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        } finally {
            this.loadingLifecycle = false;
        }
    }

    private void maidspell$readLifecycle(CompoundTag tag) {
        WinefoxEncounterState state;
        WinefoxRewardState reward;
        float maximum;
        float health;
        if (tag.contains("MaidSpellWinefoxLifecycle", Tag.TAG_COMPOUND)) {
            CompoundTag lifecycle = tag.getCompound("MaidSpellWinefoxLifecycle");
            maximum = lifecycle.getFloat("BossMaxHealth");
            health = lifecycle.getFloat("BossHealth");
            state = WinefoxEncounterState.fromId(lifecycle.getByte("EncounterState"));
            reward = WinefoxRewardState.fromId(lifecycle.getByte("RewardState"));
            this.encounterId = lifecycle.hasUUID("EncounterId")
                ? lifecycle.getUUID("EncounterId") : UUID.randomUUID();
            this.rewardTransaction = lifecycle.hasUUID("RewardTransaction")
                ? lifecycle.getUUID("RewardTransaction") : null;
            BossLifecycleAccess.withDataWrite(this,
                () -> this.entityData.set(ENCOUNTER_SERIAL, Math.max(0, lifecycle.getInt("EncounterSerial"))));
        } else {
            maximum = this.getMaxHealth();
            health = tag.contains("Health", Tag.TAG_ANY_NUMERIC) ? tag.getFloat("Health") : maximum;
            if (tag.getBoolean("WinefoxDefeated")) {
                state = WinefoxEncounterState.DEFEATED;
            } else if (tag.getInt("WinefoxReturnHomeTicks") > 0) {
                state = WinefoxEncounterState.RETURNING;
            } else if (!tag.contains("WinefoxSeated") || tag.getBoolean("WinefoxSeated")) {
                state = WinefoxEncounterState.SEATED;
            } else {
                state = WinefoxEncounterState.COMBAT;
            }
            reward = state == WinefoxEncounterState.DEFEATED
                ? WinefoxRewardState.NOT_ELIGIBLE : WinefoxRewardState.NONE;
        }
        this.maidspell$setMaxHealth(maximum);
        health = WinefoxBossHealthController.sanitize(health, this.maidspell$bossMaxHealth());
        if (state == WinefoxEncounterState.SEATED || state == WinefoxEncounterState.CHALLENGE_START) {
            health = this.maidspell$bossMaxHealth();
        } else if (state == WinefoxEncounterState.DEFEATED) {
            health = 0.0F;
        } else if (state == WinefoxEncounterState.COMBAT && health == 0.0F) {
            state = WinefoxEncounterState.RETURNING;
            this.returnHomeTicks = Math.max(1, this.returnHomeTicks);
            reward = WinefoxRewardState.NOT_ELIGIBLE;
        }
        this.maidspell$setEncounterState(state);
        this.maidspell$setRewardState(reward);
        if (reward == WinefoxRewardState.GRANTING) {
            com.github.yimeng261.maidspell.Global.LOGGER.error(
                "Winefox reward {} for encounter {}#{} requires administrator review; no automatic retry",
                this.rewardTransaction, this.encounterId, this.entityData.get(ENCOUNTER_SERIAL));
        }
        final float restoredHealth = health;
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.LOAD,
            () -> this.setHealth(restoredHealth));
        this.dead = false;
        this.deathTime = 0;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (PersistentEntityLifecycleGuard.shouldBlockRemoval(this, reason)) {
            this.maidspell$onBlockedRemoval(reason);
            return;
        }
        super.remove(reason);
    }

    @Override
    public boolean maidspell$shouldProtectLifecycle() {
        return !this.level().isClientSide;
    }

    @Override
    public UUID maidspell$encounterId() {
        return this.encounterId;
    }

    @Override
    public void maidspell$onBlockedRemoval(RemovalReason reason) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (now == this.lastBlockedRemovalSync) {
            return;
        }
        this.lastBlockedRemovalSync = now;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this) > 128.0D * 128.0D) {
                continue;
            }
            player.connection.send(new ClientboundAddEntityPacket(this.getId(), this.getUUID(),
                this.getX(), this.getY(), this.getZ(), this.getXRot(), this.getYRot(), this.getType(), 0,
                this.getDeltaMovement(), this.getYHeadRot()));
            List<SynchedEntityData.DataValue<?>> values = this.entityData.getNonDefaultValues();
            if (values != null && !values.isEmpty()) {
                player.connection.send(new ClientboundSetEntityDataPacket(this.getId(), values));
            }
            player.connection.send(new ClientboundTeleportEntityPacket(this));
            this.resendHealthTo(player);
            this.resendCastingStateTo(player);
        }
    }

    public void maidspell$destroyAuthorized() {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        BossLifecycleAccess.withAuthorizedTeardown(this, RemovalReason.DISCARDED, () -> {
            this.recallSummons();
            this.releaseRetiredMaids();
            this.bossEvent.removeAllPlayers();
            this.remove(RemovalReason.DISCARDED);
        });
    }

    /** 管理员：把她拉回秋千，不发奖励、不重置交易。可从任意状态调用，重复调用安全。 */
    public void maidspell$returnAuthorized() {
        switch (this.maidspell$encounterState()) {
            // 战斗中的收场走与普通生物战斗同一条路径（清理 + 归位倒计时）。
            case COMBAT -> this.finishCombat(false);
            // 开场期间被打断：状态机允许 CHALLENGE_START -> RETURNING。
            case CHALLENGE_START -> {
                this.maidspell$setEncounterState(WinefoxEncounterState.RETURNING);
                this.returnHomeTicks = Math.max(1, this.returnHomeTicks);
                this.maidspell$teardownCombat();
            }
            case DEFEATED -> {
                this.maidspell$setEncounterState(WinefoxEncounterState.RETURNING);
                this.returnHomeTicks = Math.max(1, this.returnHomeTicks);
                this.maidspell$teardownCombat();
            }
            // SEATED 已经在家：这里什么都不做。清理是给"战斗被打断"用的，
            // 对一个正常坐着的她执行清理只会白跑一遍，还会掩盖真正的状态问题。
            case SEATED -> { }
            case RETURNING -> {
                this.returnHomeTicks = 1;
                this.maidspell$teardownCombat();
            }
        }
    }

    /**
     * 管理员修复专用的强制写入：把状态直接摆到 {@code RETURNING}，不经过转移表。
     *
     * <p>SEATED 与 RETURNING 之间没有合法边（坐姿即待战），而修复命令需要把任意状态收敛到
     * 同一条归位路径。为了让转移表保持严格，这条旁路显式命名，而不是放宽转移表。
     */
    private void maidspell$forceReturningForRepair() {
        BossLifecycleAccess.withDataWrite(this, () -> this.entityData.set(
            ENCOUNTER_STATE, (byte) WinefoxEncounterState.RETURNING.ordinal()));
    }

    /**
     * 管理员：把损坏的状态修到"可再次挑战"。
     *
     * <p>与 {@link #maidspell$returnAuthorized} 的区别是它<b>保证收场</b>：即使她已经坐在秋千上，
     * 也先走一趟归位、清掉残留副作用、退回满血。否则"修好了"只是看起来坐在那儿。
     */
    public void maidspell$repairAuthorized() {
        if (this.isRemoved()) {
            return;
        }
        if (this.maidspell$encounterState() != WinefoxEncounterState.RETURNING) {
            this.maidspell$forceReturningForRepair();
        }
        this.returnHomeTicks = 1;
        this.maidspell$teardownCombat();
        // 权威生命退回满值，下一场挑战从完整状态开始。
        WinefoxBossHealthController.withCause(this, WinefoxBossHealthController.Cause.ADMIN,
            () -> this.setHealth(this.maidspell$bossMaxHealth()));
        if (this.maidspell$rewardState() == WinefoxRewardState.NONE) {
            this.maidspell$setRewardState(WinefoxRewardState.NOT_ELIGIBLE);
        }
    }

    public boolean maidspell$resolvePendingReward(boolean reissue) {
        if (this.level().isClientSide || this.maidspell$rewardState() != WinefoxRewardState.GRANTING) {
            return false;
        }
        UUID transaction = this.rewardTransaction;
        this.maidspell$setRewardState(WinefoxRewardState.GRANTED);
        if (reissue) {
            try {
                this.dropDefeatRewards(this.damageSources().generic());
            } catch (RuntimeException exception) {
                com.github.yimeng261.maidspell.Global.LOGGER.error(
                    "Manual Winefox reward reissue {} failed; review spawned items before any further action",
                    transaction, exception);
                return false;
            }
        }
        this.tradingUnlocked |= !this.isRestricted();
        // 同样不置空：管理员补发一份奖励不该把当天的限购一起清掉。
        // 上面这一行若把 tradingUnlocked 翻成了 true，getOffers() 会自行补上那一档。
        com.github.yimeng261.maidspell.Global.LOGGER.warn(
            "Administrator {} Winefox reward {} for encounter {}#{}",
            reissue ? "reissued" : "confirmed", transaction, this.encounterId,
            this.entityData.get(ENCOUNTER_SERIAL));
        return true;
    }

    public Component maidspell$lifecycleStatus() {
        return Component.literal("Winefox " + this.getUUID() + " encounter=" + this.encounterId
            + "#" + this.entityData.get(ENCOUNTER_SERIAL) + " " + this.maidspell$encounterState()
            + " health=" + this.maidspell$authoritativeHealth() + "/" + this.maidspell$bossMaxHealth()
            + " reward=" + this.maidspell$rewardState() + " unattributed=" + this.unattributedDamage
            // 驯服那几项也报出来：胜利进度是攒在实体上的暗账，出了"为什么点她没反应"的疑问时，
            // 这一行是唯一能一眼看出是场次不够、还是誓约早就成了的地方。
            + " wins=" + this.playerWinCount + "/" + VOW_REQUIRED_PLAYER_WINS
            + " soloWin=" + this.soloWinAfterThree
            + " vow=" + this.vowStage + (this.vowSealed ? "(sealed)" : "")
            + " vowTicks=" + this.vowTicks);
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

    /** 固定血条称号，并用 NAME_KEY 供客户端识别自绘血条。 */
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

    /** 配对后补发权威生命、最大生命及遭遇状态，兼容同步默认值与重新追踪。 */
    private void resendHealthTo(ServerPlayer player) {
        if (this.level().isClientSide) {
            return;
        }
        player.connection.send(new ClientboundSetEntityDataPacket(this.getId(), List.of(
            SynchedEntityData.DataValue.create(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), 1.0F),
            SynchedEntityData.DataValue.create(BOSS_HEALTH, this.maidspell$authoritativeHealth()),
            SynchedEntityData.DataValue.create(BOSS_MAX_HEALTH, this.maidspell$bossMaxHealth()),
            SynchedEntityData.DataValue.create(ENCOUNTER_STATE, this.entityData.get(ENCOUNTER_STATE)),
            SynchedEntityData.DataValue.create(REWARD_STATE, this.entityData.get(REWARD_STATE)),
            SynchedEntityData.DataValue.create(ENCOUNTER_SERIAL, this.entityData.get(ENCOUNTER_SERIAL)))));
    }

    /** ISS 只在状态变化时同步施法，新追踪者需要主动补发当前状态。 */
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

    /** 战败不走原版死亡计时；显式调用也不能移除这个持久 Boss。 */
    @Override
    protected void tickDeath() {
        this.deathTime = 0;
    }
}
