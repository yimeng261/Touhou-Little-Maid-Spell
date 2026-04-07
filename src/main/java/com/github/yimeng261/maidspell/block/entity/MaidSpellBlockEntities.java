package com.github.yimeng261.maidspell.block.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MaidSpellBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MaidSpellMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<ScarletZhuhuaBlockEntity>> SCARLET_ZHUHUA =
        BLOCK_ENTITIES.register("scarlet_zhuhua",
            () -> BlockEntityType.Builder.of(
                ScarletZhuhuaBlockEntity::new,
                MaidSpellBlocks.SCARLET_ZHUHUA.get(),
                MaidSpellBlocks.POTTED_SCARLET_ZHUHUA.get()
            ).build(null));

    private MaidSpellBlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
