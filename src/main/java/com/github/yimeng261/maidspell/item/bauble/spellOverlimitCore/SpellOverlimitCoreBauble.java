package com.github.yimeng261.maidspell.item.bauble.spellOverlimitCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

/**
 * 法术超限核心饰品逻辑
 * 突破法术限制的核心逻辑
 * 具体功能由配置和其他系统实现
 */
public class SpellOverlimitCoreBauble implements IMaidBauble {

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        // 装备时的逻辑，由使用者实现
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        // 卸下时的逻辑，由使用者实现
    }
}

