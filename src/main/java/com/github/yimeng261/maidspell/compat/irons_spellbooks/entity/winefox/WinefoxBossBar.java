package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/**
 * 酒狐血条的识别键与显示键。独立于 ISS 实体类，供无条件注册的客户端渲染器读取。
 */
public final class WinefoxBossBar {

    /** 见类注释：语言文件里的值恒为 {@code "%s"}，只作识别标记用。 */
    public static final String NAME_KEY = "entity.touhou_little_maid_spell.stellar_witch.bossbar";

    /** 血条上显示的固定称号；中英文各自翻译，见语言文件的 {@code .bossbar.title}。 */
    public static final String TITLE_KEY = "entity.touhou_little_maid_spell.stellar_witch.bossbar.title";

    private WinefoxBossBar() {
    }
}
