package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import java.util.List;

/**
 * 万法酒狐由服务端计时驱动的动画的唯一知识来源：动画名、时长、终止方式、中途事件。
 *
 * <p>这些知识原先抄在三个互不知情的地方——动画 JSON 的 {@code animation_length}、实体里的
 * {@code PHASE_TRANSITION_TICKS} 一类常量、以及客户端的 {@code hasAnimationFinished()}。 集中到这里之后，{@code WinefoxActionDataTest} 会把每一项与动画文件对账， 在 Blockbench 里改了时长而没回来改代码，测试当场变红。
 *
 * <p><b>本枚举不引任何 Minecraft / Forge / 铁魔法类型</b>，只有字符串和数字。一是对账测试
 * 不用启动 Forge 就能 {@code values()}；二是没装铁魔法时加载它也是安全的。 {@code RawAnimation} 的缓存留到接入实体那一步再说。
 */
public enum WinefoxAction {

    /**
     * 无动作。这是唯一没有动画的一项。
     */
    NONE(null, 0, WinefoxTermination.NONE),

    STAFF_ATTACK_1("staff_attack_1", 20, WinefoxTermination.ONE_SHOT),
    STAFF_ATTACK_2("staff_attack_2", 20, WinefoxTermination.ONE_SHOT),

    SWORD_ATTACK_1("sword_attack_1", 30, WinefoxTermination.ONE_SHOT),
    SWORD_ATTACK_2("sword_attack_2", 20, WinefoxTermination.ONE_SHOT),
    SWORD_ATTACK_3("sword_attack_3", 18, WinefoxTermination.ONE_SHOT),
    SWORD_ATTACK_4("sword_attack_4", 19, WinefoxTermination.ONE_SHOT),

    /**
     * 转阶段：第 55t 半径 5 格击退，同一 tick 把主手换成本阶段该拿的那把。
     *
     * <p>2.75s 正好落在动画把武器缩到 0 的那一小段（2.625s~3.0s）正中间 ——
     * 换手是瞬间的，得有一段看不见的窗口盖住，不然会当场闪一下。
     *
     * <p><b>进二阶段（法杖→长剑）和被治疗退形回一阶段（长剑→法杖）是同一项。</b>
     * 没单独做一条逆向动画：两个方向都是“站定、发光、换武器”，同一段表演够用；
     * 击退两边都保留，否则贴身的人看不出她在切形态。方向本身不在这里， 而在实体的 {@code phaseTransitionTarget}（那一位才是落 NBT 的）， 所以这里一项就够。
     */
    PHASE_TRANSITION("phase_transition", 120, WinefoxTermination.ONE_SHOT,
        Event.at(55, EventKind.KNOCKBACK),
        Event.at(55, EventKind.WEAPON_SWAP)),

    /**
     * 战败。
     *
     * <p><b>它属于顶层的「战败」状态，不属于动作区域</b>，不由 {@code beginAction} 发起，
     * 而是由 {@code DEFEATED} 同步标志驱动。遍历 {@code values()} 做动作逻辑时要和 {@link #NONE} 一样过滤掉。
     *
     * <p>动画名是模型包作者起的 {@code death}（我们这边原先叫 {@code defeat}）。
     * 时长 10000s = 200000t 也是作者的手法：这个 geckolib3 分支里 {@code hold_on_last_frame} 和 {@code play_once} 行为一致，
     * 播完控制器直接 STOP、 姿势弹回，所以「定格」只能靠把动画拉长到播不完。
     * 作者给每条 {@code hold_mainhand:*} 用的都是这一招。这里的 200000 不是什么倒计时，只是照实抄动画时长好让对账测试成立。
     */
    DEFEAT("death", 200000, WinefoxTermination.HOLD_LAST_FRAME);

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

    /**
     * 动画文件里的轨道名；只有 {@link #NONE} 没有，返回 {@code null}。
     */
    public String animationName() {
        return this.animationName;
    }

    /**
     * 服务端计时的总 tick 数，等于 {@code ceil(animation_length * 20)}。
     */
    public int durationTicks() {
        return this.durationTicks;
    }

    public WinefoxTermination termination() {
        return this.termination;
    }

    /**
     * 播放途中要派发的副作用，按 tick 声明；绝大多数动作是空的。
     */
    public List<Event> events() {
        return this.events;
    }

    /**
     * 是否有自己的动画轨道。对账测试与触发逻辑都靠这个过滤。
     */
    public boolean hasOwnAnimation() {
        return this.animationName != null;
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

    /**
     * 编号越界一律当 {@link #NONE}，陈旧的 {@code -1} 也能安全落地。
     */
    public static WinefoxAction byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
    }

    /**
     * 动画播放途中的一个副作用。
     */
    public record Event(int tick, EventKind kind) {
        public static Event at(int tick, EventKind kind) {
            return new Event(tick, kind);
        }
    }

    public enum EventKind {
        /**
         * 转阶段击退。
         */
        KNOCKBACK,
        /**
         * 转阶段中途把主手武器换成本阶段该拿的那把。
         */
        WEAPON_SWAP
    }
}
