package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatDimensionData;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.dimension.StructureSearchQueue;
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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 寻风之铃 - 用于前往归隐之地维度并寻找隐世之境
 * 1. 主世界右键：传送到归隐之地
 * 2. 归隐之地右键：搜索隐世之境结构
 * 3. Shift+右键：从归隐之地返回主世界
 */
public class WindSeekingBell extends Item {

    private static final Component NO_STRUCTURE_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.no_structure"
    ).withStyle(ChatFormatting.RED);

    private static final Component CLICK_SUGGEST_MESSAGE = Component.translatable(
        "item.touhou_little_maid_spell.wind_seeking_bell.click_to_suggest"
    ).withStyle(ChatFormatting.YELLOW);

    public WindSeekingBell() {
        super(new Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        player.startUsingItem(hand);

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            BlockPos playerPos = player.blockPosition();

            // Shift+右键：从归隐之地返回主世界
            if (player.isShiftKeyDown() && TheRetreatDimension.isInRetreat(player)) {
                TheRetreatDimension.teleportFromRetreat(serverPlayer);
                player.displayClientMessage(
                    Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.return_to_overworld")
                        .withStyle(ChatFormatting.GREEN), true);
                return InteractionResultHolder.success(itemStack);
            }

            if (TheRetreatDimension.isInRetreat(player)) {
                // 在归隐之地：搜索隐世之境
                findHiddenRetreatWithQueue(serverLevel, playerPos, player, itemStack);
            } else {
                // 在主世界：传送到归隐之地
                teleportToRetreat(serverPlayer, itemStack);
            }

            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    private void teleportToRetreat(ServerPlayer player, ItemStack itemStack) {
        if (Config.enablePrivateDimensions) {
            teleportToPrivateRetreat(player);
        } else {
            teleportToSharedRetreat(player);
        }
    }

    private void teleportToPrivateRetreat(ServerPlayer player) {
        ServerLevel retreatLevel = PlayerRetreatManager.getOrCreatePlayerRetreat(
            player.getServer(), player.getUUID());

        if (retreatLevel == null) {
            player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.teleport_failed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        TheRetreatDimension.teleportToRetreat(player);
        player.displayClientMessage(
            Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.entered_retreat")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private void teleportToSharedRetreat(ServerPlayer player) {
        ServerLevel sharedRetreat = PlayerRetreatManager.getOrCreateSharedRetreat(player.getServer());
        if (sharedRetreat == null) {
            player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.teleport_failed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        RetreatDimensionData data = RetreatDimensionData.get(player.getServer());
        RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(player.getUUID());

        // 修复：只有 info == null 才是首次使用，quota == 0 是配额已用完
        if (info == null) {
            data.registerDimension(player.getUUID());
            info = data.getDimensionInfo(player.getUUID());
            info.structureQuota = 1;
            data.updateAccessTime(player.getUUID());

            player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.first_entry_shared")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }

        TheRetreatDimension.teleportToRetreat(player);
        player.displayClientMessage(
            Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.entered_retreat")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /**
     * 使用队列机制搜索隐世之境
     * 物品在搜索成功后才消耗（修复原先提前消耗的问题）
     */
    private void findHiddenRetreatWithQueue(ServerLevel serverLevel, BlockPos playerPos, Player player, ItemStack itemStack) {
        long worldSeed = serverLevel.getSeed();
        long searchStartTime = System.currentTimeMillis();

        // 1. 检查玩家缓存（私人/共享模式均适用，每个玩家独立缓存自己的隐世之境位置）
        RetreatManager.CacheResult cacheResult = RetreatManager.checkCache(player.getUUID());
        if (cacheResult.hasCache && !cacheResult.isNegative) {
            consumeItem(player, itemStack);
            handleSearchResult(serverLevel, playerPos, player, cacheResult.position,
                System.currentTimeMillis() - searchStartTime);
            return;
        }

        // 2. 优先检查未分配结构池（共享模式专属优化）
        if (!Config.enablePrivateDimensions && RetreatManager.hasUnassignedStructures(worldSeed)) {
            BlockPos unassignedPos = RetreatManager.pollUnassignedStructure(worldSeed);
            if (unassignedPos != null) {
                // 立即分配给当前玩家
                RetreatManager.updateCache(player.getUUID(), unassignedPos);
                consumeItem(player, itemStack);
                handleSearchResult(serverLevel, playerPos, player, unassignedPos,
                    System.currentTimeMillis() - searchStartTime);
                return;
            }
        }

        // 3. 加入队列获取 Future
        CompletableFuture<BlockPos> resultFuture = StructureSearchQueue.addSearchRequest(
            worldSeed, player.getUUID(), playerPos);

        int queuePosition = StructureSearchQueue.getPlayerPosition(worldSeed, player.getUUID());
        int totalWaiting = StructureSearchQueue.getWaitingCount(worldSeed);

        if (queuePosition > 0) {
            player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.waiting_in_queue",
                    queuePosition, totalWaiting).withStyle(ChatFormatting.YELLOW), true);
        } else {
            player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.waiting_for_structure")
                    .withStyle(ChatFormatting.YELLOW), true);
        }

        // 4. 触发搜索（单线程异步 findNearestMapStructure）
        RetreatManager.triggerStructureSearch(serverLevel, playerPos);

        // 5. 等待回调结果 — 成功后才消耗物品
        resultFuture.thenAccept(result -> {
            long searchTime = System.currentTimeMillis() - searchStartTime;
            serverLevel.getServer().execute(() -> {
                if (result != null) {
                    // 搜索成功：消耗物品并展示结果
                    consumeItem(player, itemStack);
                    handleSearchResult(serverLevel, playerPos, player, result, searchTime);
                } else {
                    // 搜索失败：不消耗物品，设置负缓存，显示失败消息
                    RetreatManager.updateCache(player.getUUID(), null);
                    handleSearchResult(serverLevel, playerPos, player, null, searchTime);
                }
            });
        }).exceptionally(throwable -> {
            // CancellationException 是预期行为（玩家再次使用铃铛时取消旧请求），不记录为错误
            Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                ? throwable.getCause() : throwable;
            if (!(cause instanceof java.util.concurrent.CancellationException)) {
                Global.LOGGER.error("搜索请求异常 - 玩家: {}", player.getName().getString(), throwable);
            }
            return null;
        });
    }

    private void consumeItem(Player player, ItemStack itemStack) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }
        }
    }

    /**
     * 处理搜索结果（展示飞行实体和坐标信息）
     */
    private void handleSearchResult(ServerLevel serverLevel, BlockPos playerPos, Player player,
                                    BlockPos structurePos, long searchTime) {
        serverLevel.getServer().execute(() -> {
            if (structurePos != null) {
                int height = serverLevel.getChunkSource().getGenerator().getFirstOccupiedHeight(
                    structurePos.getX(), structurePos.getZ(),
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    serverLevel.getChunk(structurePos).getHeightAccessorForGeneration(),
                    serverLevel.getChunkSource().randomState()
                );
                BlockPos structurePosXZ = new BlockPos(structurePos.getX(), 0, structurePos.getZ());
                BlockPos playerPosXZ = new BlockPos(playerPos.getX(), 0, playerPos.getZ());

                WindSeekingBellEntity bell = new WindSeekingBellEntity(serverLevel, player);
                ItemStack displayItem = player.getMainHandItem().copy();
                displayItem.setCount(1);
                bell.setItem(displayItem);
                bell.signalTo(structurePosXZ.above(height + 3));
                serverLevel.addFreshEntity(bell);

                int distance = (int) Math.sqrt(structurePosXZ.distSqr(playerPosXZ));
                String tpCommand = String.format("/tp %f %d %f",
                    structurePos.getX() + 0.5, height + 3, structurePos.getZ() + 0.5);

                Component coordinateMessage = Component.translatable(
                    "item.touhou_little_maid_spell.wind_seeking_bell.found_structure",
                    structurePos.getX(), height, structurePos.getZ(), distance
                ).withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, CLICK_SUGGEST_MESSAGE)));

                Component timingMessage = Component.translatable(
                    "item.touhou_little_maid_spell.wind_seeking_bell.search_time", searchTime
                ).withStyle(ChatFormatting.AQUA);

                player.sendSystemMessage(coordinateMessage);
                player.sendSystemMessage(timingMessage);

                if (player instanceof ServerPlayer sp) {
                    int count = sp.getStats().getValue(Stats.ITEM_USED.get(this));
                    if (count == 0) {
                        sp.sendSystemMessage(Component.translatable(
                            "item.touhou_little_maid_spell.wind_seeking_bell.first_use"
                        ).withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                }
            } else {
                Component timingMessage = Component.translatable(
                    "item.touhou_little_maid_spell.wind_seeking_bell.search_time", searchTime
                ).withStyle(ChatFormatting.GREEN);
                player.displayClientMessage(NO_STRUCTURE_MESSAGE, true);
                player.sendSystemMessage(timingMessage);
            }
        });
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
                                @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
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
}
