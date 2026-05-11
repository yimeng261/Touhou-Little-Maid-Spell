package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Curios 事件处理器 - 仅在 Curios 加载时注册
 */
public class CuriosEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onMaidCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide()) {
            return;
        }

        ItemStack from = event.getFrom();
        ItemStack to = event.getTo();
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);

        if (!from.isEmpty()) {
            manager.removeSpellItem(maid, from);
            LOGGER.debug("女仆 {} 从curios槽位 {} 卸下物品: {}",
                maid.getUUID(), event.getIdentifier(), from.getItem());
        }

        if (!to.isEmpty()) {
            manager.addSpellItem(maid, to);
            LOGGER.debug("女仆 {} 在curios槽位 {} 装备物品: {}",
                maid.getUUID(), event.getIdentifier(), to.getItem());
        }
    }
}
