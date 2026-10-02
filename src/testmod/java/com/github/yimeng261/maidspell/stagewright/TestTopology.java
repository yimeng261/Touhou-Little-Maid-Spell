package com.github.yimeng261.maidspell.stagewright;

/**
 * 场景所属拓扑。
 * <p>StageWright 只在 hold 任务里传 {@code stagewright.topology}，所以各运行配置自己设
 * {@code maidspell.test.topology}；两者都没有时按专用服务器处理。
 */
public enum TestTopology {
    DEDICATED_SERVER("dedicatedServer"),
    INTEGRATED_SERVER("integratedServer"),
    INTEGRATED_SERVER_SHARED("integratedServerShared"),
    /** 只装车万女仆（可选联动全部缺席）。 */
    DEDICATED_SERVER_MINIMAL("dedicatedServerMinimal"),
    /** 全套联动去掉农夫乐事。 */
    DEDICATED_SERVER_NO_FARMERS_DELIGHT("dedicatedServerNoFarmersDelight"),
    /** 专用服 + 经网络加入的真实客户端。 */
    DEDICATED_SERVER_WITH_CLIENT("dedicatedServerWithClient");

    private final String id;

    TestTopology(String id) {
        this.id = id;
    }

    public static TestTopology current() {
        String value = System.getProperty("maidspell.test.topology", System.getProperty("stagewright.topology"));
        if (value == null) {
            return DEDICATED_SERVER;
        }
        for (TestTopology topology : values()) {
            if (topology.id.equals(value)) {
                return topology;
            }
        }
        throw new IllegalStateException("未知的测试拓扑: " + value);
    }
}
