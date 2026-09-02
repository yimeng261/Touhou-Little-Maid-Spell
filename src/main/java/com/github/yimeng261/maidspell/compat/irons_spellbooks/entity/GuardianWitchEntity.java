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
import net.minecraft.world.entity.Entity;
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
 * 观星塔的中立法师守卫，使用加权施法与交易系统。
 * 施法间隔由 WizardAttackGoal 控制，法力和冷却缩减属性不影响自身出手频率。
 * 星陨石只出售一次，不参与常规补货。
 */
public class GuardianWitchEntity extends NeutralWizard implements IMerchantWizard {
    /** 轨路虚空来自 traveloptics，那个模组不是编译期依赖，只能按 id 在运行时找。 */
    private static final ResourceLocation ORBITAL_VOID = ResourceLocation.fromNamespaceAndPath("traveloptics", "orbital_void");

    /**
     * 出手间隔（tick）。{@code WizardAttackGoal} 按「目标离得多远」在这两个值之间插值：
     * 贴脸取 min，站在 20 格施法距离边上取 max。
     */
    private static final int SPELL_ATTACK_INTERVAL_MIN = 30;
    private static final int SPELL_ATTACK_INTERVAL_MAX = 60;

    /**
     * 报价的默认每日上限。与 {@code ElfTemplarEntity} 取同一个值：交易界面里 {@code maxUses} 就是它，
     * 卖完要等半天补货。
     *
     * <p>不是全表统一：星锚珍珠按 {@code AstroMancerTrades#dailyLimitFor} 只给 1，星陨石那一条更是全局一次。
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
                .add(AttributeRegistry.MAX_MANA, 10000.0)
                // ISS 的百分比属性基准是 1.0，1.9 就是面板上的「+90% 冷却缩减」。
                .add(AttributeRegistry.COOLDOWN_REDUCTION, 1.9)
                .add(AttributeRegistry.CAST_TIME_REDUCTION, 1.5)
                // 全学派 +10%：ISS 算法术强度是「通用系数 × 对应学派系数」，通用那一项抬 10% 等于每个学派都抬 10%。
                .add(AttributeRegistry.SPELL_POWER, 1.1);
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

    /** 同类范围法术可能误伤并触发还击，声明同盟以阻止互相攻击。 */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return entity instanceof GuardianWitchEntity || super.isAlliedTo(entity);
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
                                        @Nullable SpawnGroupData spawnData) {
        this.populateDefaultEquipmentSlots(this.random, difficulty);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
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

    /** 打开菜单失败时清除交易对象，避免后续交易被锁住。 */
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

    /**
     * 补货：与她同源的精灵守卫走 {@code IMerchantWizard#restock} 那份默认实现，这里只多一条 ——
     * 「只可交易一次」的星陨石不跟着刷回来（见 {@code AstroMancerTrades#isOnceEver}）。
     *
     * <p>与默认实现逐行相同的原因：默认那份会把整张表一律 {@code resetUses()}，星陨石一次用掉之后
     * 每半天又会被它放回货架，那就不叫只可交易一次了。次数本身就是它的全部状态，
     * {@code uses}／{@code maxUses} 落盘，所以读档以后仍然是卖光。
     */
    @Override
    public void restock() {
        for (MerchantOffer offer : this.getOffers()) {
            if (AstroMancerTrades.isOnceEver(offer.getResult())) {
                continue;
            }
            offer.updateDemand();
            offer.resetUses();
        }
        this.setRestocksToday(this.getRestocksToday() + 1);
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
        // 按当前报价重建，仅从存档保留使用次数。
        deserializeMerchant(compound, offers -> this.offers = AstroMancerTrades.rebuildKeepingUses(offers));
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
