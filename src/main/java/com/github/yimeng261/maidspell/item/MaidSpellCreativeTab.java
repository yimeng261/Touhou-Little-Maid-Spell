package com.github.yimeng261.maidspell.item;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 女仆法术模组的创造模式物品栏
 * <p>
 * 分为「饰品」与「杂项」两个页签：
 * 「饰品」只收纳 {@link MaidBaubleRegistry} 中绑定了女仆饰品行为的物品，
 * 「杂项」收纳武器、装备、材料、装饰方块、管理员工具与生成蛋。
 * 两个页签始终注册，但只有在铁魔法模组存在时才显示相关物品。
 */
public class MaidSpellCreativeTab {

    // 始终创建注册器
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "touhou_little_maid_spell");

    /**
     * 饰品页签：沿用旧的注册名与图标，保证已有引用与材质不受影响。
     */
    public static final RegistryObject<CreativeModeTab> MAID_SPELL_BAUBLE_TAB =
        CREATIVE_MODE_TABS.register("maid_spell_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.touhou_little_maid_spell"))
            .icon(() -> new ItemStack(MaidSpellItems.FLOW_CORE.get()))
            .displayItems((parameters, output) -> {
                boolean ironsSpellbooks = IronsSpellbooksCompat.isLoaded();

                // 核心类饰品
                if (ironsSpellbooks) {
                    output.accept(MaidSpellItems.SPELL_ENHANCEMENT_CORE.get());
                }
                output.accept(MaidSpellItems.SPELL_OVERLIMIT_CORE.get());
                output.accept(MaidSpellItems.ANCHOR_CORE.get());
                output.accept(MaidSpellItems.FLOW_CORE.get());
                output.accept(MaidSpellItems.ROCK_CRYSTAL.get());
                output.accept(MaidSpellItems.DREAM_CAT_CRYSTAL.get());

                // 首饰与增益饰品
                output.accept(MaidSpellItems.QUICK_CHANT_RING.get());
                output.accept(MaidSpellItems.SPRING_RING.get());
                output.accept(MaidSpellItems.HAIRPIN.get());
                output.accept(MaidSpellItems.DOUBLE_HEART_CHAIN.get());
                output.accept(MaidSpellItems.TRANSMOG_NECKLACE.get());
                output.accept(MaidSpellItems.SLIVER_CERCIS.get());
                output.accept(MaidSpellItems.FRAGRANT_INGENUITY.get());

                // 功能型饰品
                output.accept(MaidSpellItems.CHAOS_BOOK.get());
                output.accept(MaidSpellItems.SOUL_BOOK.get());
                output.accept(MaidSpellItems.ENDER_POCKET.get());
                if (ironsSpellbooks) {
                    output.accept(MaidSpellItems.SPELL_WHITE_LIST.get());
                    output.accept(MaidSpellItems.ARC_CROSS.get());
                }

                // 战斗与生存饰品
                output.accept(MaidSpellItems.FLOATING_FOX_LEAF.get());
                output.accept(MaidSpellItems.MOLTEN_FOX_LEAF.get());
                output.accept(MaidSpellItems.WOUND_RIME_BLADE.get());
                output.accept(MaidSpellItems.BLEEDING_HEART.get());
                output.accept(MaidSpellItems.SPRING_BLOOM_RETURN.get());
            })
            .build());

    /**
     * 杂项页签：武器与装备、材料与道具、装饰方块、管理员工具、生成蛋。
     */
    public static final RegistryObject<CreativeModeTab> MAID_SPELL_MISC_TAB =
        CREATIVE_MODE_TABS.register("maid_spell_misc_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.touhou_little_maid_spell.misc"))
            .icon(() -> new ItemStack(MaidSpellItems.WIND_SEEKING_BELL.get()))
            // 显式排在饰品页签之后，避免不同环境下页签顺序不一致
            .withTabsBefore(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                new ResourceLocation("touhou_little_maid_spell", "maid_spell_tab")))
            .displayItems((parameters, output) -> {
                boolean ironsSpellbooks = IronsSpellbooksCompat.isLoaded();

                // 武器与装备
                if (ironsSpellbooks) {
                    output.accept(IronsSpellbooksCompatItems.STAR_SHADOW_LONGSWORD.get());
                    output.accept(IronsSpellbooksCompatItems.STAR_SHADOW_STAFF.get());
                    output.accept(IronsSpellbooksCompatItems.STAR_WITCH_HAT.get());
                    output.accept(MaidSpellItems.STARGLINT_DAGGER.get());
                }

                // 材料与道具
                if (ironsSpellbooks) {
                    output.accept(MaidSpellItems.NEBULA_CORE.get());
                    output.accept(MaidSpellItems.STAR_METEORITE.get());
                    output.accept(MaidSpellItems.RITUAL_HILT.get());
                    output.accept(MaidSpellItems.WIND_SEEKING_BELL.get());
                    output.accept(MaidSpellItems.STARWATCH_COMPASS.get());
                }

                // 装饰方块
                output.accept(MaidSpellItems.SCARLET_ZHUHUA.get());
                output.accept(MaidSpellItems.YUE_LINGLAN.get());
                output.accept(MaidSpellItems.JINGXU_YOULAN.get());
                output.accept(MaidSpellItems.STAR_GLOW_FLOWER_CLUSTER.get());
                output.accept(MaidSpellItems.SUPPRESSION_STONE.get());

                // 管理员工具
                output.accept(MaidSpellItems.OWNER_CLEAR_TOOL.get());

                // 怪物蛋
                if (ironsSpellbooks) {
                    output.accept(IronsSpellbooksCompatItems.MAGICAL_WINEFOX_BOSS_SPAWN_EGG.get());
                    output.accept(IronsSpellbooksCompatItems.CORRUPTED_KNIGHT_SPAWN_EGG.get());
                    output.accept(IronsSpellbooksCompatItems.SHADOW_ASSASSIN_SPAWN_EGG.get());
                    output.accept(IronsSpellbooksCompatItems.ELF_TEMPLAR_SPAWN_EGG.get());
                    output.accept(IronsSpellbooksCompatItems.HOLY_CONSTRUCT_SPAWN_EGG.get());
                    output.accept(IronsSpellbooksCompatItems.GUARDIAN_WITCH_SPAWN_EGG.get());
                }
            })
            .build());

    /**
     * 注册创造模式物品栏
     */
    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
