package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/**
 * 酒狐 Boss 血条的两个名字键。
 *
 * <h2>{@link #NAME_KEY}：外壳，也是「这条血条是酒狐的」的唯一判据</h2>
 * <ul>
 *   <li>服务端 {@code MagicalWinefoxBossEntity.createBossBarName()} 把称号套在它下面，
 *       语言文件里这条的值就是 {@code "%s"}，所以玩家看到的字只有称号本身；</li>
 *   <li>客户端 {@code WinefoxBossBarOverlay} 拿 {@code Component.getContents()} 比对键名，
 *       认出来之后才换成自己的贴图，其余 Boss 血条一律不动。</li>
 * </ul>
 *
 * <p><b>为什么不放在实体类里</b>：实体类继承铁魔法的 {@code AbstractSpellCastingMob}，
 * 没装铁魔法时加载它会直接抛 {@code NoClassDefFoundError}；而血条渲染只在客户端注册、
 * 无条件生效，不该被这条依赖拖下水。这个类不引用任何 Minecraft 或铁魔法的类型，
 * 谁都能安全地读它。
 *
 * <h2>{@link #TITLE_KEY}：血条上真正显示的那行字</h2>
 * 血条名不再拿 {@code getDisplayName()} 拼，而是一个固定的称号——实体名（头顶名、刷怪蛋、
 * 对话里的自称）依旧是「星之魔女」，只有血条顶格写着「星之魔女酒狐」。
 * 命名牌也就不再影响血条：改了名也还是这个称号。
 */
public final class WinefoxBossBar {

    /** 见类注释：语言文件里的值恒为 {@code "%s"}，只作识别标记用。 */
    public static final String NAME_KEY = "entity.touhou_little_maid_spell.stellar_witch.bossbar";

    /** 血条上显示的固定称号；中英文各自翻译，见语言文件的 {@code .bossbar.title}。 */
    public static final String TITLE_KEY = "entity.touhou_little_maid_spell.stellar_witch.bossbar.title";

    private WinefoxBossBar() {
    }
}
