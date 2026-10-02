package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import java.util.List;

/**
 * 万法酒狐由服务端计时驱动的动画的唯一知识来源：动画名、时长、终止方式、中途事件。这些知识原先散在动画 JSON 的
 * {@code animation_length}、实体的 {@code PHASE_TRANSITION_TICKS} 一类常量、客户端的 {@code hasAnimationFinished()}，
 * 集中后只有一个来源：改动画时长只改这一处。本枚举不引任何 Minecraft / Forge / 铁魔法类型，只有字符串和数字——
 * 对账工具不用启动 Forge 就能 {@code values()}，没装铁魔法时加载也安全。{@code RawAnimation} 的缓存留到接入实体那一步。
 */
public enum WinefoxAction {

    /**
     * 无动作。这是唯一没有动画的一项。
     */
    NONE(null, 0, WinefoxTermination.NONE),

    STAFF_ATTACK_1("staff_attack_1", 20, WinefoxTermination.ONE_SHOT),
    STAFF_ATTACK_2("staff_attack_2", 20, WinefoxTermination.ONE_SHOT),

    SWORD_ATTACK_1("sword_attack_1", 30, WinefoxTermination.ONE_SHOT, Event.sound(2, "atk3")),
    SWORD_ATTACK_2("sword_attack_2", 20, WinefoxTermination.ONE_SHOT, Event.sound(2, "atk2")),
    SWORD_ATTACK_3("sword_attack_3", 18, WinefoxTermination.ONE_SHOT, Event.sound(1, "atk3")),
    SWORD_ATTACK_4("sword_attack_4", 19, WinefoxTermination.ONE_SHOT, Event.sound(4, "atk3")),

    /**
     * 转阶段：第 55t 半径 5 格击退，同一 tick 把主手换成本阶段该拿的那把。
     * 2.75s 正好落在动画把武器缩到 0 的那一小段（2.625s~3.0s）正中间——换手是瞬间的，得有一段看不见的窗口盖住，不然会当场闪一下。
     *
     * <p><b>进二阶段（法杖→长剑）和被治疗退形回一阶段（长剑→法杖）是同一项。</b>没单独做逆向动画：两个方向都是“站定、发光、换武器”，
     * 同一段表演够用；击退两边都保留，否则贴身的人看不出她在切形态。方向不在动画里，而在实体的 {@code phaseTransitionTarget}（落 NBT 的那一位），所以一项就够。
     */
    PHASE_TRANSITION("phase_transition", 120, WinefoxTermination.ONE_SHOT,
        Event.sound(1, "atked"), Event.sound(45, "shengyin"),
        Event.at(55, EventKind.KNOCKBACK),
        Event.at(55, EventKind.WEAPON_SWAP)),

    /**
     * 战败。<b>它属于顶层的「战败」状态，不属于动作区域</b>，不由 {@code beginAction} 发起，而由 {@code DEFEATED} 同步标志驱动；
     * 遍历 {@code values()} 做动作逻辑时要和 {@link #NONE} 一样过滤掉。
     *
     * <p>动画名是模型包里的 {@code death}，时长 5 秒，服务端的归位等待也从该时长推导，
     * 确保战败演出完整播放后再回秋千。
     */
    DEFEAT("death", 100, WinefoxTermination.HOLD_LAST_FRAME),

    SPEAR_THROW("iss:spear_throw", 64, WinefoxTermination.ONE_SHOT,
        Event.sound(12, "atk2"), Event.sound(38, "magic01_shoot"),
        Event.at(40, EventKind.PROJECTILE)),

    /** 开战行礼与服务端倒计时共用 90 tick；ONE_SHOT 覆盖模型 JSON 的循环设置。 */
    CURTSY_COMBAT("curtsy_combat", 90, WinefoxTermination.ONE_SHOT),

    /** 第一问的 30 tick 动画循环至玩家回应，最多持续 300 tick。 */
    VOW_1("vow_1", 300, WinefoxTermination.LOOP),

    /**
     * 驯服的第二问：玩家再次右击，她当面把魂符交出去。
     *
     * <p>{@code 9s}（180t）是模型包里这条轨道的 {@code animation_length}；魂符在第 {@code 6s}（120t）出手，
     * 那一刻藏在 {@link #events()} 里而不是实体里 —— 与 {@code PHASE_TRANSITION} 的击退、{@code SPEAR_THROW}
     * 的出枪同一条路：改动画时长只改这一处，实体的计时永远从这儿推导。
     */
    VOW_2("vow_2", 180, WinefoxTermination.ONE_SHOT,
        Event.at(120, EventKind.SOUL_CHARM));

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
     *
     * <p>唯一的例外是 {@link WinefoxTermination#LOOP}：循环动作的 {@code animation_length} 只是<b>循环节</b>的长度，
     * 这里报的是「循环最多持续多久」—— 与动画文件对账时要按这一条把 {@link #VOW_1} 摘出去，
     * 它那 300t 是有意不等于 30t 的。
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

    /** 客户端同步编号；NONE 为 0，必须与实体同步字段的默认值一致。 */
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
    public record Event(int tick, EventKind kind, String sound) {
        public static Event at(int tick, EventKind kind) {
            return new Event(tick, kind, null);
        }

        public static Event sound(int tick, String name) {
            return new Event(tick, EventKind.SOUND, name);
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
        WEAPON_SWAP,
        PROJECTILE,
        /**
         * 驯服第二问的第 120t：把魂符交到玩家手上。
         */
        SOUL_CHARM,
        SOUND
    }
}
