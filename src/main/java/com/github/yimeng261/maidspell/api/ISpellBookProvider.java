package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Function;

/**
 * 法术书提供者抽象类
 * 用于支持不同模组的法术书系统
 * 
 * @param <T> 对应的法术数据类型，必须继承自 IMaidSpellData
 */
public abstract class ISpellBookProvider<T extends IMaidSpellData> {

    /**
     * 数据工厂方法，用于获取或创建指定女仆的法术数据
     */
    protected final Function<UUID, T> dataFactory;

    /**
     * 构造函数
     * @param dataFactory 数据工厂方法，根据女仆UUID获取或创建对应的法术数据
     */
    protected ISpellBookProvider(Function<UUID, T> dataFactory) {
        this.dataFactory = dataFactory;
    }

    /**
     * 获取指定女仆的法术数据
     * @param maid 女仆实体
     * @return 对应的法术数据，如果女仆为null则返回null
     */
    protected T getData(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        return dataFactory.apply(maid.getUUID());
    }

    /**
     * 处理物品堆栈，用于初始化或更新法术书
     */
    public void handleItemStack(EntityMaid maid, ItemStack spellBook, boolean isAddOperation) {
        if(isAddOperation) {
            if (isSpellBook(spellBook)) {
                getData(maid).addSpellBook(spellBook);
            }
        }else{
            clearSpellItem(maid,spellBook);
        }

    }

    /**
     * 清除女仆的法术物品数据
     */
    public void clearSpellItems(EntityMaid maid) {
        getData(maid).clearSpellBooks();
        stopCasting(maid);
    }

    /**
     * 清除女仆的法术物品数据
     */
    public void clearSpellItem(EntityMaid maid, ItemStack spellItem) {
        getData(maid).removeSpellBook(spellItem);
    }

    /**
     * 检查物品是否为该模组的法术书
     */
    public abstract boolean isSpellBook(ItemStack itemStack);

    /**
     * 执行法术施放
     */
    public void castSpell(EntityMaid entityMaid){
        IMaidSpellData spellData = getData(entityMaid);
        if(spellData.spellBooks.isEmpty() || spellData.isCasting){
            return;
        }
        initiateCasting(entityMaid);
    }

    /**
     * 更新法术冷却：每次一秒
     */
    public abstract void updateCooldown(EntityMaid maid);
    
    /**
     * 设置当前目标
     */
    public void setTarget(EntityMaid maid, LivingEntity target) {
        T data = getData(maid);
        if (data != null) {
            data.setTarget(target);
        }
    }
    
    /**
     * 获取当前目标
     */
    public LivingEntity getTarget(EntityMaid maid) {
        T data = getData(maid);
        return data != null ? data.getTarget() : null;
    }

    
    /**
     * 检查是否正在施法
     */
    public boolean isCasting(EntityMaid maid) {
        T data = getData(maid);
        return data != null && data.isCasting();
    }
    
    /**
     * 开始施法
     */
    protected abstract void initiateCasting(EntityMaid maid);
    
    /**
     * 处理持续性施法的tick
     */
    public abstract void processContinuousCasting(EntityMaid maid);
    
    /**
     * 停止当前施法
     */
    public abstract void stopCasting(EntityMaid maid);


} 