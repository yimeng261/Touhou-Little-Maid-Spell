package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

/**
 * 酒狐专属法术的标记。实现类须显式覆写以下方法，阻止撰写和战利品获取。
 * 配置中的 AllowCrafting 只是默认值，玩家可重新启用；代码限制不受该配置影响。
 * 仅本模组的专属法术实现此接口，原版铁魔法法术不受影响。
 */
public interface BossExclusiveSpell {

    default boolean allowCrafting() {
        return false;
    }

    default boolean allowLooting() {
        return false;
    }
}
