package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import com.github.yimeng261.maidspell.event.MaidSpellEventHandler;
import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.MaidSpellCreativeTab;
import com.github.yimeng261.maidspell.item.bauble.blueNote.contianer.MaidSpellContainers;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructurePieceTypes;
import com.github.yimeng261.maidspell.network.NetworkHandler;
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
    
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final SpellCombatMeleeTask SPELL_COMBAT_TASK = new SpellCombatMeleeTask();
    

    @SuppressWarnings("removal")
    public MaidSpellMod() {
        
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        var forgeBus = MinecraftForge.EVENT_BUS;
        
        modBus.addListener(this::setup);
        forgeBus.register(this);
        
        // 手动注册事件处理器，确保事件能被正确监听
        forgeBus.register(MaidSpellEventHandler.class);
        MaidSpellItems.register(modBus);
        MaidSpellCreativeTab.register(modBus);
        MaidSpellContainers.register(modBus);
        MaidSpellSounds.SOUNDS.register(modBus);
        MaidSpellEntities.register(modBus);
        // 注册自定义结构
        MaidSpellStructures.STRUCTURE_TYPES.register(modBus);
        MaidSpellStructurePieceTypes.STRUCTURE_PIECE_TYPES.register(modBus);

        
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 注册网络消息
            NetworkHandler.registerMessages();
            
            if (checkDependencies()) {
                LOGGER.info("Dependencies verified - initialization complete");
            }
        });
    }
    
    private boolean checkDependencies() {
        boolean hasTouhouMaid = checkModLoaded("touhou_little_maid");
        return hasTouhouMaid;
    }
    
    private boolean checkModLoaded(String modId) {
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
    }


}