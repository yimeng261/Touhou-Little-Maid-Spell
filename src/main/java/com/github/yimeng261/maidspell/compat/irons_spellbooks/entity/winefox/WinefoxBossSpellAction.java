package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

public enum WinefoxBossSpellAction {
    MAGIC_MISSILE,
    COUNTERSPELL,
    MAGIC_ARROW,
    SUMMON_SWORDS,
    FIREBALL,
    LIGHTNING_LANCE,
    LIGHTNING_BOLT,
    ARROW_VOLLEY,
    EVASION,
    ARCANE_SHACKLE,
    HEAL,
    ABYSSAL_SHROUD,
    MODIFIED_STARFALL,
    MAGIC_SHOTGUN,
    VOID_PHASE,
    ECHOING_STRIKES,
    SHADOW_SLASH,
    MODIFIED_TELEPORT,
    STAR_SHADOW_STRIKE,
    SHOCKWAVE,
    DIVINE_SMITE,
    SWORD_PRISON,
    /** 净化：一、二阶段都会用，只在自己身上挂着负面效果时才有意义。 */
    CLEANSE,
    /** 黑洞：只有一阶段（法杖阶段）会放。 */
    BLACK_HOLE,
    /**
     * 三矢连星：万法酒狐专属，一次施法连发三支魔法箭。
     *
     * <p>和 {@link #MAGIC_ARROW} 是两项而不是替换关系：那一项是原版法术的单发，这一项是模组自己的
     * 三连发。两项各自有独立冷却，所以她既可能单发点射、也可能来一轮三连。
     */
    TRIPLE_STAR_ARROW,

    /**
     * 伴星黑洞：头顶 5 格的跟随型小黑洞，只吸别人的弹射物。
     *
     * <p>和 {@link #BLACK_HOLE} 是两项：那一项是原版那种把人和怪一起吸住的大黑洞，
     * 这一项是挂在头顶、专治弹道的防守件。两项各有独立冷却，她可以两个都放。
     */
    COMPANION_BLACK_HOLE,
    SPELLBREAKING_ECHO
}
