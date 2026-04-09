package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.CorruptedKnightEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.ElfTemplarEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.ShadowAssassinEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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

    private IronsSpellbooksCompatEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        eventBus.addListener(IronsSpellbooksCompatEntities::onEntityAttributes);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(CORRUPTED_KNIGHT.get(), CorruptedKnightEntity.createAttributes().build());
        event.put(SHADOW_ASSASSIN.get(), ShadowAssassinEntity.createAttributes().build());
        event.put(ELF_TEMPLAR.get(), ElfTemplarEntity.createAttributes().build());
    }
}
