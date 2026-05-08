package com.github.yimeng261.maidspell.block.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaidSpellBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MaidSpellMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScarletZhuhuaBlockEntity>> SCARLET_ZHUHUA =
            BLOCK_ENTITIES.register("scarlet_zhuhua",
                    () -> BlockEntityType.Builder.of(
                            ScarletZhuhuaBlockEntity::new,
                            MaidSpellBlocks.SCARLET_ZHUHUA.get(),
                            MaidSpellBlocks.POTTED_SCARLET_ZHUHUA.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<YueLinglanBlockEntity>> YUE_LINGLAN =
            BLOCK_ENTITIES.register("yue_linglan",
                    () -> BlockEntityType.Builder.of(
                            YueLinglanBlockEntity::new,
                            MaidSpellBlocks.YUE_LINGLAN.get(),
                            MaidSpellBlocks.POTTED_YUE_LINGLAN.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JingxuYoulanBlockEntity>> JINGXU_YOULAN =
            BLOCK_ENTITIES.register("jingxu_youlan",
                    () -> BlockEntityType.Builder.of(
                            JingxuYoulanBlockEntity::new,
                            MaidSpellBlocks.JINGXU_YOULAN.get(),
                            MaidSpellBlocks.POTTED_JINGXU_YOULAN.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuppressionStoneBlockEntity>> SUPPRESSION_STONE =
            BLOCK_ENTITIES.register("suppression_stone",
                    () -> BlockEntityType.Builder.of(
                            SuppressionStoneBlockEntity::new,
                            MaidSpellBlocks.SUPPRESSION_STONE.get())
                            .build(null));

    private MaidSpellBlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
