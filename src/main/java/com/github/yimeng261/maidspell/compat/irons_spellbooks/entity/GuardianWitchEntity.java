package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.NeutralWizard;
import io.redspace.ironsspellbooks.entity.mobs.goals.GustDefenseGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardRecoverGoal;
import io.redspace.ironsspellbooks.entity.mobs.wizards.fire_boss.NotIdioticNavigation;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 守塔人。观星塔的守卫，牧师那套远程法师模板：站桩不动、被打才还手，靠 {@code WizardAttackGoal}
 * 的加权逻辑在攻击／防御／辅助三类法术之间挑。
 *
 * <p>与牧师的区别只有两处：
 * <ul>
 *   <li>不带村庄那一摊（交易、找 POI、回家睡觉、保卫村庄），只留「玩家中立」这一层——
 *       {@link NeutralWizard} 自带的怒气系统管着：不惹它就不动手，打了它就记仇。</li>
 *   <li>魔法飞弹改成连发，见 {@link GuardianWitchAttackGoal}。</li>
 * </ul>
 *
 * <p><b>法力值和冷却缩减对它自己不起作用</b>，记在这里省得下次再查：
 * {@code AbstractSpellCastingMob} 的施法路径压根不碰 {@code MagicData} 的法力池，也不走
 * {@code getEffectiveSpellCooldown}——怪物的出手节奏完全由 {@code WizardAttackGoal} 的
 * {@code spellAttackInterval} 决定。属性照着需求配上是为了让面板／别的模组读得到，
 * 真想让它放得更密，改的是 {@link #SPELL_ATTACK_INTERVAL_MIN}/{@code MAX}。
 */
public class GuardianWitchEntity extends NeutralWizard {
    /** 轨路虚空来自 traveloptics，那个模组不是编译期依赖，只能按 id 在运行时找。 */
    private static final ResourceLocation ORBITAL_VOID = new ResourceLocation("traveloptics", "orbital_void");

    /**
     * 出手间隔（tick）。{@code WizardAttackGoal} 按「目标离得多远」在这两个值之间插值：
     * 贴脸取 min，站在 20 格施法距离边上取 max。
     */
    private static final int SPELL_ATTACK_INTERVAL_MIN = 30;
    private static final int SPELL_ATTACK_INTERVAL_MAX = 60;

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
                SpellRegistry.CHAIN_LIGHTNING_SPELL.get()));
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
}
