package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.yimeng261.maidspell.bauble.SpellEnhancementBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraftforge.fml.ModList;

/**
 * 女仆法术饰品注册器
 * 只有在铁魔法模组加载时才会注册饰品
 */
@LittleMaidExtension
public class MaidSpellBaubleRegistry implements ILittleMaid {
    
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        // 只有在铁魔法模组加载时才注册饰品
        if (ModList.get().isLoaded("irons_spellbooks") && MaidSpellItems.SPELL_ENHANCEMENT_CORE != null) {
            manager.bind(MaidSpellItems.SPELL_ENHANCEMENT_CORE, new SpellEnhancementBauble());
        }
    }
} 