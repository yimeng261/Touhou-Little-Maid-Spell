package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

public enum WinefoxBossSpellAction {
    MAGIC_MISSILE,
    COUNTERSPELL,
    MAGIC_ARROW,
    SUMMON_SWORDS,
    /** Chain lightning replaced the old fireball entry in the default pool. */
    CHAIN_LIGHTNING,
    /** Sunbeam (烈阳射线), fixed at level 3 in phase one defaults. */
    SUNBEAM,
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
     */
    TRIPLE_STAR_ARROW,

    /**
     * 伴星黑洞：头顶 5 格的跟随型小黑洞，只吸别人的弹射物。
     */
    COMPANION_BLACK_HOLE,
    SPELLBREAKING_ECHO,
    /** 三垣连星：以目标为中心收缩的三棱柱场地。 */
    THREE_ENCLOSURES,
    /** 七星引路：固定北斗星阵，七次落击后由斗柄完成必中收束。 */
    SEVEN_STAR_GUIDANCE,
    PALM_BLACK_HOLE;

    /** Ordinary spells weigh two; only the indicated phase reduces each special spell. */
    public int defaultSelectionWeight(boolean secondPhase) {
        if (this == BLACK_HOLE && !secondPhase || this == SWORD_PRISON && secondPhase) {
            return 1;
        }
        return 2;
    }

}
