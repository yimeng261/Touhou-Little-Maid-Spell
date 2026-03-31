package com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.utils.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 梦云水晶 - 顶级女仆综合型饰品
 * 集成大量被动效果、战斗增强、维度加成和特殊机制
 */
public class DreamCatCrystal extends Item {

    public DreamCatCrystal() {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
        );
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
                                @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        List<Component> detailLines = new ArrayList<>();
        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc5_base")
            .withStyle(ChatFormatting.GOLD));
        if (ModList.get().isLoaded("irons_spellbooks")) {
            detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc5_iss")
                .withStyle(ChatFormatting.GOLD));
        }
        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc6")
            .withStyle(ChatFormatting.YELLOW));
        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc7")
            .withStyle(ChatFormatting.GREEN));
        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc8")
            .withStyle(ChatFormatting.AQUA));

        if (Config.dreamCrystalExtraTrueDamageEnabled) {
            detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc9_true_damage")
                .withStyle(ChatFormatting.RED));
        }

        if(Config.dreamCrystalSetNoAiEnabled){
            detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc9_freeze")
                .withStyle(ChatFormatting.RED));
        }


        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc9_splash")
            .withStyle(ChatFormatting.RED));

        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc10")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc11")
            .withStyle(ChatFormatting.BLUE));
        detailLines.add(Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc12")
            .withStyle(ChatFormatting.BLUE));

        TooltipHelper.addShiftTooltip(tooltip,
            List.of(
                Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc1")
                    .withStyle(ChatFormatting.AQUA),
                Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc2")
                    .withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc3")
                    .withStyle(ChatFormatting.GRAY),
                Component.translatable("item.touhou_little_maid_spell.dream_cat_crystal.desc4")
                    .withStyle(ChatFormatting.DARK_PURPLE)
            ),
            detailLines);
    }
}
