package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractSpellData implements ISpellData {

    private static final Logger LOGGER = LogUtils.getLogger();

    public final Map<String, Integer> spellCooldowns = new HashMap<>();

    // === 基本状态 ===
    public LivingEntity target;
    public ItemStack spellBook = ItemStack.EMPTY;
    public boolean isCasting = false;


    @Override
    public LivingEntity getTarget() {
        return this.target;
    }

    @Override
    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    @Override
    public ItemStack getSpellBook() {
        return spellBook;
    }

    @Override
    public void setSpellBook(ItemStack spellBook) {
        this.spellBook = spellBook;
    }

    @Override
    public boolean isCasting() {
        return isCasting;
    }

    @Override
    public void setCasting(boolean casting) {
        this.isCasting = casting;
    }

    @Override
    public boolean isSpellOnCooldown(String spellId) {
        if (spellId == null) return true;
        int remainingCooldown = spellCooldowns.getOrDefault(spellId, 0);
        return remainingCooldown > 0;
    }

    @Override
    public void setSpellCooldown(String spellId, int cooldownTicks, EntityMaid maid) {
        CoolDown coolDown = new CoolDown(cooldownTicks,maid);
        if (spellId != null) {
            Global.common_coolDownCalc.forEach(func->{
                func.apply(coolDown);
            });

            Global.bauble_coolDownCalc.forEach((item,func)->{
                if(BaubleStateManager.hasBauble(maid, item)) {
                    func.apply(coolDown);
                }
            });

            spellCooldowns.put(spellId, coolDown.cooldownticks);
        }
    }

    @Override
    public int getSpellCooldown(String spellId) {
        return spellCooldowns.getOrDefault(spellId, 0);
    }

    /**
     * 更新所有法术的冷却时间(每秒一次)
     */
    @Override
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
