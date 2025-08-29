package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import com.github.yimeng261.maidspell.event.MaidSpellEventHandler;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.MaidSpellCreativeTab;
import com.github.yimeng261.maidspell.item.bauble.blueNote.contianer.MaidSpellContainers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MaidSpellMod.MOD_ID)
public class MaidSpellMod {
    public static final String MOD_ID = "touhou_little_maid_spell";
    public static final String MOD_NAME = "Touhou Little Maid: Spell";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final SpellCombatMeleeTask SPELL_COMBAT_TASK = new SpellCombatMeleeTask();
    

    public MaidSpellMod() {
        
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        var forgeBus = MinecraftForge.EVENT_BUS;
        
        modBus.addListener(this::setup);
        forgeBus.register(this);
        
        // 手动注册事件处理器，确保事件能被正确监听
        forgeBus.register(MaidSpellEventHandler.class);
        MaidSpellItems.ITEMS.register(forgeBus);

        MaidSpellItems.register(modBus);
        MaidSpellCreativeTab.register(modBus);
        MaidSpellContainers.register(modBus);

        
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (checkDependencies()) {
                LOGGER.info("Dependencies verified - initialization complete");
            }
        });
    }
    
    private boolean checkDependencies() {
        boolean hasTouhouMaid = checkModLoaded("touhou_little_maid");
        if (!hasTouhouMaid) {
            return false;
        }
        return true;
    }
    
    private boolean checkModLoaded(String modId) {
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
    }
    

}