package com.github.yimeng261.maidspell.item;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.yimeng261.maidspell.item.bauble.bleedingHeart.BleedingHeartBauble;
import com.github.yimeng261.maidspell.item.bauble.blueNote.BlueNoteBauble;
import com.github.yimeng261.maidspell.item.bauble.doubleHeartChain.DoubleHeartChainBauble;
import com.github.yimeng261.maidspell.item.bauble.flowCore.FlowCoreBauble;
import com.github.yimeng261.maidspell.item.bauble.hairpin.HairpinBauble;
import com.github.yimeng261.maidspell.item.bauble.chaosBook.ChaosBookBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity.FragrantIngenuityBauble;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import com.github.yimeng261.maidspell.item.bauble.anchorCore.AnchorCoreBauble;
import com.github.yimeng261.maidspell.item.bauble.spellOverlimitCore.SpellOverlimitCoreBauble;
import com.github.yimeng261.maidspell.item.bauble.quickChantRing.QuickChantBauble;
import com.github.yimeng261.maidspell.item.bauble.crystalCircuit.CrystalCircuitBauble;
import com.github.yimeng261.maidspell.item.bauble.rockCrystal.RockCrystalBauble;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.spellCore.SpellEnhancementBauble;
import com.github.yimeng261.maidspell.item.bauble.springRing.SpringBauble;
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

        if(MaidSpellItems.QUICK_CHANT_RING != null){
            manager.bind(MaidSpellItems.QUICK_CHANT_RING, new QuickChantBauble());
        }

        if(MaidSpellItems.SPRING_RING != null){
            manager.bind(MaidSpellItems.SPRING_RING, new SpringBauble());
        }
        
        // 新增饰品注册
        if(MaidSpellItems.DOUBLE_HEART_CHAIN != null){
            manager.bind(MaidSpellItems.DOUBLE_HEART_CHAIN, new DoubleHeartChainBauble());
        }
        
        if(MaidSpellItems.ROCK_CRYSTAL != null){
            manager.bind(MaidSpellItems.ROCK_CRYSTAL, new RockCrystalBauble());
        }
        
        if(MaidSpellItems.SLIVER_CERCIS != null){
            manager.bind(MaidSpellItems.SLIVER_CERCIS, new SilverCercisBauble());
        }

        if(MaidSpellItems.BLUE_NOTE != null){
            manager.bind(MaidSpellItems.BLUE_NOTE, new BlueNoteBauble());
        }

        if(MaidSpellItems.HAIRPIN != null){
            manager.bind(MaidSpellItems.HAIRPIN, new HairpinBauble());
        }

        if(MaidSpellItems.CHAOS_BOOK != null){
            manager.bind(MaidSpellItems.CHAOS_BOOK, new ChaosBookBauble());
        }

        if(MaidSpellItems.SOUL_BOOK != null){
            manager.bind(MaidSpellItems.SOUL_BOOK, new SoulBookBauble());
        }

        if(MaidSpellItems.ENDER_POCKET != null){
            manager.bind(MaidSpellItems.ENDER_POCKET, new EnderPocketBauble());
        }

        if(MaidSpellItems.WOUND_RIME_BLADE != null){
            manager.bind(MaidSpellItems.WOUND_RIME_BLADE, new WoundRimeBladeBauble());
        }

        if(MaidSpellItems.ANCHOR_CORE != null){
            manager.bind(MaidSpellItems.ANCHOR_CORE, new AnchorCoreBauble());
        }

        if(MaidSpellItems.SPELL_OVERLIMIT_CORE != null){
            manager.bind(MaidSpellItems.SPELL_OVERLIMIT_CORE, new SpellOverlimitCoreBauble());
        }

        if(MaidSpellItems.FRAGRANT_INGENUITY != null){
            manager.bind(MaidSpellItems.FRAGRANT_INGENUITY, new FragrantIngenuityBauble());
        }

        if(MaidSpellItems.CRYSTAL_CIRCUIT != null){
            manager.bind(MaidSpellItems.CRYSTAL_CIRCUIT, new CrystalCircuitBauble());
        }
    }
}