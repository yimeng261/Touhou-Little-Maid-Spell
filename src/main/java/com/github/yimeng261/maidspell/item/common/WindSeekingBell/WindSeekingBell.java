package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.entity.WindSeekingBellEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 寻风之铃 - 用于寻找最近的隐世之境结构
 * 功能模仿末影之眼，成功找到结构后播放铃声音效
 * 性能优化说明：
 * 1. 采用基于Main算法的简化螺旋搜索，避免OptimizedWhitePoleFinder的复杂数据结构开销
 * 2. 针对hidden_retreat极稀少特性（1万格以上），使用大步长(4)减少无效检测
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
 * 18. 分段锁优化：使用基于ServerLevel的64段锁，同一世界串行，不同世界并发，保证线程安全
 * 19. 模块化设计：配置、缓存、验证逻辑分离，代码从952行优化到约750行，可维护性大幅提升
 */
public class WindSeekingBell extends Item {
    
    // 注意：所有搜索配置已迁移到 SearchConfig 类
    
    // 常用消息组件缓存：避免重复创建相同的本地化组件
    private static final Component NO_STRUCTURE_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.no_structure"
    ).withStyle(ChatFormatting.RED);
    
    private static final Component CLICK_SUGGEST_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.click_to_suggest"
    ).withStyle(ChatFormatting.YELLOW);
    
    
    // 搜索缓存管理器：管理所有缓存和正在进行的搜索
    private static final SearchCacheManager cacheManager = new SearchCacheManager();
    
    // 生物群系验证器：检查樱花林生物群系
    private static final BiomeValidator biomeValidator = new BiomeValidator();
    
    // 结构搜索引擎：负责执行并行搜索
    private static final StructureSearchEngine searchEngine = new StructureSearchEngine(biomeValidator, cacheManager);
    
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
            findNearestHiddenRetreatAsync(serverLevel, playerPos, player, itemStack);
            
            // 立即返回成功，实际结果将异步处理
            return InteractionResultHolder.success(itemStack);
        }
        
        return InteractionResultHolder.consume(itemStack);
    }
    
    /**
     * 异步搜索隐世之境结构
     */
    private void findNearestHiddenRetreatAsync(ServerLevel serverLevel, BlockPos playerPos, Player player, ItemStack itemStack) {
        // 记录搜索开始时间
        long searchStartTime = System.currentTimeMillis();
        
        // 使用搜索引擎执行异步搜索
        searchEngine.searchAsync(serverLevel, playerPos).thenAccept(result -> {
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
                // 找到结构，创建自定义的寻风之铃飞行实体
                int height = serverLevel.getChunkSource().getGenerator().getFirstOccupiedHeight(
                    structurePos.getX(), structurePos.getZ(), 
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 
                    serverLevel.getChunk(structurePos).getHeightAccessorForGeneration(), 
                    serverLevel.getChunkSource().randomState()
                );
                BlockPos structurePosVec3i = new BlockPos(structurePos.getX(), 0, structurePos.getZ());
                BlockPos playerPosVec3i = new BlockPos(playerPos.getX(), 0, playerPos.getZ());
                WindSeekingBellEntity windSeekingBell = new WindSeekingBellEntity(serverLevel, player);
                ItemStack newItemStack = itemStack.copy();
                newItemStack.setCount(1);
                windSeekingBell.setItem(newItemStack);
                windSeekingBell.signalTo(structurePosVec3i.above(height+3));
                serverLevel.addFreshEntity(windSeekingBell);


                // 在聊天栏显示可点击的坐标信息

                int distance = (int) Math.sqrt(structurePosVec3i.distSqr(playerPosVec3i));
                
                // 创建tp指令（使用相对y坐标）
                String tpCommand = String.format("/tp %f %d %f",
                    structurePos.getX() + 0.5,
                    height+3,
                    structurePos.getZ() + 0.5);
                
                // 创建可点击的坐标消息
                Component coordinateMessage = Component.translatable(
                    "item.touhou_little_maid_spell.wind_seeking_bell.found_structure",
                    structurePos.getX(),
                    height,
                    structurePos.getZ(),
                    distance
                ).withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, CLICK_SUGGEST_MESSAGE))
                );

                Component timingMessage = Component.translatable(
                        "item.touhou_little_maid_spell.wind_seeking_bell.search_time",
                        searchTime
                    ).withStyle(ChatFormatting.AQUA);
                
                player.sendSystemMessage(coordinateMessage);
                player.sendSystemMessage(timingMessage);

                if(player instanceof ServerPlayer serverPlayer){
                    int count =serverPlayer.getStats().getValue(Stats.ITEM_USED.get(this));
                    if(count == 0){
                        serverPlayer.sendSystemMessage(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.first_use").withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                }

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
    
    

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc1")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc2")
            .withStyle(ChatFormatting.GOLD));
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
            Global.LOGGER.info("WindSeekingBell: Using striped lock with {} segments for improved concurrency", 
                SearchConfig.LOCK_STRIPE_COUNT);
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
        // 清空搜索缓存管理器
        cacheManager.clearAll();
        
        // 清空搜索引擎的结构集合缓存
        StructureSearchEngine.clearCaches();
        
        Global.LOGGER.debug("WindSeekingBell: All caches cleared");
    }
}

