package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import java.util.List;

/**
 * 万法酒狐由服务端计时驱动的动画的唯一知识来源：动画名、时长、终止方式、中途事件。
 *
 * <p>这些知识原先抄在三个互不知情的地方——动画 JSON 的 {@code animation_length}、实体里的
 * {@code PHASE_TRANSITION_TICKS} 一类常量、以及客户端的 {@code hasAnimationFinished()}。
 * 集中到这里之后，{@code WinefoxActionDataTest} 会把每一项与动画文件对账，
 * 在 Blockbench 里改了时长而没回来改代码，测试当场变红。
 *
 * <p><b>本枚举不引任何 Minecraft / Forge / 铁魔法类型</b>，只有字符串和数字。一是对账测试
 * 不用启动 Forge 就能 {@code values()}；二是没装铁魔法时加载它也是安全的。
 * {@code RawAnimation} 的缓存留到接入实体那一步再说。
 */
public enum WinefoxAction {

    /** 无动作。这是唯一没有动画的一项。 */
    NONE(null, 0, WinefoxTermination.NONE),

    STAFF_ATTACK_1("staff_attack_1", 20, WinefoxTermination.ONE_SHOT),
    STAFF_ATTACK_2("staff_attack_2", 20, WinefoxTermination.ONE_SHOT),

    SWORD_ATTACK_1("sword_attack_01", 30, WinefoxTermination.ONE_SHOT),
    SWORD_ATTACK_2("sword_attack_02", 20, WinefoxTermination.ONE_SHOT),
    SWORD_ATTACK_3("sword_attack_03", 18, WinefoxTermination.ONE_SHOT),
    SWORD_ATTACK_4("sword_attack_04", 19, WinefoxTermination.ONE_SHOT),

    /**
     * 进二阶段：第 55t 半径 5 格击退，同一 tick 把主手从法杖换成长剑。
     *
     * <p>2.75s 正好落在动画把武器缩到 0 的那一小段（2.625s~3.0s）正中间 ——
     * 换手是瞬间的，得有一段看不见的窗口盖住，不然会当场闪一下。
     */
    PHASE_TRANSITION("phase_transition", 120, WinefoxTermination.ONE_SHOT,
            Event.at(55, EventKind.KNOCKBACK),
            Event.at(55, EventKind.WEAPON_SWAP)),

    /**
     * 回一阶段：被治疗回半血以上时退形。
     *
     * <p><b>共用 {@code phase_transition} 这条动画</b>，只是换手方向相反（长剑→法杖）。
     * 没单独做一条逆向动画：两个方向都是“站定、发光、换武器”，
     * 同一段表演够用。击退也保留，否则贴身的人看不出她在切形态。
     *
     * <p>它不走 {@link #worthResending()}：与进二阶段同理，120t 够长，晚到的人值得补一遍。
     */
    PHASE_REVERT("phase_transition", 120, WinefoxTermination.ONE_SHOT,
            Event.at(55, EventKind.KNOCKBACK),
            Event.at(55, EventKind.WEAPON_SWAP)),

    /**
     * 施法。动画名与时长在运行时由法术决定，见 {@link WinefoxCastAnimation}；
     * 时长由铁魔法 {@code handleCastDuration()} 管，不走 {@code actionTicks}。
     */
    CAST(null, 0, WinefoxTermination.EXTERNAL),

    /**
     * 战败。
     *
     * <p><b>它属于顶层的「战败」状态，不属于动作区域</b>，永远不走 {@code action} 控制器，
     * 也不由 {@code beginAction} 发起——列在这里只是因为它的时长是同一类知识
     * （{@code DEFEAT_ANIMATION_TICKS} 与 {@code defeat} 的 2.0s 又是一份手抄），
     * 放进来就能白拿一份对账。遍历 {@code values()} 做动作逻辑时要和
     * {@link #NONE}、{@link #CAST} 一样过滤掉。
     */
    DEFEAT("defeat", 40, WinefoxTermination.HOLD_LAST_FRAME);

    private static final WinefoxAction[] BY_ID = values();

    private final String animationName;
    private final int durationTicks;
    private final WinefoxTermination termination;
    private final List<Event> events;

    WinefoxAction(String animationName, int durationTicks, WinefoxTermination termination,
                  Event... events) {
        this.animationName = animationName;
        this.durationTicks = durationTicks;
        this.termination = termination;
        this.events = List.of(events);
    }

    /** 动画文件里的轨道名；{@link #NONE} 与 {@link #CAST} 没有，返回 {@code null}。 */
    public String animationName() {
        return this.animationName;
    }

    /** 服务端计时的总 tick 数，等于 {@code ceil(animation_length * 20)}。 */
    public int durationTicks() {
        return this.durationTicks;
    }

    public WinefoxTermination termination() {
        return this.termination;
    }

    /** 播放途中要派发的副作用，按 tick 声明；绝大多数动作是空的。 */
    public List<Event> events() {
        return this.events;
    }

    /**
     * 是否有自己的动画轨道。对账测试与触发逻辑都靠这个过滤。
     *
     * <p>注意动画名不是唯一的：{@link #PHASE_TRANSITION} 与 {@link #PHASE_REVERT}
     * 共用 {@code phase_transition}，区别只在服务端的换手方向。
     */
    public boolean hasOwnAnimation() {
        return this.animationName != null;
    }

    /**
     * 动作期间是否锁死移动（{@code getNavigation().stop()} + 清零位移）。
     *
     * <p>照抄现状：只有转阶段（两个方向）锁移动，近战与施法不锁。
     */
    public boolean locksMovement() {
        return this == PHASE_TRANSITION || this == PHASE_REVERT;
    }

    /**
     * 触发之后才进入追踪范围的玩家，值不值得给他单独补发一遍。
     *
     * <p>{@code triggerAnim} 是发完即忘的，晚到的人什么都收不到。近战 20t 补发反而容易
     * 让人看到半截动作，所以只有 120t 的转阶段值得。
     *
     * <p><b>这只是补发条件的一半。</b>另一半是循环施法，它由
     * {@link WinefoxCastAnimation#worthResending()} 回答——{@link #CAST} 自己
     * {@link #animationName()} 是 {@code null}，压根给不出该补发哪条动画。
     * 补发点要把两边或起来。
     */
    public boolean worthResending() {
        return this == PHASE_TRANSITION || this == PHASE_REVERT;
    }

    /**
     * 同步给客户端时用的整数编号。
     *
     * <p>{@link #byId(int)} 是它唯一的反函数——两个方向共用一份映射，改这里就两边一起改。
     * 注意 {@code NONE.id()} 是 {@code 0}，不是历史上那个 {@code -1}；
     * {@code defineSynchedData} 的默认值必须跟着走，否则实体一生成客户端就会补放一次攻击动画。
     *
     * <p>这层编号只在同一次会话内的同步里用，不落 NBT（存档里存的是
     * {@code WinefoxTransitionTicks} 一类的计时，加载时重新推出动作），所以常量顺序可以放心调整。
     * 换成 {@code triggerAnim} 之后整个编号会一起消失。
     */
    public int id() {
        return this.ordinal();
    }

    /** 编号越界一律当 {@link #NONE}，陈旧的 {@code -1} 也能安全落地。 */
    public static WinefoxAction byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
    }

    /** 动画播放途中的一个副作用。 */
    public record Event(int tick, EventKind kind) {
        public static Event at(int tick, EventKind kind) {
            return new Event(tick, kind);
        }
    }

    public enum EventKind {
        /** 转阶段击退。 */
        KNOCKBACK,
        /** 转阶段中途把主手武器换成本阶段该拿的那把。 */
        WEAPON_SWAP
    }
}
