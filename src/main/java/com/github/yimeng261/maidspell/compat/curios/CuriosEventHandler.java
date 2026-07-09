package com.github.yimeng261.maidspell.compat.curios;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * curios 槽位变化事件处理器。
 * 单独成类是为了让对 {@code CurioChangeEvent} 的硬引用与主事件处理器解耦，
 * curios 未加载时由 {@link CuriosCompat#init} 跳过注册，避免类链接失败。
 */
public final class CuriosEventHandler {
    private CuriosEventHandler() {
    }

    @SubscribeEvent
    public static void onMaidCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide()) {
            return;
        }

        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        ItemStack from = event.getFrom();
        ItemStack to = event.getTo();

        if (!from.isEmpty()) {
            manager.removeSpellItem(maid, from);
        }
        if (!to.isEmpty()) {
            manager.addSpellItem(maid, to);
        }
    }
}
