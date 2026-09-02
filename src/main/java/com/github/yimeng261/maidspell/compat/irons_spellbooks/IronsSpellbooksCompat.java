package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.IronsSpellbooksCompatClient;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.VoidPhaseDamageHandler;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.WinefoxBossSleepGuard;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEntities;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatItems;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

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
        IronsSpellbooksCompatItems.register(eventBus);
        IronsSpellbooksCompatEntities.register(eventBus);
        IronsSpellbooksCompatEffects.register(eventBus);
        IronsSpellbooksCompatSpells.register(eventBus);
        MinecraftForge.EVENT_BUS.register(VoidPhaseDamageHandler.class);
        MinecraftForge.EVENT_BUS.register(WinefoxBossSleepGuard.class);
        // 这个类嵌在 Boss 里，但同样不能靠注解自动注册：它的处理器要解析外层类的字面量。
        MinecraftForge.EVENT_BUS.register(MagicalWinefoxBossEntity.NonLethalGuard.class);
    }

    public static void initClient(EntityRenderersEvent.RegisterRenderers event) {
        if (!isLoaded()) {
            return;
        }
        IronsSpellbooksCompatClient.onRegisterEntityRenderers(event);
    }

    /**
     * 星影长剑/星影法杖物品栏用的平面图标模型。
     *
     * <p>两把武器的物品模型是 {@code builtin/entity}，没任何东西引用这两份平面模型，
     * 不登记就不会被烘焙，取出来是紫黑方块。放在这儿而不是通用客户端类里，
     * 是因为读它们的 {@code GUI_MODEL} 会加载这两个类，而它们的父类来自铁魔法。
     */
    public static void initClientModels(ModelEvent.RegisterAdditional event) {
        if (!isLoaded()) {
            return;
        }
        event.register(StarShadowLongswordItem.GUI_MODEL);
        event.register(StarShadowStaffItem.GUI_MODEL);
    }

    /** 客户端 setup 阶段要做的铁魔法相关注册，从通用的 {@code ClientSetup} 里调进来。 */
    public static void initClientSetup() {
        if (!isLoaded()) {
            return;
        }
        IronsSpellbooksCompatClient.onClientSetup();
    }
}
