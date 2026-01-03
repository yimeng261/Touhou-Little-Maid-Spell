package com.github.yimeng261.maidspell.client.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;

/**
 * 魔法女仆提供的内部参数
 * <br>
 * 这个接口是内部使用不保证稳定
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-07-23 00:30
 */
public interface MaidSpellAnimateState {
    SpellData maidspell$getCastingSpell();

    AbstractSpell maidspell$getInstantCastSpellType();

    void maidspell$setInstantCastSpellType(AbstractSpell instantCastSpellType);

    boolean maidspell$getCancelCastAnimation();

    void maidspell$setCancelCastAnimation(boolean cancelCastAnimation);
}
