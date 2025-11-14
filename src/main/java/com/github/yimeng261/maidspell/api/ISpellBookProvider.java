package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 法术书提供者抽象类
 * 用于支持不同模组的法术书系统
 * 
 * @param <T> 对应的法术数据类型，必须继承自 IMaidSpellData
 * @param <S> 对应的法术类型（不是法术物品类）
 */
public abstract class ISpellBookProvider<T extends IMaidSpellData, S> {

    /**
     * 数据工厂方法，用于获取或创建指定女仆的法术数据
     */
    protected final Function<UUID, T> dataFactory;
    
    /**
     * 法术类的Class对象，用于类型识别
     */
    protected final Class<S> spellClass;

    /**
     * 构造函数
     * @param dataFactory 数据工厂方法，根据女仆UUID获取或创建对应的法术数据
     * @param spellClass 法术类的Class对象
     */
    protected ISpellBookProvider(Function<UUID, T> dataFactory, Class<S> spellClass) {
        this.dataFactory = dataFactory;
        this.spellClass = spellClass;
    }

    /**
     * 从所有可用的法术书中收集法术
     * @param maid 女仆实体
     * @return 收集到的法术列表
     */
    protected List<S> collectSpellFromAvailableSpellBooks(EntityMaid maid){
        List<S> spells = new ArrayList<>();
        for(ItemStack spellBook : getData(maid).getSpellBooks()){
            spells.addAll(collectSpellFromSingleSpellBook(spellBook,maid));
        }
        return spells;
    }
    
    /**
     * 从单个法术书中收集法术
     * @param spellBook 法术书物品
     * @param maid 女仆
     * @return 该法术书中的所有法术
     */
    protected abstract List<S> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid);

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
        if(maid.level().isClientSide){
            return;
        }
        if(isAddOperation) {
            if (isSpellBook(spellBook)) {
                getData(maid).addSpellBook(spellBook,maid);
            }
        }else{
            getData(maid).removeSpellBook(spellBook);
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
     * 检查物品是否为该模组的法术书
     */
    public abstract boolean isSpellBook(ItemStack itemStack);

    /**
     * 执行法术施放
     */
    public void castSpell(EntityMaid entityMaid){
        IMaidSpellData spellData = getData(entityMaid);
        if(spellData.getSpellBooks().isEmpty() || spellData.isCasting()){
            return;
        }
        initiateCasting(entityMaid);
    }

    /**
     * 更新法术冷却：每次一秒
     */
    public void updateCooldown(EntityMaid maid){
        getData(maid).updateCooldowns();
    }
    
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