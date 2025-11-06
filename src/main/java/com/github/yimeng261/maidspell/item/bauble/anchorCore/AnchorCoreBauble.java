package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.utils.ChunkLoadingManager;

/**
 * 锚定核心饰品逻辑
 * 为女仆提供全面保护，防止被其他模组影响
 */
public class AnchorCoreBauble implements IExtendBauble {

    @Override
    public void onAdd(EntityMaid maid) {
        Global.LOGGER.debug("锚定核心已装备到女仆 {}", maid.getUUID());
        
        // 启用区块强加载
        ChunkLoadingManager.enableChunkLoading(maid);
    }

    @Override
    public void onRemove(EntityMaid maid) {
        Global.LOGGER.debug("锚定核心已从女仆 {} 卸下", maid.getUUID());
    }
}
