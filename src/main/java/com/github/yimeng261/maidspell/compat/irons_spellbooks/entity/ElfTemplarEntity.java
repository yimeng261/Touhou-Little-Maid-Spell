package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.base.AbstractSpellMeleeMob;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.wizards.IMerchantWizard;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ElfTemplarEntity extends AbstractSpellMeleeMob implements IMerchantWizard {
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
                .add(ForgeMod.ENTITY_REACH.get(), 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(AttributeRegistry.CAST_TIME_REDUCTION.get(), 1.5);
    }

    @Override
    protected boolean addDefaultPlayerTargetGoal() {
        return false;
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
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        equipAndHideDrop(EquipmentSlot.MAINHAND, new ItemStack(getClaymoreItem()));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean preventTrade = isAggressive() || this.getTarget() != null || (!this.level().isClientSide && this.getOffers().isEmpty());
        if (!preventTrade) {
            Level level = this.level();
            if (!level.isClientSide && !this.getOffers().isEmpty()) {
                if (shouldRestock()) {
                    restock();
                }
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 0);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            this.offers.add(new MerchantOffer(
                new ItemStack(net.minecraft.world.item.Items.EMERALD, 10),
                ItemStack.EMPTY,
                new ItemStack(MaidSpellItems.YUE_LINGLAN.get()),
                0,
                12,
                1,
                0.05f
            ));
            this.offers.add(new MerchantOffer(
                new ItemStack(net.minecraft.world.item.Items.EMERALD, 5),
                ItemStack.EMPTY,
                new ItemStack(ItemRegistry.NATURE_RUNE.get()),
                0,
                12,
                1,
                0.05f
            ));
            this.offers.add(new MerchantOffer(
                new ItemStack(net.minecraft.world.item.Items.EMERALD, 2),
                ItemStack.EMPTY,
                new ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE),
                0,
                16,
                1,
                0.05f
            ));
            this.offers.add(new MerchantOffer(
                new ItemStack(net.minecraft.world.item.Items.EMERALD, 5),
                ItemStack.EMPTY,
                new ItemStack(net.minecraft.world.item.Items.POISONOUS_POTATO, 3),
                0,
                12,
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
            this.playSound(this.getTradeUpdatedSound(!stack.isEmpty()), this.getSoundVolume(), this.getVoicePitch());
        }
    }

    protected SoundEvent getTradeUpdatedSound(boolean isYesSound) {
        return isYesSound ? SoundRegistry.TRADER_YES.get() : SoundRegistry.TRADER_NO.get();
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundRegistry.TRADER_YES.get();
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
}
