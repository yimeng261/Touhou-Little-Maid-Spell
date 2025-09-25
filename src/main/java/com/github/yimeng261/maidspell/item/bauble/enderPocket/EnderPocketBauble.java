package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IExtendBauble;

/**
 * 末影腰包饰品实现
 * 不需要特殊的事件处理，主要功能通过按键和GUI实现
 */
public class EnderPocketBauble implements IExtendBauble {
    
    @Override
    public void onRemove(EntityMaid maid) {
        // 移除饰品时不需要特殊处理
    }
}
