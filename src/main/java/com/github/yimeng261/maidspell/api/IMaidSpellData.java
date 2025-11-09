package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;

public abstract class IMaidSpellData {

    protected static final Logger LOGGER = LogUtils.getLogger();
    protected final Map<String, Integer> spellCooldowns = new HashMap<>();

    // === 基本状态 ===
    protected LivingEntity target;
    protected final Set<ItemStack> spellBooks = new HashSet<>();
    protected final Set<Class<?>> spellBookKinds = new HashSet<>();
    protected boolean isCasting = false;


    public LivingEntity getTarget() {
        return this.target;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public static IMaidSpellData getOrCreate(UUID maidUUID){
        Global.LOGGER.error("should complete getOrCreate method");
        return null;
    }

    /**
     * 获取第一本法术书（兼容旧代码）
     * @return 返回第一本法术书，如果没有则返回 ItemStack.EMPTY
     */
    public ItemStack getSpellBook() {
        return spellBooks.isEmpty() ? ItemStack.EMPTY : spellBooks.iterator().next();
    }

    /**
     * 获取所有法术书
     * @return 法术书集合
     */
    public Set<ItemStack> getSpellBooks() {
        return spellBooks;
    }

    /**
     * 添加一本法术书
     * @param spellBook 要添加的法术书
     */
    public void addSpellBook(ItemStack spellBook, EntityMaid maid) {
        if(spellBook==null||spellBook.isEmpty()){
            return;
        }
        if(!BaubleStateManager.hasBauble(maid, MaidSpellItems.SPELL_OVERLIMIT_CORE)) {
            if (!canAddSpellBook(spellBook)) {
                return;
            }
        }
        spellBooks.add(spellBook);
        spellBookKinds.add(spellBook.getItem().getClass());
    }

    protected boolean canAddSpellBook(ItemStack spellBook) {
        Class<?> spellBookClass = spellBook.getItem().getClass();
        for(Class<?> spellBookKind : spellBookKinds) {
            if(spellBookKind.isAssignableFrom(spellBookClass)||spellBookClass.isAssignableFrom(spellBookKind)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 移除一本法术书
     * @param spellBook 要移除的法术书
     */
    public void removeSpellBook(ItemStack spellBook) {
        for(ItemStack spellBookItem : spellBooks) {
            if (ItemStack.isSameItemSameComponents(spellBookItem, spellBook)) {
                spellBooks.remove(spellBookItem);
                break;
            }
        }
        spellBookKinds.remove(spellBook.getItem().getClass());
    }

    /**
     * 清空所有法术书
     */
    public void clearSpellBooks() {
        spellBooks.clear();
        spellBookKinds.clear();
    }

    /**
     * 检查是否拥有特定的法术书
     * @param spellBook 要检查的法术书
     * @return 如果拥有返回 true
     */
    public boolean hasSpellBook(ItemStack spellBook) {
        return spellBooks.contains(spellBook);
    }

    public boolean isCasting() {
        return isCasting;
    }

    public void setCasting(boolean casting) {
        this.isCasting = casting;
    }

    /**
     * 重置施法状态
     */
    public void resetCastingState() {
        this.isCasting = false;
        this.target = null;
    }

    public boolean isSpellOnCooldown(String spellId) {
        if (spellId == null) return true;
        int remainingCooldown = spellCooldowns.getOrDefault(spellId, 0);
        return remainingCooldown > 0;
    }

    public void setSpellCooldown(String spellId, int cooldownTicks, EntityMaid maid) {
        CoolDown coolDown = new CoolDown(cooldownTicks,maid);
        if (spellId != null) {
            Global.commonCoolDownCalc.forEach(func->{
                func.apply(coolDown);
            });

            Global.baubleCoolDownCalc.forEach((item, func)->{
                if(BaubleStateManager.hasBauble(maid, item)) {
                    func.apply(coolDown);
                }
            });

            spellCooldowns.put(spellId, coolDown.cooldownticks);
        }
    }

    public int getSpellCooldown(String spellId) {
        return spellCooldowns.getOrDefault(spellId, 0);
    }

    /**
     * 更新所有法术的冷却时间(每秒一次)
     */
    public void updateCooldowns() {
        spellCooldowns.replaceAll((spellId, cooldown) -> Math.max(0, cooldown - 20));
        spellCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    public static class CoolDown{
        public int cooldownticks;
        public EntityMaid maid;
        public CoolDown(int cooldownTicks, EntityMaid maid) {
            this.cooldownticks = cooldownTicks;
            this.maid = maid;
        }
    }

}
