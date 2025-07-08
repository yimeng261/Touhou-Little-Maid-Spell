package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 法术书提供者接口
 * 用于支持不同模组的法术书系统
 */
public interface ISpellBookProvider {

    
    /**
     * 检查物品是否为该模组的法术书
     */
    boolean isSpellBook(ItemStack itemStack);

    /**
     * 执行法术施放（简单版本）
     */
    boolean castSpell(EntityMaid entityMaid);

    /**
     * 更新法术冷却：每次一秒
     */
    void updateCooldown(EntityMaid maid);
    
    /**
     * 设置当前目标
     */
    void setTarget(EntityMaid maid, LivingEntity target);
    
    /**
     * 获取当前目标
     */
    LivingEntity getTarget(EntityMaid maid);
    
    /**
     * 设置法术书物品
     */
    void setSpellBook(EntityMaid maid, ItemStack spellBook);
    
    /**
     * 检查是否正在施法
     */
    boolean isCasting(EntityMaid maid);
    
    /**
     * 开始施法
     */
    boolean initiateCasting(EntityMaid maid);
    
    /**
     * 处理持续性施法的tick
     */
    void processContinuousCasting(EntityMaid maid);
    
    /**
     * 停止当前施法
     */
    void stopCasting(EntityMaid maid);


} 