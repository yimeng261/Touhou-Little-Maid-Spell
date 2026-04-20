package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.CorruptedKnightEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.ElfTemplarEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.HolyConstructEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.ShadowAssassinEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class IronsSpellbooksCompatEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MaidSpellMod.MOD_ID);

    public static final RegistryObject<EntityType<CorruptedKnightEntity>> CORRUPTED_KNIGHT =
            ENTITY_TYPES.register("corrupted_knight",
                    () -> EntityType.Builder.of(CorruptedKnightEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("corrupted_knight"));

    public static final RegistryObject<EntityType<ShadowAssassinEntity>> SHADOW_ASSASSIN =
            ENTITY_TYPES.register("shadow_assassin",
                    () -> EntityType.Builder.of(ShadowAssassinEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("shadow_assassin"));

    public static final RegistryObject<EntityType<ElfTemplarEntity>> ELF_TEMPLAR =
            ENTITY_TYPES.register("elf_templar",
                    () -> EntityType.Builder.of(ElfTemplarEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("elf_templar"));

    public static final RegistryObject<EntityType<HolyConstructEntity>> HOLY_CONSTRUCT =
            ENTITY_TYPES.register("holy_construct",
                    () -> EntityType.Builder.of(HolyConstructEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 2.5F)
                            .clientTrackingRange(10)
                            .build("holy_construct"));

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

    private static void onRegisterSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
            ELF_TEMPLAR.get(),
            SpawnPlacements.Type.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            IronsSpellbooksCompatEntities::canElfTemplarSpawn,
            SpawnPlacementRegisterEvent.Operation.REPLACE
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
