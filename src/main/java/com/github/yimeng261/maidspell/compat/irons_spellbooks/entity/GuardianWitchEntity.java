package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.NeutralWizard;
import io.redspace.ironsspellbooks.entity.mobs.goals.GustDefenseGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardRecoverGoal;
import io.redspace.ironsspellbooks.entity.mobs.wizards.IMerchantWizard;
import io.redspace.ironsspellbooks.entity.mobs.wizards.fire_boss.NotIdioticNavigation;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * 守塔人。观星塔的守卫，牧师那套远程法师模板：站桩不动、被打才还手，靠 {@code WizardAttackGoal}
 * 的加权逻辑在攻击／防御／辅助三类法术之间挑。
 *
 * <p>与牧师的区别只有三处：
 * <ul>
 *   <li>不带村庄那一摊（找 POI、回家睡觉、保卫村庄），只留「玩家中立」这一层——
 *       {@link NeutralWizard} 自带的怒气系统管着：不惹它就不动手，打了它就记仇。</li>
 *   <li>魔法飞弹改成连发，见 {@link GuardianWitchAttackGoal}；攻击池里另有魔法霰弹。</li>
 *   <li>开了交易栏，见 {@link AstroMancerTrades} —— 那套「每天 5 次、半天补货」的机器
 *       与 {@link ElfTemplarEntity} 逐行同源，改一边记得看另一边。</li>
 * </ul>
 *
 * <p><b>法力值和冷却缩减对它自己不起作用</b>，记在这里省得下次再查：
 * {@code AbstractSpellCastingMob} 的施法路径压根不碰 {@code MagicData} 的法力池，也不走
 * {@code getEffectiveSpellCooldown}——怪物的出手节奏完全由 {@code WizardAttackGoal} 的
 * {@code spellAttackInterval} 决定。属性照着需求配上是为了让面板／别的模组读得到，
 * 真想让它放得更密，改的是 {@link #SPELL_ATTACK_INTERVAL_MIN}/{@code MAX}。
 */
public class GuardianWitchEntity extends NeutralWizard implements IMerchantWizard {
    /** 轨路虚空来自 traveloptics，那个模组不是编译期依赖，只能按 id 在运行时找。 */
    private static final ResourceLocation ORBITAL_VOID = new ResourceLocation("traveloptics", "orbital_void");

    /**
     * 出手间隔（tick）。{@code WizardAttackGoal} 按「目标离得多远」在这两个值之间插值：
     * 贴脸取 min，站在 20 格施法距离边上取 max。
     */
    private static final int SPELL_ATTACK_INTERVAL_MIN = 30;
    private static final int SPELL_ATTACK_INTERVAL_MAX = 60;

    /**
     * 每条报价每天能成交几次。与 {@code ElfTemplarEntity} 取同一个值：交易界面里
     * {@code maxUses} 就是这个数，卖完要等半天补货。
     */
    static final int DAILY_TRADE_MAX_USES = 5;

    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;
    private long lastRestockGameTime;
    private int numberOfRestocksToday;
    private long lastRestockCheckDayTime;


    public GuardianWitchEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(AttributeRegistry.MAX_MANA.get(), 10000.0)
                // ISS 的百分比属性基准是 1.0，1.9 就是面板上的「+90% 冷却缩减」。
                .add(AttributeRegistry.COOLDOWN_REDUCTION.get(), 1.9)
                .add(AttributeRegistry.CAST_TIME_REDUCTION.get(), 1.5)
                // 全学派 +10%：ISS 算法术强度是「通用系数 × 对应学派系数」，通用那一项抬 10% 等于每个学派都抬 10%。
                .add(AttributeRegistry.SPELL_POWER.get(), 1.1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GustDefenseGoal(this));
        this.goalSelector.addGoal(2, new GuardianWitchAttackGoal(this, 1.25,
                SPELL_ATTACK_INTERVAL_MIN, SPELL_ATTACK_INTERVAL_MAX,
                SpellRegistry.MAGIC_MISSILE_SPELL.get(), 3, 5)
                .setSpells(attackSpells(), defenseSpells(), List.of(), supportSpells())
                .setSpellQuality(0.3f, 0.5f)
                .setDrinksPotions());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new WizardRecoverGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                this::isHostileTowards));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    private static List<AbstractSpell> attackSpells() {
        List<AbstractSpell> spells = new ArrayList<>(List.of(
                SpellRegistry.MAGIC_MISSILE_SPELL.get(),
                SpellRegistry.MAGIC_ARROW_SPELL.get(),
                SpellRegistry.SUMMON_SWORDS.get(),
                SpellRegistry.STARFALL_SPELL.get(),
                SpellRegistry.RAY_OF_FROST_SPELL.get(),
                SpellRegistry.DRAGON_BREATH_SPELL.get(),
                SpellRegistry.ARROW_VOLLEY_SPELL.get(),
                SpellRegistry.CHAIN_LIGHTNING_SPELL.get(),
                // 本模组自己的魔法霰弹（星之魔女酒狐也用它）。瞬发、朝视线方向铺一梭子，
                // 不需要额外施法数据，所以直接丢进这个池子就行。
                IronsSpellbooksCompatSpells.MAGIC_SHOTGUN.get()));
        // 装不装 traveloptics 都得能跑：没注册的 id 换回来的是 NoneSpell，放进列表会白放一次手。
        AbstractSpell orbitalVoid = SpellRegistry.getSpell(ORBITAL_VOID);
        if (orbitalVoid != SpellRegistry.none()) {
            spells.add(orbitalVoid);
        }
        return List.copyOf(spells);
    }

    private static List<AbstractSpell> defenseSpells() {
        return List.of(
                SpellRegistry.COUNTERSPELL_SPELL.get(),
                SpellRegistry.SHIELD_SPELL.get(),
                SpellRegistry.GUST_SPELL.get());
    }

    private static List<AbstractSpell> supportSpells() {
        return List.of(SpellRegistry.HEAL_SPELL.get());
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        this.populateDefaultEquipmentSlots(this.random, difficulty);
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // ARTIFICER_STAFF 是字段名，注册名和资源都叫 artificer_cane（匠师手杖）。
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemRegistry.ARTIFICER_STAFF.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NotIdioticNavigation(this, level);
    }

    @Override
    public boolean guardsBlocks() {
        return false;
    }

    // ==================== 交易栏 ====================

    /**
     * 右键开交易栏，手上拿什么都可以。
     *
     * <p>打架或者正在气头上（被玩家打过）的时候不开：她这会儿的 {@code getTarget()} 非空，
     * 该走的是法师 AI 而不是柜台。这与 {@link ElfTemplarEntity} 的判据一致。
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean preventTrade = this.isAggressive() || this.getTarget() != null
            || (!this.level().isClientSide && this.getOffers().isEmpty());
        if (preventTrade) {
            return super.mobInteract(player, hand);
        }
        Level level = this.level();
        if (!level.isClientSide) {
            if (shouldRestock()) {
                restock();
            }
            this.startTrading(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 打开交易界面。
     *
     * <p>不用 {@link net.minecraft.world.item.trading.Merchant} 自带的
     * {@code openTradingScreen}，只为多一句：菜单没开起来时把交易对象撤回来。
     * 默认实现开不起来就直接返回，而 {@code tradingPlayer} 只在
     * {@code MerchantMenu.removed()} 里清 —— 菜单压根没开过，那个方法就永远不会跑，
     * 于是 {@code mobInteract} 里那一关永远过不去，之后再也点不开交易。
     * 星之魔女酒狐那边（{@code MagicalWinefoxBossEntity#startTrading}）同样处理。
     */
    private void startTrading(Player player) {
        this.setTradingPlayer(player);
        OptionalInt containerId = player.openMenu(new SimpleMenuProvider(
            (id, inventory, opener) -> new MerchantMenu(id, inventory, this), this.getDisplayName()));
        if (containerId.isEmpty()) {
            this.setTradingPlayer(null);
            return;
        }
        MerchantOffers current = this.getOffers();
        if (!current.isEmpty()) {
            player.sendMerchantOffers(containerId.getAsInt(), current, 0,
                this.getVillagerXp(), this.showProgressBar(), this.canRestock());
        }
    }

    /**
     * 交易表只算一次，之后一直挂在字段上。
     *
     * <p>{@code MerchantOffer} 自己记着今天用了几次、还欠多少补货，每次交互重算一张新表
     * 等于把次数抹掉。
     */
    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = AstroMancerTrades.build();
            this.setLastRestockGameTime(this.level().getGameTime());
        }
        return this.offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (!this.level().isClientSide && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
        }
    }

    /** 她不发村民那几声「嗯哼」：交给静音那一份空音效，与精灵守卫同源。 */
    @Override
    public SoundEvent getNotifyTradeSound() {
        return MaidSpellSounds.SILENT_MERCHANT_FEEDBACK.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        serializeMerchant(compound, this.offers, this.lastRestockGameTime, this.numberOfRestocksToday);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        deserializeMerchant(compound, offers -> this.offers = offers);
        if (this.offers != null) {
            this.offers = normalizeDailyUseLimit(this.offers);
        }
    }

    /**
     * 把读回来的表按当前的每日限额重盖一遍。
     *
     * <p>存档里的报价是<b>过去某一版</b>的 {@code AstroMancerTrades} 写下的：改过价格、
     * 加过条目、动过限额之后，老存档会一直卖着旧价。这里只重盖 {@code maxUses} 与已用次数
     * 的上限，价格条目本身留给 {@code getOffers()} 那份新表 —— 但只有表被重建时才会生效。
     * 想彻底换表（比如删掉某条报价）得清掉实体存档里的 {@code Offers}。
     */
    private MerchantOffers normalizeDailyUseLimit(MerchantOffers saved) {
        MerchantOffers normalized = new MerchantOffers();
        for (MerchantOffer offer : saved) {
            normalized.add(new MerchantOffer(
                offer.getBaseCostA().copy(),
                offer.getCostB().copy(),
                offer.getResult().copy(),
                Math.min(offer.getUses(), DAILY_TRADE_MAX_USES),
                DAILY_TRADE_MAX_USES,
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand()));
        }
        return normalized;
    }

    @Override
    public int getRestocksToday() {
        return this.numberOfRestocksToday;
    }

    @Override
    public void setRestocksToday(int restocks) {
        this.numberOfRestocksToday = restocks;
    }

    @Override
    public long getLastRestockGameTime() {
        return this.lastRestockGameTime;
    }

    @Override
    public void setLastRestockGameTime(long time) {
        this.lastRestockGameTime = time;
    }

    @Override
    public long getLastRestockCheckDayTime() {
        return this.lastRestockCheckDayTime;
    }

    @Override
    public void setLastRestockCheckDayTime(long time) {
        this.lastRestockCheckDayTime = time;
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    @Nullable
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }
}
