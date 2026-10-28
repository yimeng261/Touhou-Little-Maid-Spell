package com.github.yimeng261.maidspell.compat.goety;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 只在 Goety 存在时注册的物品。
 * <p>
 * 这个类只在 Goety 已加载时才会被加载，它对 Goety API 的引用不会影响没有 Goety 的整合包。
 */
public final class GoetyCompatItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidSpellMod.MOD_ID);

    /**
     * 仅用作虚拟法杖物品栈身份的隐藏物品，不进创造模式物品栏，也不出现在任何战利品和配方里。
     */
    public static final DeferredItem<Item> VIRTUAL_GOETY_WAND =
            ITEMS.register("virtual_goety_wand", VirtualGoetyWandItem::new);

    private GoetyCompatItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(GoetyCompatItems::registerCapabilities);
    }

    /** Goety 的聚晶读取走物品栏的物品处理器能力，虚拟法杖在这里挂上代理处理器。 */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, context) -> new VirtualSoulUsingItemHandler(stack),
                VIRTUAL_GOETY_WAND.get());
    }
}
