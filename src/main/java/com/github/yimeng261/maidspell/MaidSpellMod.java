package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.entity.MaidSpellEntities;
import com.github.yimeng261.maidspell.event.MaidSpellEventHandler;
import com.github.yimeng261.maidspell.item.MaidSpellCreativeTab;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.blueNote.contianer.MaidSpellContainers;
import com.github.yimeng261.maidspell.item.bauble.spellCore.SpellEnhancementBauble;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MaidSpellMod.MOD_ID)
public class MaidSpellMod {
    public static final String MOD_ID = "touhou_little_maid_spell";
    public static final String MOD_NAME = "Touhou Little Maid: Spell";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public MaidSpellMod(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        // 检查依赖
        modEventBus.addListener(this::setup);
        // 注册网络消息
        modEventBus.addListener(NetworkHandler::registerMessages);
        // 注册额外物品数据标签
        MaidSpellDataComponents.DATA_COMPONENTS.register(modEventBus);

        // 手动注册事件处理器，确保事件能被正确监听
        NeoForge.EVENT_BUS.register(MaidSpellEventHandler.class);
        MaidSpellItems.register(modEventBus);
        MaidSpellCreativeTab.register(modEventBus);
        MaidSpellContainers.register(modEventBus);
        MaidSpellSounds.SOUNDS.register(modEventBus);
        MaidSpellEntities.register(modEventBus);
        // 注册自定义结构
        MaidSpellStructures.STRUCTURE_TYPES.register(modEventBus);

        // 铁魔法属性修改器获取
        if (ModList.get().isLoaded("irons_spellbooks")) {
            modEventBus.addListener(SpellEnhancementBauble::initializeAttributes);
        }

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        if (dist.isClient()) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (checkDependencies()) {
                LOGGER.info("Dependencies verified - initialization complete");
            }
        });
    }

    private boolean checkDependencies() {
        return checkModLoaded("touhou_little_maid");
    }

    private boolean checkModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }


}