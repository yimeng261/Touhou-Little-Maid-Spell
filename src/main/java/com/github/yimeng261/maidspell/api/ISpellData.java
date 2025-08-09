package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;


public interface ISpellData {

    public LivingEntity getTarget();

    public void setTarget(LivingEntity target);

    public ItemStack getSpellBook();

    public void setSpellBook(ItemStack spellBook);

    public boolean isCasting();

    public void setCasting(boolean casting);

    public boolean isSpellOnCooldown(String spellId);

    public void setSpellCooldown(String spellId, int cooldownTicks, EntityMaid maid);

    public int getSpellCooldown(String spellId);

    public void updateCooldowns();

    public void resetCastingState();

}
