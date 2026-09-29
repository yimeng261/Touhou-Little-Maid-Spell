package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.StarShadowSpearEntity;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.CorruptedKnightEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.ElfTemplarEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.HolyConstructEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.ShadowAssassinEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.CompanionBlackHoleEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedStarfallCloudEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedStarfallCometEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SpellbreakingEchoEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.StarShadowStrikeEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IronsSpellbooksCompatEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MaidSpellMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedKnightEntity>> CORRUPTED_KNIGHT =
            ENTITY_TYPES.register("corrupted_knight",
                    () -> EntityType.Builder.of(CorruptedKnightEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("corrupted_knight"));

    public static final DeferredHolder<EntityType<?>, EntityType<ShadowAssassinEntity>> SHADOW_ASSASSIN =
            ENTITY_TYPES.register("shadow_assassin",
                    () -> EntityType.Builder.of(ShadowAssassinEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("shadow_assassin"));

    public static final DeferredHolder<EntityType<?>, EntityType<ElfTemplarEntity>> ELF_TEMPLAR =
            ENTITY_TYPES.register("elf_templar",
                    () -> EntityType.Builder.of(ElfTemplarEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("elf_templar"));

    public static final DeferredHolder<EntityType<?>, EntityType<HolyConstructEntity>> HOLY_CONSTRUCT =
            ENTITY_TYPES.register("holy_construct",
                    () -> EntityType.Builder.of(HolyConstructEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 2.5F)
                            .clientTrackingRange(10)
                            .build("holy_construct"));

    public static final DeferredHolder<EntityType<?>, EntityType<ModifiedStarfallCloudEntity>> MODIFIED_STARFALL_CLOUD =
            ENTITY_TYPES.register("starfall_modified_cloud",
                    () -> EntityType.Builder.<ModifiedStarfallCloudEntity>of(ModifiedStarfallCloudEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("starfall_modified_cloud"));

    public static final DeferredHolder<EntityType<?>, EntityType<ModifiedStarfallCometEntity>> MODIFIED_STARFALL_COMET =
            ENTITY_TYPES.register("starfall_modified_comet",
                    () -> EntityType.Builder.<ModifiedStarfallCometEntity>of(ModifiedStarfallCometEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("starfall_modified_comet"));

    public static final DeferredHolder<EntityType<?>, EntityType<WinefoxSwordProjectileEntity>> WINEFOX_SWORD_PROJECTILE =
            ENTITY_TYPES.register("winefox_sword_projectile",
                    () -> EntityType.Builder.<WinefoxSwordProjectileEntity>of(WinefoxSwordProjectileEntity::new, MobCategory.MISC)
                            .sized(0.35F, 0.35F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("winefox_sword_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<StarShadowStrikeEntity>> STAR_SHADOW_STRIKE =
            ENTITY_TYPES.register("star_shadow_strike",
                    () -> EntityType.Builder.<StarShadowStrikeEntity>of(StarShadowStrikeEntity::new, MobCategory.MISC)
                            .sized(5.0F, 1.0F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("star_shadow_strike"));

    /**
     * 「伴星黑洞」用的小型黑洞载体。
     *
     * <p>{@code updateInterval(1)} 必须保留：实体每 tick 都要移到施法者头顶，隔帧更新会让跟随一顿一顿。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionBlackHoleEntity>> COMPANION_BLACK_HOLE =
            ENTITY_TYPES.register("companion_black_hole",
                    () -> EntityType.Builder.<CompanionBlackHoleEntity>of(CompanionBlackHoleEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("companion_black_hole"));

    public static final DeferredHolder<EntityType<?>, EntityType<SpellbreakingEchoEntity>> SPELLBREAKING_ECHO =
            ENTITY_TYPES.register("spellbreaking_echo",
                    () -> EntityType.Builder.<SpellbreakingEchoEntity>of(SpellbreakingEchoEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("spellbreaking_echo"));

    /** 扔出去的星影投枪。 */
    public static final DeferredHolder<EntityType<?>, EntityType<StarShadowSpearEntity>> STAR_SHADOW_SPEAR =
            ENTITY_TYPES.register("star_shadow_spear",
                    () -> EntityType.Builder.<StarShadowSpearEntity>of(StarShadowSpearEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.13F)
                            .clientTrackingRange(4)
                            .updateInterval(5)
                            .build("star_shadow_spear"));

    private IronsSpellbooksCompatEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        eventBus.addListener(IronsSpellbooksCompatEntities::onEntityAttributes);
        eventBus.addListener(IronsSpellbooksCompatEntities::onRegisterSpawnPlacements);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(CORRUPTED_KNIGHT.get(), CorruptedKnightEntity.createAttributes().build());
        event.put(SHADOW_ASSASSIN.get(), ShadowAssassinEntity.createAttributes().build());
        event.put(ELF_TEMPLAR.get(), ElfTemplarEntity.createAttributes().build());
        event.put(HOLY_CONSTRUCT.get(), HolyConstructEntity.prepareAttributes().build());
    }

    private static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
            ELF_TEMPLAR.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            IronsSpellbooksCompatEntities::canElfTemplarSpawn,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    private static boolean canElfTemplarSpawn(EntityType<ElfTemplarEntity> entityType,
                                              ServerLevelAccessor level,
                                              MobSpawnType spawnType,
                                              BlockPos pos,
                                              RandomSource random) {
        return isSpawnableGround(level, pos) && level.getRawBrightness(pos, 0) > 8;
    }

    private static boolean isSpawnableGround(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON);
    }
}
