package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 寻风之铃 - 用于前往归隐之地维度并寻找隐世之境
 * 新功能：
 * 1. 在主世界使用：传送到玩家专属的归隐之地维度
 * 2. 在归隐之地使用：寻找隐世之境结构位置（每个维度只有一个）
 * 3. Shift+右键：从归隐之地返回主世界
 * 优化策略（简化版）：
 * 1. 无需樱花林验证：隐世之境维度中所有区块都是固定的樱花林生物群系
 * 2. 维度级缓存：每个玩家的维度只有一个结构，按维度缓存结果，不受玩家位置影响
 * 3. 并行螺旋搜索：采用分块并行螺旋搜索算法，快速定位结构位置
 * 4. 重复搜索保护：同一维度的并发搜索会共享结果，避免资源浪费
 * 5. 异步搜索机制：使用CompletableFuture在后台线程执行搜索，避免主线程卡顿
 * 6. 分段锁优化：使用基于ServerLevel的64段锁，保证线程安全同时提升并发性能
 */
public class WindSeekingBell extends Item {

    // 常用消息组件缓存：避免重复创建相同的本地化组件
    private static final Component NO_STRUCTURE_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.no_structure"
    ).withStyle(ChatFormatting.RED);

    private static final Component CLICK_SUGGEST_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.click_to_suggest"
    ).withStyle(ChatFormatting.YELLOW);


    // 搜索缓存管理器：管理所有缓存和正在进行的搜索
    private static final SearchCacheManager cacheManager = new SearchCacheManager();

    // 结构搜索引擎：负责执行并行搜索
    private static final StructureSearchEngine searchEngine = new StructureSearchEngine(cacheManager);

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

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            BlockPos playerPos = player.blockPosition();

            // 检查是否按住Shift键（返回主世界）
            if (player.isShiftKeyDown() && TheRetreatDimension.isInRetreat(player)) {
                // 从归隐之地返回主世界
                TheRetreatDimension.teleportFromRetreat(serverPlayer, playerPos);

                player.displayClientMessage(
                    Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.return_to_overworld")
                        .withStyle(ChatFormatting.GREEN),
                    true
                );

                // 不消耗物品
                return InteractionResultHolder.success(itemStack);
            }

            // 检查玩家所在维度
            if (TheRetreatDimension.isInRetreat(player)) {
                findNearestHiddenRetreatAsync(serverLevel, playerPos, player, itemStack);
            } else {
                teleportToRetreat(serverPlayer, playerPos, itemStack);
            }

            // 立即返回成功，实际结果将异步处理
            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.consume(itemStack);
    }

    /**
     * 传送玩家到归隐之地维度
     */
    private void teleportToRetreat(ServerPlayer player, BlockPos playerPos, ItemStack itemStack) {
        // 获取或创建玩家专属的归隐之地
        ServerLevel retreatLevel = PlayerRetreatManager.getOrCreatePlayerRetreat(
            player.getServer(),
            player.getUUID()
        );

        if (retreatLevel == null) {
            player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.teleport_failed")
                    .withStyle(ChatFormatting.RED),
                true
            );
            Global.LOGGER.error("Failed to create or get retreat dimension for player: " + player.getName().getString());
            return;
        }

        // 传送到归隐之地（使用相同坐标）
        TheRetreatDimension.teleportToRetreat(player, playerPos);

        player.displayClientMessage(
            Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.entered_retreat")
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        );

        // 消耗物品（如果不是创造模式）
        if (!player.getAbilities().instabuild) {
            itemStack.shrink(1);
        }

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
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc1")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc2")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc3")
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.desc4")
            .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    /**
     * 服务器事件监听器：处理缓存清理
     */
    @EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
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

