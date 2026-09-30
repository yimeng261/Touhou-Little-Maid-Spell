package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.base.AbstractSpellMeleeMob;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.wizards.IMerchantWizard;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class ElfTemplarEntity extends AbstractSpellMeleeMob implements IMerchantWizard {
    private static final int DAILY_TRADE_MAX_USES = 5;
    /** 交易对象离开这么多格就放开交易。 */
    private static final int TRADING_MAX_DISTANCE = 8;
    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;
    private long lastRestockGameTime;
    private int numberOfRestocksToday;
    private long lastRestockCheckDayTime;

    public ElfTemplarEntity(EntityType<? extends ElfTemplarEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(AttributeRegistry.CAST_TIME_REDUCTION, 1.5);
    }

    @Override
    protected boolean addDefaultPlayerTargetGoal() {
        return false;
    }

    @Override
    protected boolean alertSameTypeWhenHurt() {
        return true;
    }

    @Override
    protected List<AbstractSpell> getAttackSpells() {
        return List.of(
                SpellRegistry.POISON_ARROW_SPELL.get(),
                SpellRegistry.FIREFLY_SWARM_SPELL.get(),
                SpellRegistry.ROOT_SPELL.get(),
                SpellRegistry.GUST_SPELL.get(),
                SpellRegistry.ARROW_VOLLEY_SPELL.get());
    }

    @Override
    protected float getComboChance() {
        return 0.2f;
    }

    @Override
    protected float getMeleeBiasMin() {
        return 0.05f;
    }

    @Override
    protected float getMeleeBiasMax() {
        return 0.15f;
    }

    @Override
    protected int getSpellAttackIntervalMin() {
        return 20;
    }

    @Override
    protected int getSpellAttackIntervalMax() {
        return 40;
    }

    @Override
    protected List<AbstractSpell> getMovementSpells() {
        return List.of(SpellRegistry.FROST_STEP_SPELL.get());
    }

    @Override
    protected List<AbstractSpell> getSupportSpells() {
        return List.of(SpellRegistry.HEAL_SPELL.get(), SpellRegistry.OAKSKIN_SPELL.get());
    }

    @Override
    protected void registerAdditionalGoals() {
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::isNaturalEnemy));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    /** 同类范围法术可能触发还击；声明同盟以阻止友伤和连锁仇恨 */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return entity instanceof ElfTemplarEntity || super.isAlliedTo(entity);
    }

    /**
     * 天生的敌人只有灾厄村民和不死生物，同类和同盟一律排除
     */
    private boolean isNaturalEnemy(LivingEntity target) {
        if (this.isAlliedTo(target)) {
            return false;
        }
        return target instanceof Raider || target.getType().is(EntityTypeTags.UNDEAD);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        equipAndHideDrop(EquipmentSlot.MAINHAND, new ItemStack(getClaymoreItem()));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean preventTrade = isAggressive() || this.getTarget() != null || (!this.level().isClientSide && this.getOffers().isEmpty());
        // 已有人在交易时按普通交互处理，与原版村民一致，也不会关掉前一个玩家的界面
        if (preventTrade || this.isTrading()) {
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

    /** 交易对象死亡、下线、换维度或走出 {@value #TRADING_MAX_DISTANCE} 格后放开交易，NPC 不会一直被占着。 */
    @Override
    public void aiStep() {
        super.aiStep();
        Player trader = this.getTradingPlayer();
        if (trader != null && !this.level().isClientSide && (!trader.isAlive() || trader.level() != this.level()
                || this.distanceToSqr(trader) > TRADING_MAX_DISTANCE * TRADING_MAX_DISTANCE)) {
            this.stopTrading();
        }
    }

    /** 真正死亡后放开交易对象让界面随之关闭；死亡被取消时照常交易。尸体要过一会儿才移出世界，不能只靠 {@link #onRemovedFromLevel}。 */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.isDeadOrDying()) {
            this.stopTrading();
        }
    }

    /** 换维度（/tp 会直接移除旧实体）、卸载或被清除后，旧实体不能再成交。 */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        this.stopTrading();
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            this.offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 10),
                Optional.empty(),
                new ItemStack(MaidSpellItems.YUE_LINGLAN.get()),
                0,
                DAILY_TRADE_MAX_USES,
                1,
                0.05f
            ));
            this.offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 5),
                Optional.empty(),
                new ItemStack(ItemRegistry.NATURE_RUNE.get()),
                0,
                DAILY_TRADE_MAX_USES,
                1,
                0.05f
            ));
            this.offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 2),
                Optional.empty(),
                new ItemStack(Items.HONEY_BOTTLE),
                0,
                DAILY_TRADE_MAX_USES,
                1,
                0.05f
            ));
            this.offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 5),
                Optional.empty(),
                new ItemStack(Items.POISONOUS_POTATO, 3),
                0,
                DAILY_TRADE_MAX_USES,
                1,
                0.05f
            ));
            this.setLastRestockGameTime(level().getGameTime());
        }
        return this.offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
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

    protected SoundEvent getTradeUpdatedSound(boolean isYesSound) {
        return MaidSpellSounds.SILENT_MERCHANT_FEEDBACK.get();
    }

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
        deserializeMerchant(compound, c -> this.offers = c);
        if (this.offers != null) {
            MerchantOffers normalizedOffers = new MerchantOffers();
            for (MerchantOffer offer : this.offers) {
                normalizedOffers.add(copyOfferWithDailyUseLimit(offer));
            }
            this.offers = normalizedOffers;
        }
    }

    @Override
    public int getRestocksToday() {
        return numberOfRestocksToday;
    }

    @Override
    public void setRestocksToday(int restocks) {
        this.numberOfRestocksToday = restocks;
    }

    @Override
    public long getLastRestockGameTime() {
        return lastRestockGameTime;
    }

    @Override
    public void setLastRestockGameTime(long time) {
        this.lastRestockGameTime = time;
    }

    @Override
    public long getLastRestockCheckDayTime() {
        return lastRestockCheckDayTime;
    }

    @Override
    public void setLastRestockCheckDayTime(long time) {
        this.lastRestockCheckDayTime = time;
    }

    @Override
    public Level level() {
        return super.level();
    }

    @Override
    public void setTradingPlayer(@Nullable Player tradingPlayer) {
        this.tradingPlayer = tradingPlayer;
    }

    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    private MerchantOffer copyOfferWithDailyUseLimit(MerchantOffer offer) {
        return new MerchantOffer(
            offer.getItemCostA(),
            offer.getItemCostB(),
            offer.getResult().copy(),
            Math.min(offer.getUses(), DAILY_TRADE_MAX_USES),
            DAILY_TRADE_MAX_USES,
            offer.getXp(),
            offer.getPriceMultiplier(),
            offer.getDemand()
        );
    }
}
