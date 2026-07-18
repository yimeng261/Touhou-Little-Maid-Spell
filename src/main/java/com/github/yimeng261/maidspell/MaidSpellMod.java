package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.block.entity.MaidSpellBlockEntities;
import com.github.yimeng261.maidspell.compat.ftbteams.FTBTeamsCompat;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.TouhouLittleMaidLegacyModelPackCleaner;
import com.github.yimeng261.maidspell.crafting.OptionalModIngredientSerializer;
import com.github.yimeng261.maidspell.event.FoxLeafOwnerWaterWalking;
import com.github.yimeng261.maidspell.event.MaidSpellEventHandler;
import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.MaidSpellCreativeTab;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.MaidSpellContainers;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructurePieceTypes;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.world.ForgeChunkManager;
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


    @SuppressWarnings("removal")
    public MaidSpellMod() {
        TouhouLittleMaidLegacyModelPackCleaner.cleanGameDirectory();

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        var forgeBus = MinecraftForge.EVENT_BUS;
        
        modBus.addListener(this::setup);
        forgeBus.register(this);
        
        // 手动注册事件处理器，确保事件能被正确监听
        forgeBus.register(MaidSpellEventHandler.class);
        forgeBus.register(FoxLeafOwnerWaterWalking.class);
        // Curios 事件处理器仅在 Curios 加载时注册，避免硬依赖
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            forgeBus.register(com.github.yimeng261.maidspell.event.CuriosEventHandler.class);
        }
        MaidSpellBlocks.register(modBus);
        MaidSpellBlockEntities.register(modBus);
        MaidSpellItems.register(modBus);
        MaidSpellCreativeTab.register(modBus);
        MaidSpellContainers.register(modBus);
        MaidSpellSounds.SOUNDS.register(modBus);
        MaidSpellEntities.register(modBus);
        FTBTeamsCompat.init();
        IronsSpellbooksCompat.init(modBus);
        // 注册自定义结构
        MaidSpellStructures.STRUCTURE_TYPES.register(modBus);
        MaidSpellStructurePieceTypes.STRUCTURE_PIECE_TYPES.register(modBus);

        
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, EnderPocketClientConfig.SPEC,
                MOD_ID + "-client.toml");
        
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CraftingHelper.register(new ResourceLocation(MOD_ID, "optional_mod_item"), OptionalModIngredientSerializer.INSTANCE);
            MaidSpellBlocks.registerPottedPlants();
            ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, (level, ticketHelper) -> {
                for (var owner : ticketHelper.getEntityTickets().keySet()) {
                    ticketHelper.removeAllTickets(owner);
                }
            });
            // 注册网络消息
            NetworkHandler.registerMessages();
            
            if (checkDependencies()) {
                LOGGER.info("Dependencies verified - initialization complete");
            }
        });
    }
    
    private boolean checkDependencies() {
        return checkModLoaded("touhou_little_maid");
    }
    
    private boolean checkModLoaded(String modId) {
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
    }


}
