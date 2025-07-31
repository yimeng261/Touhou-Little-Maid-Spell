package com.github.yimeng261.maidspell.inventory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;

/**
 * 支持女仆实体关联的饰品物品处理器
 * 当饰品发生变化时会自动调用状态管理器
 */
public class MaidAwareBaubleItemHandler extends BaubleItemHandler {

    private EntityMaid myMaid;
    /**
     * 构建 size 大小的饰品栏
     *
     * @param size 饰品栏大小
     * @param maid 关联的女仆实体
     */
    public MaidAwareBaubleItemHandler(int size, EntityMaid maid) {
        super(size);
        this.myMaid = maid;
    }
    
    @Override
    protected void onContentsChanged(int slot) {
        // 调用父类的处理逻辑
        super.onContentsChanged(slot);
        
        BaubleStateManager.updateAndCheckBaubleState(myMaid);
    }
} 