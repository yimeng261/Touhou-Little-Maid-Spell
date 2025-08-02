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
    
    /**
     * 获取关联的女仆实体
     */
    public EntityMaid getMaid() {
        return myMaid;
    }
    
    /**
     * 获取推荐的布局信息
     * @return 包含行数和列数的数组 [rows, columns]
     */
    public int[] getRecommendedLayout() {
        int slotCount = getSlots();
        if (slotCount <= 0) {
            return new int[]{1, 1}; // 最小布局
        }
        
        // 计算最优的方形布局
        int columns = (int) Math.ceil(Math.sqrt(slotCount));
        int rows = (int) Math.ceil((double) slotCount / columns);
        
        // 确保至少有一行一列
        if (rows <= 0) rows = 1;
        if (columns <= 0) columns = 1;
        
        return new int[]{rows, columns};
    }
    
    /**
     * 获取指定布局的槽位位置
     * @param row 行号（从0开始）
     * @param column 列号（从0开始）
     * @return 槽位索引，如果超出范围返回-1
     */
    public int getSlotIndex(int row, int column) {
        int[] layout = getRecommendedLayout();
        int columns = layout[1];
        int rows = layout[0];
        
        if (row < 0 || column < 0 || row >= rows || column >= columns) {
            return -1;
        }
        
        int slotIndex = row * columns + column;
        return slotIndex < getSlots() ? slotIndex : -1;
    }
} 