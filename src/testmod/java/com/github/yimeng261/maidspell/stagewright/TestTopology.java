package com.github.yimeng261.maidspell.stagewright;

/**
 * 场景所属拓扑。
 * <p>StageWright 只在 hold 任务里传 {@code stagewright.topology}，所以各运行配置自己设
 * {@code maidspell.test.topology}；两者都没有时按专用服务器处理。
 */
public enum TestTopology {
    DEDICATED_SERVER("dedicatedServer"),
    INTEGRATED_SERVER("integratedServer"),
    INTEGRATED_SERVER_SHARED("integratedServerShared");

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
