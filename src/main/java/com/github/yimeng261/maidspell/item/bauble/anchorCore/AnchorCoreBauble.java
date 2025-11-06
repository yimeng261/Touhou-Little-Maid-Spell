package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.ChunkLoadingManager;
import net.minecraft.world.item.ItemStack;

/**
 * 锚定核心饰品逻辑
 * 为女仆提供全面保护，防止被其他模组影响
 */
public class AnchorCoreBauble implements IMaidBauble {

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        // 启用区块强加载
        ChunkLoadingManager.enableChunkLoading(maid);
    }

}
