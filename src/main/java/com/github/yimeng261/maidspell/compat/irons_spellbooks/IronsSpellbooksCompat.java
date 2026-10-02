package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxSpellPowerBonus;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxKnockbackGuard;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxNonLethalGuard;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.IronsSpellbooksCompatClient;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.VoidPhaseDamageHandler;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarWitchArmorMaterial;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarWitchHatItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class IronsSpellbooksCompat {
    public static final String MOD_ID = "irons_spellbooks";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private IronsSpellbooksCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void init(IEventBus eventBus) {
        if (!isLoaded()) {
            return;
        }
        StarWitchArmorMaterial.register(eventBus);
        IronsSpellbooksCompatItems.register(eventBus);
        IronsSpellbooksCompatEntities.register(eventBus);
        IronsSpellbooksCompatEffects.register(eventBus);
        IronsSpellbooksCompatSpells.register(eventBus);
        NeoForge.EVENT_BUS.register(VoidPhaseDamageHandler.class);
        NeoForge.EVENT_BUS.register(WinefoxNonLethalGuard.class);
        // 以下监听器都引用酒狐实体，其父类来自铁魔法，所以只在这里按需注册。
        NeoForge.EVENT_BUS.register(WinefoxKnockbackGuard.class);
        NeoForge.EVENT_BUS.register(WinefoxSpellPowerBonus.class);
    }

    public static void initClientSetup() {
        if (!isLoaded()) {
            return;
        }
        IronsSpellbooksCompatClient.onClientSetup();
    }

    public static void initClient(EntityRenderersEvent.RegisterRenderers event) {
        if (!isLoaded()) {
            return;
        }
        IronsSpellbooksCompatClient.onRegisterEntityRenderers(event);
    }

    /**
     * 星影长剑、星影法杖和星之魔女法帽物品栏用的平面图标模型。
     *
     * <p>没有物品直接引用这几份模型，不登记就不会被烘焙。放在这里是因为读 {@code GUI_MODEL} 会加载这几个类，
     * 而它们的父类来自铁魔法。
     */
    public static void initClientModels(ModelEvent.RegisterAdditional event) {
        if (!isLoaded()) {
            return;
        }
        event.register(ModelResourceLocation.standalone(StarShadowLongswordItem.GUI_MODEL));
        event.register(ModelResourceLocation.standalone(StarShadowStaffItem.GUI_MODEL));
        event.register(ModelResourceLocation.standalone(StarWitchHatItem.GUI_MODEL));
    }

    public static void initClientExtensions(RegisterClientExtensionsEvent event) {
        if (!isLoaded()) {
            return;
        }
        IronsSpellbooksCompatClient.onRegisterClientExtensions(event);
    }
}
