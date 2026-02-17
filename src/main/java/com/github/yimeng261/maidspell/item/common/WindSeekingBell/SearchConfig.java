package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

/**
 * 寻风之铃结构搜索配置类
 * 集中管理所有搜索相关的配置参数
 */
public class SearchConfig {

    // ========== 搜索优化常量 ==========

    /** 最大搜索半径（区块） */
    public static final int MAX_SEARCH_RADIUS = 300;

    /**
     * 搜索步长
     */
    public static final int SEARCH_STEP = 4;

    // ========== 多线程搜索配置 ==========

    /** 最小线程数 */
    public static final int MIN_THREADS = 2;

    /** 最大线程数，避免过多线程造成上下文切换开销 */
    public static final int MAX_THREADS = 16;

    /** 每个小方格的大小（区块） */
    public static final int SECTOR_SIZE = 10;


    // ========== 常用位置偏移量 ==========

    /** 区块中心偏移量（方块） */
    public static final int CHUNK_CENTER_OFFSET = 8;

    /** 默认结构Y坐标 */
    public static final int DEFAULT_STRUCTURE_Y = 64;


    // ========== 线程池配置 ==========

    /** 获取推荐的搜索线程池大小（总线程的3/4用于搜索） */
    public static int getRecommendedThreadPoolSize() {
        int processors = Runtime.getRuntime().availableProcessors();
        int totalThreads = Math.min(MAX_THREADS, Math.max(MIN_THREADS, processors));
        // 3/4用于搜索任务，至少保留1个线程
        return Math.max(1, totalThreads * 3 / 4);
    }

    /**
     * 获取推荐的验证线程池大小（总线程的1/4用于验证）
     */
    public static int getRecommendedVerificationThreadPoolSize() {
        int processors = Runtime.getRuntime().availableProcessors();
        int totalThreads = Math.min(MAX_THREADS, Math.max(MIN_THREADS, processors));
        // 1/4用于验证任务，至少保留1个线程
        return Math.max(1, totalThreads / 4);
    }

    /** 搜索线程名称前缀 */
    public static final String THREAD_NAME_PREFIX = "WindSeekingBell-Search-";

    /**
     * 验证线程名称前缀
     */
    public static final String VERIFICATION_THREAD_NAME_PREFIX = "WindSeekingBell-Verify-";

    /** 线程优先级（稍低于普通优先级，避免影响主游戏逻辑） */
    public static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;


    // ========== 并发控制配置 ==========

    /**
     * 分段锁的段数（必须是2的幂次方）
     * 基于ServerLevel的分段锁策略：
     * - 同一世界的所有结构检查使用同一个锁（保证StructureCheck线程安全）
     * - 不同世界（主世界、下界、末地）映射到不同的锁段，可以并发执行
     * - 64个锁段足以处理多世界并发，同时避免过多的锁对象
     */
    public static final int LOCK_STRIPE_COUNT = 64;

    /**
     * 结构验证超时时间（毫秒）
     * 如果单个区块的结构验证超过此时间，将被跳过以避免搜索卡死
     * - 足够完成大多数正常的结构检查
     * - 不会让整个搜索过程变得太慢
     */
    public static final int STRUCTURE_VERIFICATION_TIMEOUT_MS = 600;


    // 私有构造函数，防止实例化
    private SearchConfig() {
        throw new AssertionError("SearchConfig is a utility class and should not be instantiated");
    }
}
