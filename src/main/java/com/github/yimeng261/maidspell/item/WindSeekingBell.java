package com.github.yimeng261.maidspell.item;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

/**
 * 寻风之铃 - 用于寻找最近的隐世之境结构
 * 功能模仿末影之眼，成功找到结构后播放铃声音效
 * 性能优化说明：
 * 1. 采用基于Main算法的简化螺旋搜索，避免OptimizedWhitePoleFinder的复杂数据结构开销
 * 2. 针对hiden_retreat极稀少特性（1万格以上），使用大步长(4)减少无效检测
 * 3. 直接按距离层遍历，每层检查边界点，找到第一个符合条件的立即返回
 * 4. 简化3×3樱花林验证逻辑：检查中心+四角+四边中点，通过后直接验证结构
 * 5. 减少Minecraft API调用次数，每个候选点最多9次生物群系检测+1次结构验证
 * 6. 使用BitSet避免重复检测，类似C++中的bitvec，以O(1)时间复杂度记录已检测区块
 * 7. BitSet内存开销低：对于10000半径，仅需约50MB内存存储检测状态
 * 8. 添加ConcurrentHashMap缓存机制：缓存搜索结果5分钟，基于玩家移动距离复用
 * 9. 缓存支持负结果：记录"无结构"区域，避免重复搜索空区域
 * 10. 智能缓存失效：玩家移动超过5000方块（约31区块）时缓存失效，匹配搜索范围
 * 11. 异步搜索机制：使用CompletableFuture在后台线程执行搜索，避免主线程卡顿
 * 12. 重复搜索保护：同一区域的并发搜索会共享结果，避免资源浪费
 * 13. 智能区域搜索：动态检测500方块范围内的进行中搜索，避免网格边界问题
 * 14. 多线程并行搜索：将方形搜索区域分割为多个子方形，并行搜索以提升性能
 * 15. 早期终止机制：任意线程找到结构后立即停止其他线程，避免资源浪费
 * 16. 自适应线程数：根据搜索范围和CPU核心数动态调整并行线程数
 * 17. 方形分割策略：保持原有方形搜索逻辑，按象限或网格分割搜索区域
 */
public class WindSeekingBell extends Item {
    
    // 搜索优化常量
    private static final int MAX_SEARCH_RADIUS = 64000; // 最大搜索半径（区块）
    private static final int SEARCH_STEP = 4; // 搜索步长，基于hiden_retreat稀少特性增大步长
    private static final int CHERRY_GROVE_CHECK_RADIUS = 2; // 樱花林3×3区域检查半径
    
    // 缓存机制
    private static final int CACHE_EXPIRY_TIME = 300000; // 缓存过期时间：5分钟
    private static final int CACHE_MOVE_THRESHOLD = 5000; // 缓存失效的移动阈值（方块）- 约31个区块
    private static final int SEARCH_REGION_SIZE = 1000; // 搜索区域大小（方块）- 用于生成searchKey
    
    // 多线程搜索配置
    private static final int MIN_THREADS = 2; // 最小线程数
    private static final int MAX_THREADS = 64; // 最大线程数，避免过多线程造成上下文切换开销
    
    // 小方格分割配置
    private static final int SECTOR_SIZE = 1000; // 每个小方格的大小（区块）：2000×2000
    
    // 多线程搜索专用线程池
    private static final ThreadPoolExecutor SEARCH_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(
        Math.min(MAX_THREADS, Math.max(MIN_THREADS, Runtime.getRuntime().availableProcessors())),
        r -> {
            Thread t = new Thread(r, "WindSeekingBell-Search-" + System.currentTimeMillis());
            t.setDaemon(true); // 设为守护线程，避免阻止JVM关闭
            t.setPriority(Thread.NORM_PRIORITY - 1); // 稍微降低优先级，避免影响主游戏逻辑
            return t;
        }
    );
    
    // 结构验证优化：提取静态常量避免重复创建
    private static final ResourceLocation HIDEN_RETREAT_LOCATION = 
        ResourceLocation.fromNamespaceAndPath("touhou_little_maid_spell", "hiden_retreat");
    private static final ResourceKey<Structure> HIDEN_RETREAT_KEY = 
        ResourceKey.create(Registries.STRUCTURE, HIDEN_RETREAT_LOCATION);
    
    // 结构集合缓存：延迟初始化以避免早期加载问题
    private static volatile HolderSet<Structure> cachedStructureSet = null;
    private static final Object STRUCTURE_SET_LOCK = new Object();
    
    // 结构检查全局同步锁：保护Minecraft结构检查系统的线程安全访问
    // 使用单一全局锁确保StructureCheck的完全线程安全
    // 虽然会降低并发性能，但能彻底解决ArrayIndexOutOfBoundsException
    private static final Object GLOBAL_STRUCTURE_CHECK_LOCK = new Object();
    
    // 常用消息组件缓存：避免重复创建相同的本地化组件
    private static final Component NO_STRUCTURE_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.no_structure"
    ).withStyle(ChatFormatting.RED);
    
    private static final Component CLICK_SUGGEST_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.click_to_suggest"
    ).withStyle(ChatFormatting.YELLOW);
    
    // 常用的区块偏移量（避免重复计算）
    private static final int CHUNK_CENTER_OFFSET = 8;
    private static final int DEFAULT_STRUCTURE_Y = 64;

    
    
    
    /**
     * 获取或初始化隐世之境结构集合（线程安全的延迟初始化）
     * @param level 服务器世界级别
     * @return 结构集合，如果注册表中不存在则返回null
     */
    private static HolderSet<Structure> getOrInitStructureSet(ServerLevel level) {
        // 双重检查锁定模式确保线程安全的延迟初始化
        if (cachedStructureSet == null) {
            synchronized (STRUCTURE_SET_LOCK) {
                if (cachedStructureSet == null) {
                    try {
                        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                        Holder<Structure> structureHolder = structureRegistry.getHolderOrThrow(HIDEN_RETREAT_KEY);
                        cachedStructureSet = HolderSet.direct(structureHolder);
                        Global.LOGGER.debug("Initialized structure set for hiden_retreat");
                    } catch (Exception e) {
                        Global.LOGGER.error("Failed to initialize hiden_retreat structure set", e);
                        return null;
                    }
                }
            }
        }
        return cachedStructureSet;
    }
    
    
    // 结构缓存：Key为"x,z"字符串，Value为CacheEntry
    private static final Map<String, CacheEntry> structureCache = new ConcurrentHashMap<>();
    
    // 正在进行的搜索：避免对同一区域的重复搜索
    private static final ConcurrentMap<String, CompletableFuture<BlockPos>> ongoingSearches = new ConcurrentHashMap<>();
    
    /**
     * 缓存条目类
     */
    private static class CacheEntry {
        final BlockPos searchCenter;  // 搜索中心位置
        final BlockPos structurePos;  // 结构位置，null表示该区域没有结构
        final long timestamp;         // 缓存时间戳
        final String dimensionKey;    // 维度标识符，确保不跨维度使用缓存
        
        CacheEntry(BlockPos searchCenter, BlockPos structurePos, long time, String dimensionKey) {
            this.searchCenter = searchCenter;
            this.structurePos = structurePos;
            this.timestamp = time;
            this.dimensionKey = dimensionKey;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
        }
        
        /**
         * 检查当前玩家位置是否可以使用此缓存
         * @param playerPos 当前玩家位置
         * @param currentDimensionKey 当前维度标识符
         * @return 是否可以使用缓存
         */
        boolean isValidForPosition(BlockPos playerPos, String currentDimensionKey) {
            // 检查维度是否匹配
            if (!this.dimensionKey.equals(currentDimensionKey)) {
                return false;
            }
            
            double moveDistance = Math.sqrt(playerPos.distSqr(searchCenter));
            return moveDistance <= CACHE_MOVE_THRESHOLD;
        }
    }
    
    /**
     * 缓存检查结果类
     */
    private static class CacheCheckResult {
        final boolean hasCache;
        final BlockPos structurePos;
        
        CacheCheckResult(boolean hasCache, BlockPos pos) {
            this.hasCache = hasCache;
            this.structurePos = pos;
        }
        
        static CacheCheckResult noCache() {
            return new CacheCheckResult(false, null);
        }
        
        static CacheCheckResult withResult(BlockPos pos) {
            return new CacheCheckResult(true, pos);
        }
    }
    
    public WindSeekingBell() {
        super(new Properties()
            .stacksTo(16)
            .rarity(Rarity.UNCOMMON)
        );
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        
        if (level instanceof ServerLevel serverLevel) {
            BlockPos playerPos = player.blockPosition();
            
            // 异步搜索结构，避免主线程卡顿
            findNearestHidenRetreatAsync(serverLevel, playerPos, player, itemStack);
            
            // 立即返回成功，实际结果将异步处理
            return InteractionResultHolder.success(itemStack);
        }
        
        return InteractionResultHolder.consume(itemStack);
    }
    
    /**
     * 异步搜索隐世之境结构
     */
    private void findNearestHidenRetreatAsync(ServerLevel serverLevel, BlockPos playerPos, Player player, ItemStack itemStack) {
        // 记录搜索开始时间
        long searchStartTime = System.currentTimeMillis();
        
        // 检查缓存
        CacheCheckResult cacheResult = checkCache(serverLevel, playerPos);
        if (cacheResult.hasCache) {
            // 缓存命中，直接处理结果
            long searchTime = System.currentTimeMillis() - searchStartTime;
            handleSearchResult(serverLevel, playerPos, player, itemStack, cacheResult.structurePos, searchTime);
            return;
        }
        
        // 检查是否已有相同区域的搜索在进行
        String searchKey = generateSearchKey(playerPos);
        CompletableFuture<BlockPos> existingSearch = ongoingSearches.get(searchKey);
        if (existingSearch != null) {
            // 等待现有搜索完成（共享搜索结果）
            existingSearch.thenAccept(result -> {
                long searchTime = System.currentTimeMillis() - searchStartTime;
                handleSearchResult(serverLevel, playerPos, player, itemStack, result, searchTime);
            });
            return;
        }
        
        // 启动新的异步搜索（支持多线程并行）
        CompletableFuture<BlockPos> searchFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return findNearestHidenRetreatParallel(serverLevel, playerPos);
            } catch (Exception e) {
                // 搜索异常，返回null
                Global.LOGGER.error("Structure search failed", e);
                return null;
            }
        }, SEARCH_EXECUTOR).whenComplete((result, throwable) -> {
            // 搜索完成，从正在进行的搜索中移除
            ongoingSearches.remove(searchKey);
        });
        
        // 将搜索加入正在进行的搜索
        ongoingSearches.put(searchKey, searchFuture);
        
        // 处理搜索结果
        searchFuture.thenAccept(result -> {
            long searchTime = System.currentTimeMillis() - searchStartTime;
            handleSearchResult(serverLevel, playerPos, player, itemStack, result, searchTime);
        });
    }
    
    /**
     * 处理搜索结果
     */
    private void handleSearchResult(ServerLevel serverLevel, BlockPos playerPos, Player player, ItemStack itemStack, 
                                  BlockPos structurePos, long searchTime) {
        // 确保在主线程中执行UI相关操作
        serverLevel.getServer().execute(() -> {
            if (structurePos != null) {
                // 找到结构，创建类似末影之眼的飞行实体
                EyeOfEnder eyeOfEnder = new EyeOfEnder(serverLevel, player.getX(), player.getY(0.5), player.getZ());
                eyeOfEnder.setItem(itemStack);
                eyeOfEnder.signalTo(structurePos);
                serverLevel.addFreshEntity(eyeOfEnder);
                

                BlockPos playerBlockPos = player.blockPosition();

                serverLevel.playSound(
                    null, 
                    playerBlockPos.getX(), 
                    playerBlockPos.getY(), 
                    playerBlockPos.getZ(), 
                    MaidSpellSounds.WIND_SEEKING_BELL.get(), 
                    SoundSource.PLAYERS, 
                    1.0F, 
                    1.2F
                );
                
                // 在聊天栏显示可点击的坐标信息
                int distance = (int) Math.sqrt(structurePos.distSqr(playerPos));
                
                // 创建tp指令（使用相对y坐标）
                String tpCommand = String.format("/tp %d ~ %d", 
                    structurePos.getX(), 
                    structurePos.getZ());
                
                // 创建可点击的坐标消息
                Component coordinateMessage = Component.translatable(
                    "item.touhou_little_maid_spell.wind_seeking_bell.found_structure",
                    structurePos.getX(),
                    structurePos.getY(), 
                    structurePos.getZ(),
                    distance
                ).withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, CLICK_SUGGEST_MESSAGE))
                );
                
                // 创建搜索耗时信息（添加缓存标识和多线程标识）
                Component timingMessage = Component.translatable(
                        "item.touhou_little_maid_spell.wind_seeking_bell.search_time",
                        searchTime
                    ).withStyle(ChatFormatting.AQUA);
                
                player.sendSystemMessage(coordinateMessage);
                player.sendSystemMessage(timingMessage);
                
                // 统计使用次数
                player.awardStat(Stats.ITEM_USED.get(this));
                
                // 消耗物品（如果不是创造模式）
                if (!player.getAbilities().instabuild) {
                    itemStack.shrink(1);
                }
            } else {
                Component timingMessage = Component.translatable(
                        "item.touhou_little_maid_spell.wind_seeking_bell.search_time",
                        searchTime
                    ).withStyle(ChatFormatting.GREEN);
                
                player.displayClientMessage(NO_STRUCTURE_MESSAGE, true);
                player.sendSystemMessage(timingMessage);
            }
        });
    }
    
    /**
     * 生成区域性搜索键
     * 将相近位置的搜索归为同一区域，避免重复搜索
     * 使用更智能的区域判定算法
     * @param pos 玩家位置
     * @return 区域性搜索键
     */
    private String generateSearchKey(BlockPos pos) {
        // 检查是否已有进行中的搜索可以复用
        for (Map.Entry<String, CompletableFuture<BlockPos>> entry : ongoingSearches.entrySet()) {
            String[] coords = entry.getKey().split(",");
            try {
                int existingX = Integer.parseInt(coords[0]);
                int existingZ = Integer.parseInt(coords[1]);
                BlockPos existingCenter = new BlockPos(existingX, 0, existingZ);
                
                // 如果距离现有搜索中心在区域范围内，使用现有的key
                double distance = Math.sqrt(pos.distSqr(existingCenter));
                if (distance <= SEARCH_REGION_SIZE) {
                    return entry.getKey();
                }
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        
        // 如果没有可复用的搜索，创建新的区域键
        // 使用当前位置作为新区域的中心
        return pos.getX() + "," + pos.getZ();
    }
    
    /**
     * 多线程并行的隐世之境结构搜索方法
     * 根据搜索范围和系统资源自适应选择单线程或多线程搜索策略
     * 大范围搜索时将区域分割为扇形，并行搜索以提升性能
     */
    private BlockPos findNearestHidenRetreatParallel(ServerLevel level, BlockPos playerPos) {
        // 1. 首先检查缓存
        CacheCheckResult cacheResult = checkCache(level, playerPos);
        if (cacheResult.hasCache) {
            // 缓存命中，直接返回结果（极快速度）
            return cacheResult.structurePos; // 可能是null，表示该区域没有结构
        }
        
        ChunkPos playerChunk = new ChunkPos(playerPos);
        
        // 2. 根据搜索范围和系统资源选择搜索策略
        BlockPos result;
        result = parallelSquareSearch(level, playerChunk);
        // 3. 将结果加入缓存
        updateCache(level, playerPos, result);
        
        return result;
    }
    
    
    /**
     * 多线程并行小方格螺旋搜索
     * 将64000×64000的搜索范围按2000×2000小方格分割，按螺旋顺序逐层检查
     * 每个小方格内使用独立线程进行完整的区域搜索，充分利用BitSet状态
     */
    private BlockPos parallelSquareSearch(ServerLevel level, ChunkPos centerChunk) {
        int availableThreads = Math.min(MAX_THREADS, Math.max(MIN_THREADS, Runtime.getRuntime().availableProcessors()));

        Global.LOGGER.debug("Starting parallel search with {} threads, radius: {}", 
                           availableThreads, WindSeekingBell.MAX_SEARCH_RADIUS);
        
        // 计算需要搜索的小方格层数（每个小方格2000×2000）
        int maxSectorLayer = (WindSeekingBell.MAX_SEARCH_RADIUS + SECTOR_SIZE - 1) / SECTOR_SIZE; // 向上取整
        
        
        // 按小方格层级螺旋搜索，确保距离优先
        for (int sectorLayer = 0; sectorLayer <= maxSectorLayer; sectorLayer++) {
            
            // 在当前小方格层内使用多线程并行搜索
            BlockPos result = searchSectorLayerParallel(level, centerChunk, sectorLayer, availableThreads);
            if (result != null) {
                return result; // 找到最近的结构，立即返回
            }
        }
        
        return null; // 未找到结构
    }
    
    /**
     * 多线程并行搜索指定小方格层
     * 将当前层的所有小方格按螺旋顺序分配给不同线程并行搜索
     *
     * @param level            世界
     * @param centerChunk      搜索中心
     * @param sectorLayer      小方格层级（0=中心小方格，1=周围8个小方格等）
     * @param availableThreads 可用线程数
     * @return 找到的结构位置，如果没有找到则返回null
     */
    private BlockPos searchSectorLayerParallel(ServerLevel level, ChunkPos centerChunk, int sectorLayer,
                                               int availableThreads) {
        if (sectorLayer == 0) {
            // 中心小方格（单线程处理）
            return searchSectorComplete(level, centerChunk, 0, 0, 0);
        }
        
        
        // 生成当前层所有小方格的坐标（按螺旋顺序）
        List<int[]> sectorCoords = generateSectorCoordinates(sectorLayer);
        
        // 创建并行任务搜索当前层的所有小方格
        List<CompletableFuture<BlockPos>> sectorTasks = new ArrayList<>();
        AtomicReference<BlockPos> foundInLayer = new AtomicReference<>(null);
        AtomicBoolean layerComplete = new AtomicBoolean(false);
        
        for (int i = 0; i < sectorCoords.size(); i++) {
            final int[] coords = sectorCoords.get(i);
            final int sectorIndex = i;
            final int sectorX = coords[0];
            final int sectorZ = coords[1];
            
            CompletableFuture<BlockPos> sectorTask = CompletableFuture.supplyAsync(() -> searchSectorWithTermination(level, centerChunk, sectorX, sectorZ,
                    layerComplete, sectorIndex), SEARCH_EXECUTOR);
            
            sectorTasks.add(sectorTask);
        }
        
        try {
            // 创建一个监控任务，检查是否找到结构
            CompletableFuture<BlockPos> monitorTask = CompletableFuture.supplyAsync(() -> {
                try {
                    while (foundInLayer.get() == null && !areAllTasksCompleted(sectorTasks)) {
                        Thread.sleep(50); // 每50ms检查一次
                        
                        // 检查是否有任务找到了结构
                        for (CompletableFuture<BlockPos> task : sectorTasks) {
                            if (task.isDone() && !task.isCancelled()) {
                                try {
                                    BlockPos result = task.get();
                                    if (result != null && foundInLayer.compareAndSet(null, result)) {
                                        layerComplete.set(true);
                                        return result;
                                    }
                                } catch (Exception e) {
                                    // 忽略单个任务的异常
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return foundInLayer.get();
            });
            
            // 等待监控任务完成或超时
            BlockPos result = monitorTask.get(120, TimeUnit.SECONDS);
            
            // 如果找到了结构，取消未完成的任务
            if (result != null) {
                sectorTasks.forEach(task -> task.cancel(true));
            } else {
                // 等待所有任务自然完成
                CompletableFuture.allOf(sectorTasks.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            }
            
            return result;
            
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            sectorTasks.forEach(task -> task.cancel(true));
            return null;
        }
    }
    
    
    /**
     * 检查所有任务是否已完成
     */
    private boolean areAllTasksCompleted(List<CompletableFuture<BlockPos>> tasks) {
        return tasks.stream().allMatch(CompletableFuture::isDone);
    }
    
    /**
     * 生成指定层所有小方格的坐标（按螺旋顺序）
     */
    private List<int[]> generateSectorCoordinates(int layer) {
        List<int[]> coords = new ArrayList<>();
        
        if (layer == 0) {
            coords.add(new int[]{0, 0});
            return coords;
        }
        
        // 按螺旋顺序生成小方格坐标：顶边→右边→底边→左边
        // 顶边：z = layer, x 从 -layer 到 layer
        for (int x = -layer; x <= layer; x++) {
            coords.add(new int[]{x, layer});
        }
        
        // 右边：x = layer, z 从 layer-1 到 -layer
        for (int z = layer - 1; z >= -layer; z--) {
            coords.add(new int[]{layer, z});
        }
        
        // 底边：z = -layer, x 从 layer-1 到 -layer
        for (int x = layer - 1; x >= -layer; x--) {
            coords.add(new int[]{x, -layer});
        }
        
        // 左边：x = -layer, z 从 -layer+1 到 layer-1
        for (int z = -layer + 1; z <= layer - 1; z++) {
            coords.add(new int[]{-layer, z});
        }
        
        return coords;
    }
    
    /**
     * 搜索单个小方格（带早期终止检查）
     */
    private BlockPos searchSectorWithTermination(ServerLevel level, ChunkPos centerChunk, int sectorX, int sectorZ,
                                                 AtomicBoolean layerComplete, int sectorIndex) {
        // 提前检查是否需要终止
        if (layerComplete.get()) {
            return null;
        }
        
        return searchSectorComplete(level, centerChunk, sectorX, sectorZ, sectorIndex);
    }
    
    /**
     * 完整搜索单个2000×2000小方格区域
     * 使用独立的BitSet记录该小方格内的检测状态，充分利用缓存局部性
     */
    private BlockPos searchSectorComplete(ServerLevel level, ChunkPos centerChunk, int sectorX, int sectorZ,
                                          int sectorIndex) {
        // 计算小方格的实际区块坐标范围
        int sectorStartX = centerChunk.x + sectorX * SECTOR_SIZE - SECTOR_SIZE / 2;
        int sectorEndX = sectorStartX + SECTOR_SIZE - 1;
        int sectorStartZ = centerChunk.z + sectorZ * SECTOR_SIZE - SECTOR_SIZE / 2;
        int sectorEndZ = sectorStartZ + SECTOR_SIZE - 1;
        
        // 限制搜索范围不超过maxRadius
        sectorStartX = Math.max(sectorStartX, centerChunk.x - WindSeekingBell.MAX_SEARCH_RADIUS);
        sectorEndX = Math.min(sectorEndX, centerChunk.x + WindSeekingBell.MAX_SEARCH_RADIUS);
        sectorStartZ = Math.max(sectorStartZ, centerChunk.z - WindSeekingBell.MAX_SEARCH_RADIUS);
        sectorEndZ = Math.min(sectorEndZ, centerChunk.z + WindSeekingBell.MAX_SEARCH_RADIUS);
        
        
        // 为当前小方格创建独立的BitSet（充分利用局部性）
        int sectorWidth = sectorEndX - sectorStartX + 1;
        int sectorHeight = sectorEndZ - sectorStartZ + 1;
        BitSet sectorChecked = new BitSet(sectorWidth * sectorHeight);
        
        // 在小方格内进行完整的螺旋搜索
        ChunkPos sectorCenter = new ChunkPos((sectorStartX + sectorEndX) / 2, (sectorStartZ + sectorEndZ) / 2);
        int sectorRadius = Math.max(sectorWidth, sectorHeight) / 2;
        
        // 按距离层级搜索小方格内部
        for (int layer = 0; layer <= sectorRadius; layer += SEARCH_STEP) {
            BlockPos result = searchSectorLayer(level, sectorCenter, layer, sectorStartX, sectorEndX, 
                                              sectorStartZ, sectorEndZ, sectorChecked);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * 搜索小方格内的指定层
     */
    private BlockPos searchSectorLayer(ServerLevel level, ChunkPos sectorCenter, int layer,
                                     int minX, int maxX, int minZ, int maxZ,
                                     BitSet sectorChecked) {
        if (layer == 0) {
            // 检查小方格中心点
            if (isInSectorBounds(sectorCenter, minX, maxX, minZ, maxZ)) {
                if (!isSectorChunkChecked(sectorCenter, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(sectorCenter, minX, minZ, sectorChecked, maxX - minX + 1);
                    return checkPotentialCenter(level, sectorCenter);
                }
            }
            return null;
        }
        
        // 搜索层边界（与标准螺旋搜索相同，但限制在小方格范围内）
        // 顶边
        for (int x = -layer; x <= layer; x += SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x + x, sectorCenter.z + layer);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (!isSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }
        
        // 右边、底边、左边（类似实现）
        for (int z = layer - SEARCH_STEP; z >= -layer; z -= SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x + layer, sectorCenter.z + z);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (!isSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }
        
        for (int x = layer - SEARCH_STEP; x >= -layer; x -= SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x + x, sectorCenter.z - layer);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (!isSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }
        
        for (int z = -layer + SEARCH_STEP; z <= layer - SEARCH_STEP; z += SEARCH_STEP) {
            ChunkPos candidate = new ChunkPos(sectorCenter.x - layer, sectorCenter.z + z);
            if (isInSectorBounds(candidate, minX, maxX, minZ, maxZ)) {
                if (!isSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1)) {
                    setSectorChunkChecked(candidate, minX, minZ, sectorChecked, maxX - minX + 1);
                BlockPos result = checkPotentialCenter(level, candidate);
                if (result != null) return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 检查坐标是否在小方格范围内
     */
    private boolean isInSectorBounds(ChunkPos pos, int minX, int maxX, int minZ, int maxZ) {
        return pos.x >= minX && pos.x <= maxX && pos.z >= minZ && pos.z <= maxZ;
    }
    
    /**
     * 小方格专用BitSet操作：检查区块是否已被检测
     */
    private boolean isSectorChunkChecked(ChunkPos chunk, int sectorMinX, int sectorMinZ, BitSet sectorChecked, int sectorWidth) {
        int x = chunk.x - sectorMinX;
        int z = chunk.z - sectorMinZ;
        if (x < 0 || z < 0 || x >= sectorWidth) return true; // 超出范围视为已检测
        
        int index = z * sectorWidth + x;
        return index >= 0 && index < sectorChecked.size() && sectorChecked.get(index);
    }
    
    /**
     * 小方格专用BitSet操作：标记区块为已检测
     */
    private void setSectorChunkChecked(ChunkPos chunk, int sectorMinX, int sectorMinZ, BitSet sectorChecked, int sectorWidth) {
        int x = chunk.x - sectorMinX;
        int z = chunk.z - sectorMinZ;
        if (x < 0 || z < 0 || x >= sectorWidth) return; // 超出范围不记录
        
        int index = z * sectorWidth + x;
        if (index >= 0 && index < sectorChecked.size()) {
            sectorChecked.set(index);
        }
    }
    
    /**
     * 检查缓存中是否有该位置的搜索结果
     * @param serverLevel 服务器世界级别
     * @param playerPos 玩家位置
     * @return 缓存检查结果
     */
    private CacheCheckResult checkCache(ServerLevel serverLevel, BlockPos playerPos) {
        // 清理过期缓存
        cleanExpiredCache();
        
        // 获取当前维度标识符
        String currentDimensionKey = serverLevel.dimension().location().toString();
        
        // 检查是否有可用的缓存结果
        for (CacheEntry cache : structureCache.values()) {
            if (cache.isExpired()) continue;
            
            // 检查玩家相对于原搜索位置的移动距离和维度匹配
            if (cache.isValidForPosition(playerPos, currentDimensionKey)) {
                return CacheCheckResult.withResult(cache.structurePos); // 可能是null，表示该区域没有结构
            }
        }
        
        return CacheCheckResult.noCache(); // 没有找到相关缓存，需要进行搜索
    }
    
    /**
     * 更新缓存
     * @param serverLevel 服务器世界级别
     * @param searchCenter 搜索中心位置
     * @param result 搜索结果（可能为null）
     */
    private void updateCache(ServerLevel serverLevel, BlockPos searchCenter, BlockPos result) {
        String key = generateSearchKey(searchCenter);
        String dimensionKey = serverLevel.dimension().location().toString();
        CacheEntry entry = new CacheEntry(searchCenter, result, System.currentTimeMillis(), dimensionKey);
        structureCache.put(key, entry);
    }
    
    /**
     * 清理过期缓存
     */
    private void cleanExpiredCache() {
        structureCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 检查潜在的结构中心点
     * 基于Main算法思路：直接验证是否为有效的hiden_retreat中心
     */
    private BlockPos checkPotentialCenter(ServerLevel level, ChunkPos centerChunk) {
        // 1. 首先检查中心是否为樱花林（类似Main算法检查中心格子）
        if (!isCherryGroveChunk(level, centerChunk)) {
            return null;
        }
        
        // 2. 检查3×3区域的四个角落（类似Main算法检查四个角）
        ChunkPos[] corners = {
            new ChunkPos(centerChunk.x - CHERRY_GROVE_CHECK_RADIUS, centerChunk.z - CHERRY_GROVE_CHECK_RADIUS), // 左上
            new ChunkPos(centerChunk.x - CHERRY_GROVE_CHECK_RADIUS, centerChunk.z + CHERRY_GROVE_CHECK_RADIUS), // 左下  
            new ChunkPos(centerChunk.x + CHERRY_GROVE_CHECK_RADIUS, centerChunk.z - CHERRY_GROVE_CHECK_RADIUS), // 右上
            new ChunkPos(centerChunk.x + CHERRY_GROVE_CHECK_RADIUS, centerChunk.z + CHERRY_GROVE_CHECK_RADIUS)  // 右下
        };
        
        for (ChunkPos corner : corners) {
            if (!isCherryGroveChunk(level, corner)) {
                return null; // 角落不是樱花林，不符合条件
            }
        }
        
        // 3. 检查3×3区域的边中点（类似Main算法检查边中点）
        ChunkPos[] edges = {
            new ChunkPos(centerChunk.x - CHERRY_GROVE_CHECK_RADIUS, centerChunk.z), // 左边
            new ChunkPos(centerChunk.x + CHERRY_GROVE_CHECK_RADIUS, centerChunk.z), // 右边
            new ChunkPos(centerChunk.x, centerChunk.z - CHERRY_GROVE_CHECK_RADIUS), // 上边
            new ChunkPos(centerChunk.x, centerChunk.z + CHERRY_GROVE_CHECK_RADIUS)  // 下边
        };
        
        for (ChunkPos edge : edges) {
            if (!isCherryGroveChunk(level, edge)) {
                return null; // 边中点不是樱花林，不符合条件
            }
        }
        
        // 4. 所有樱花林检查通过，进行结构验证（类似Main算法的白极点检测）
        return verifyStructureExists(level, centerChunk);
    }
    
    
    
    /**
     * 快速检查区块是否为樱花林生物群系
     */
    private boolean isCherryGroveChunk(ServerLevel level, ChunkPos chunk) {
        try {
            // 只检查区块中心点的生物群系（使用预定义常量）
            int x = chunk.getMinBlockX() + CHUNK_CENTER_OFFSET;
            int z = chunk.getMinBlockZ() + CHUNK_CENTER_OFFSET;
            int y = level.getSeaLevel();
            
            Holder<Biome> biome = level.getBiome(new BlockPos(x, y, z));
            return biome.is(Biomes.CHERRY_GROVE);
        } catch (Exception e) {
            return false;
        }
    }
    

    /**
     * 验证结构是否真的存在于该位置
     * 优化版本：使用缓存的静态变量避免重复对象创建
     * 线程安全版本：同步访问Minecraft的结构检查系统
     */
    private BlockPos verifyStructureExists(ServerLevel level, ChunkPos chunk) {
        try {
            // 使用缓存的结构集合（线程安全的延迟初始化）
            HolderSet<Structure> structureSet = getOrInitStructureSet(level);
            if (structureSet == null) {
                return null; // 结构不存在或初始化失败
            }
            
            // 计算区块中心位置（使用预定义常量）
            BlockPos chunkCenter = new BlockPos(
                chunk.getMinBlockX() + CHUNK_CENTER_OFFSET, 
                DEFAULT_STRUCTURE_Y, 
                chunk.getMinBlockZ() + CHUNK_CENTER_OFFSET
            );
            
            // 使用全局锁保护Minecraft结构检查系统的访问
            // 因为StructureCheck内部使用的Long2BooleanOpenHashMap不是线程安全的
            // 虽然会降低并发性能，但能彻底解决ArrayIndexOutOfBoundsException
            synchronized (GLOBAL_STRUCTURE_CHECK_LOCK) {
            var result = level.getChunkSource().getGenerator().findNearestMapStructure(
                level, 
                structureSet,
                chunkCenter, 
                1, // 只检查当前区块
                false
            );
            
            return result != null ? result.getFirst() : null;
            }
        } catch (Exception e) {
            // 如果出现异常，清除缓存以便下次重新初始化
            synchronized (STRUCTURE_SET_LOCK) {
                cachedStructureSet = null;
            }
            Global.LOGGER.debug("Structure verification failed, cache cleared", e);
            return null;
        }
    }
    

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc1")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc2")
            .withStyle(ChatFormatting.BLUE));
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }
    
    /**
     * 服务器事件监听器：处理缓存清理
     */
    @Mod.EventBusSubscriber(modid = "touhou_little_maid_spell")
    public static class ServerEventHandler {
        
        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            // 服务器启动时清空缓存，确保不会跨存档使用旧缓存
            clearAllCaches();
            Global.LOGGER.info("WindSeekingBell: Cleared caches on server startup");
        }
        
        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            // 服务器停止时清空缓存，释放内存
            clearAllCaches();
            Global.LOGGER.info("WindSeekingBell: Cleared caches on server shutdown");
        }
    }
    
    /**
     * 清空所有缓存数据
     */
    public static void clearAllCaches() {
        // 清空结构搜索缓存
        structureCache.clear();
        
        // 清空正在进行的搜索
        ongoingSearches.clear();
        
        // 清空结构集合缓存
        synchronized (STRUCTURE_SET_LOCK) {
            cachedStructureSet = null;
        }
        
        Global.LOGGER.debug("WindSeekingBell: All caches cleared");
    }
}

