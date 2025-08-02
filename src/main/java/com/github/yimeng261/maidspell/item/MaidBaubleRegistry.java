package com.github.yimeng261.maidspell.item;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.yimeng261.maidspell.item.bauble.bleedingHeart.BleedingHeartBauble;
import com.github.yimeng261.maidspell.item.bauble.flowCore.FlowCoreBauble;
import com.github.yimeng261.maidspell.item.bauble.spellCore.SpellEnhancementBauble;
import net.minecraftforge.fml.ModList;

/**
 * 女仆法术饰品注册器
 * 只有在铁魔法模组加载时才会注册饰品
 */
@LittleMaidExtension
public class MaidBaubleRegistry implements ILittleMaid {

    @Override
    public void bindMaidBauble(BaubleManager manager) {

        if (ModList.get().isLoaded("irons_spellbooks") && MaidSpellItems.SPELL_ENHANCEMENT_CORE != null) {
            manager.bind(MaidSpellItems.SPELL_ENHANCEMENT_CORE, new SpellEnhancementBauble());
        }

        if (MaidSpellItems.BLEEDING_HEART != null) {
            manager.bind(MaidSpellItems.BLEEDING_HEART, new BleedingHeartBauble());
        }

        if(MaidSpellItems.FLOW_CORE != null) {
            manager.bind(MaidSpellItems.FLOW_CORE, new FlowCoreBauble());
        }
    }
}