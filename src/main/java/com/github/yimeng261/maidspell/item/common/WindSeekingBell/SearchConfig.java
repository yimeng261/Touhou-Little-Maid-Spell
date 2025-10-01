package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

/**
 * 寻风之铃结构搜索配置类
 * 集中管理所有搜索相关的配置参数
 */
public class SearchConfig {
    
    // ========== 搜索优化常量 ==========
    
    /** 最大搜索半径（区块） */
    public static final int MAX_SEARCH_RADIUS = 64000;
    
    /** 搜索步长，基于hidden_retreat稀少特性增大步长 */
    public static final int SEARCH_STEP = 4;
    
    /** 樱花林3×3区域检查半径 */
    public static final int CHERRY_GROVE_CHECK_RADIUS = 2;
    
    
    // ========== 缓存机制常量 ==========
    
    /** 缓存过期时间：5分钟（毫秒） */
    public static final int CACHE_EXPIRY_TIME = 300000;
    
    /** 缓存失效的移动阈值（方块）- 约31个区块 */
    public static final int CACHE_MOVE_THRESHOLD = 5000;
    
    /** 搜索区域大小（方块）- 用于生成searchKey */
    public static final int SEARCH_REGION_SIZE = 1000;
    
    
    // ========== 多线程搜索配置 ==========
    
    /** 最小线程数 */
    public static final int MIN_THREADS = 2;
    
    /** 最大线程数，避免过多线程造成上下文切换开销 */
    public static final int MAX_THREADS = 64;
    
    /** 每个小方格的大小（区块） */
    public static final int SECTOR_SIZE = 1000;
    
    
    // ========== 常用位置偏移量 ==========
    
    /** 区块中心偏移量（方块） */
    public static final int CHUNK_CENTER_OFFSET = 8;
    
    /** 默认结构Y坐标 */
    public static final int DEFAULT_STRUCTURE_Y = 64;
    
    
    // ========== 线程池配置 ==========
    
    /** 获取推荐的线程池大小 */
    public static int getRecommendedThreadPoolSize() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.min(MAX_THREADS, Math.max(MIN_THREADS, processors));
    }
    
    /** 线程名称前缀 */
    public static final String THREAD_NAME_PREFIX = "WindSeekingBell-Search-";
    
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
    
    
    // 私有构造函数，防止实例化
    private SearchConfig() {
        throw new AssertionError("SearchConfig is a utility class and should not be instantiated");
    }
}
