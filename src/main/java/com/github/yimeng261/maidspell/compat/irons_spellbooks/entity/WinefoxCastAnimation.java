package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * 万法酒狐的施法动画。
 *
 * <p>这份清单原先抄了两遍：{@code MagicalWinefoxBossEntity.ISS_CAST_ANIMATIONS} 一遍，
 * {@code WinefoxBossResourceValidationTest.REQUIRED_CAST_ANIMATIONS} 又一遍，
 * 加动画时漏改哪边都不会有人告诉你。合并到这里之后，实体和测试都从这一处读。
 *
 * <p>这里同时是与铁魔法耦合的收口点：{@link #key()} 必须逐字等于铁魔法
 * {@code AnimationHolder.getForPlayer().getPath()} 的返回值，对不上就静默失配
 * （现在的表现是 {@code castAnimation == null} 之后直接 {@code PlayState.STOP}，无日志）。
 * 集中到一处之后至少可以加告警。
 *
 * <p>与 {@link WinefoxAction} 一样，本枚举不引任何 Minecraft / Forge / 铁魔法类型。
 */
public enum WinefoxCastAnimation {

    INSTANT_PROJECTILE("instant_projectile", WinefoxTermination.ONE_SHOT),
    INSTANT_SELF("instant_self", WinefoxTermination.ONE_SHOT),
    INSTANT_SLASH("instant_slash", WinefoxTermination.ONE_SHOT),
    KATANA_UPSLASH("katana_upslash", WinefoxTermination.ONE_SHOT),
    CHARGED_THROW("charged_throw", WinefoxTermination.ONE_SHOT),
    CHARGE_WAVY("charge_wavy", WinefoxTermination.ONE_SHOT),
    CHARGE_RAISED_HAND("charge_raised_hand", WinefoxTermination.ONE_SHOT),
    CHARGE_ARROW("charge_arrow", WinefoxTermination.ONE_SHOT),
    CHARGE_SPIT("charge_spit", WinefoxTermination.ONE_SHOT),
    CHARGE_SPIT_FINISH("charge_spit_finish", WinefoxTermination.ONE_SHOT),
    LONG_CAST_FINISH("long_cast_finish", WinefoxTermination.ONE_SHOT),
    TOUCH_GROUND("touch_ground", WinefoxTermination.ONE_SHOT),
    CAST_T_POSE("cast_t_pose", WinefoxTermination.ONE_SHOT),
    STOMP("stomp", WinefoxTermination.ONE_SHOT),
    HORIZONTAL_SLASH_ONE_HANDED("horizontal_slash_one_handed", WinefoxTermination.ONE_SHOT),
    OVERHEAD_TWO_HANDED_SWING("overhead_two_handed_swing", WinefoxTermination.ONE_SHOT),

    /**
     * 举枪。剑牢法术的起手动作，是本模组自己加的一条，不来自铁魔法。
     *
     * <p>{@link #key()} 因此不必对上铁魔法的任何返回值 ——
     * 它由 {@code WinefoxBossSpells.getCastAnimation} 对剑牢单独指定。
     * 动画本身在 boss 动画文件里叫 {@code iss:spear_throw}，前缀与其余施法动画一致。
     */
    SPEAR_THROW("spear_throw", WinefoxTermination.ONE_SHOT),

    LONG_CAST("long_cast", WinefoxTermination.LOOP),
    CHARGE_BLACK_HOLE("charge_black_hole", WinefoxTermination.LOOP),
    CROSS_ARMS("cross_arms", WinefoxTermination.LOOP),
    CONTINUOUS_THRUST("continuous_thrust", WinefoxTermination.LOOP),
    CONTINUOUS_OVERHEAD("continuous_overhead", WinefoxTermination.LOOP);

    /** 动画文件里施法轨道统一带的前缀。 */
    public static final String ANIMATION_PREFIX = "iss:";

    private static final Map<String, WinefoxCastAnimation> BY_KEY = buildIndex();

    private final String key;
    private final WinefoxTermination termination;

    WinefoxCastAnimation(String key, WinefoxTermination termination) {
        this.key = key;
        this.termination = termination;
    }

    /** 铁魔法 {@code AnimationHolder.getForPlayer().getPath()} 的返回值，必须逐字一致。 */
    public String key() {
        return this.key;
    }

    /** 动画文件里的轨道名，就是 {@link #key()} 加上 {@code iss:} 前缀。 */
    public String animationName() {
        return ANIMATION_PREFIX + this.key;
    }

    public WinefoxTermination termination() {
        return this.termination;
    }

    /**
     * 循环施法要不要给晚到的玩家补发。
     *
     * <p>是 {@link WinefoxAction#worthResending()} 的另一半：一次性施法最长也就 10s 且多半
     * 已经播完，循环施法才会一直摆在那儿让人看出不对。
     */
    public boolean worthResending() {
        return this.termination == WinefoxTermination.LOOP;
    }

    /** 按铁魔法给的 key 查；查不到返回 {@code null}（说明两边的动画清单已经失配）。 */
    public static WinefoxCastAnimation byKey(String key) {
        return BY_KEY.get(key);
    }

    private static Map<String, WinefoxCastAnimation> buildIndex() {
        Map<String, WinefoxCastAnimation> index = new HashMap<>();
        for (WinefoxCastAnimation animation : values()) {
            WinefoxCastAnimation previous = index.put(animation.key, animation);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Winefox cast animation key: " + animation.key);
            }
        }
        return Map.copyOf(index);
    }
}
