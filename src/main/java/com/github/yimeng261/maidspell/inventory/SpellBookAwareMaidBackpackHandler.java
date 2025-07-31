package com.github.yimeng261.maidspell.inventory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.MaidBackpackHandler;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;


public class SpellBookAwareMaidBackpackHandler extends MaidBackpackHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 直接存储女仆实体引用，避免反射
    private EntityMaid myMaid;
    
    public SpellBookAwareMaidBackpackHandler(int size, EntityMaid maid) {
        super(size, maid);
        this.myMaid = maid;

    }
    
    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        LOGGER.info("mixin onContentsChanged");
        handleSpellBookChange(slot);
    }
    
    /**
     * 处理法术书变化
     * @param slot 变化的槽位
     */
    private void handleSpellBookChange(int slot) {
        try {
            if (myMaid == null || myMaid.level().isClientSide()) {
                return;
            }
            
            SpellBookManager manager = SpellBookManager.getOrCreateManager(myMaid);
            if (manager == null) {
                return;
            }
            manager.updateSpellBooks();
            
        } catch (Exception e) {
            LOGGER.error("Error handling spell book change in backpack slot {}: {}", slot, e.getMessage(), e);
        }
    }
    
} 