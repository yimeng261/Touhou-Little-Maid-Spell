package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.dimension.*;
import com.github.yimeng261.maidspell.entity.WindSeekingBellEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
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
import java.util.List;
import java.util.UUID;
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
                findHiddenRetreat(serverLevel, playerPos, player, itemStack);
            } else {
                // 在主世界：传送到归隐之地
                teleportToRetreat(serverPlayer);
            }

            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    private void teleportToRetreat(ServerPlayer player) {
        if (Config.enablePrivateDimensions) {
            teleportToPrivateRetreat(player);
        } else {
            teleportToSharedRetreat(player);
        }
    }

    private void teleportToPrivateRetreat(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel retreatLevel = PlayerRetreatManager.getLoadedPlayerRetreat(server, player.getUUID());
        if (retreatLevel == null) {
            UUID playerUUID = player.getUUID();
            PlayerRetreatManager.getOrCreatePlayerRetreatAsync(server, playerUUID).whenComplete((createdLevel, throwable) ->
                    server.execute(() -> {
                        ServerPlayer currentPlayer = server.getPlayerList().getPlayer(playerUUID);
                        if (currentPlayer == null) {
                            return;
                        }

                        if (throwable != null || createdLevel == null) {
                            Global.LOGGER.error("Failed to prepare retreat dimension for player: {}", playerUUID, throwable);
                            currentPlayer.displayClientMessage(
                                    Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.teleport_failed")
                                            .withStyle(ChatFormatting.RED), true);
                            return;
                        }

                        TheRetreatDimension.teleportToRetreat(currentPlayer, createdLevel);
                        currentPlayer.displayClientMessage(
                                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.entered_retreat")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    }));
            return;
        }

        TheRetreatDimension.teleportToRetreat(player, retreatLevel);
        player.displayClientMessage(
            Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.entered_retreat")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private void teleportToSharedRetreat(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel sharedRetreat = PlayerRetreatManager.getLoadedSharedRetreat(server);
        if (sharedRetreat != null) {
            enterSharedRetreat(player, sharedRetreat);
            return;
        }

        UUID playerUUID = player.getUUID();
        PlayerRetreatManager.getOrCreateSharedRetreatAsync(server).whenComplete((createdLevel, throwable) ->
                server.execute(() -> {
                    ServerPlayer currentPlayer = server.getPlayerList().getPlayer(playerUUID);
                    if (currentPlayer == null) {
                        return;
                    }

                    if (throwable != null || createdLevel == null) {
                        Global.LOGGER.error("Failed to prepare shared retreat dimension", throwable);
                        currentPlayer.displayClientMessage(
                                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.teleport_failed")
                                        .withStyle(ChatFormatting.RED), true);
                        return;
                    }

                    enterSharedRetreat(currentPlayer, createdLevel);
                }));
    }

    private void enterSharedRetreat(ServerPlayer player, ServerLevel sharedRetreat) {
        RetreatDimensionData data = RetreatDimensionData.get(player.getServer());
        RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(player.getUUID());

        // 首次使用时注册并分配配额
        if (info == null) {
            data.registerDimension(player.getUUID());
            info = data.getDimensionInfo(player.getUUID());
            // 配额限制开启时给予1个配额；关闭时不限制（配额不会被检查）
            if (Config.enableSharedQuotaLimit) {
                info.structureQuota = 1;
            }
            data.updateAccessTime(player.getUUID());

            player.displayClientMessage(
                    Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.first_entry_shared")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }

        TheRetreatDimension.teleportToRetreat(player, sharedRetreat);
        player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.entered_retreat")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /**
     * 搜索隐世之境结构。
     * 搜索成功后消耗物品；共享模式下，已找到过的结构免费重复查看。
     */
    private void findHiddenRetreat(ServerLevel serverLevel, BlockPos playerPos, Player player, ItemStack itemStack) {
        // 冷却中：防止连续右键重复触发
        if (player.getCooldowns().isOnCooldown(this)) {
            return;
        }

        long searchStartTime = System.currentTimeMillis();

        // 0. 共享模式 + 配额限制：首次使用铃时注册并分配配额（兼容 TP 直接进入维度的情况）
        if (!Config.enablePrivateDimensions && Config.enableSharedQuotaLimit) {
            RetreatDimensionData data = RetreatDimensionData.get(player.getServer());
            RetreatDimensionData.DimensionInfo info = data.getDimensionInfo(player.getUUID());
            if (info == null) {
                data.registerDimension(player.getUUID());
                info = data.getDimensionInfo(player.getUUID());
                info.structureQuota = 1;
                data.updateAccessTime(player.getUUID());

                player.displayClientMessage(
                        Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.first_entry_shared")
                                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }

        // 1. 共享模式 + 配额限制：已找到的结构免费查看
        if (!Config.enablePrivateDimensions && Config.enableSharedQuotaLimit) {
            RetreatDimensionData data = RetreatDimensionData.get(player.getServer());
            BlockPos persistedPos = data.getFoundStructurePos(player.getUUID());
            if (persistedPos != null) {
                ItemStack displayItem = itemStack.copy();
                displayItem.setCount(1);
                consumeItem(player, itemStack);
                player.getCooldowns().addCooldown(this, 20);
                handleSearchResult(serverLevel, playerPos, player, persistedPos,
                        System.currentTimeMillis() - searchStartTime, true, displayItem);
                return;
            }
        }

        // 2. 检查内存缓存
        RetreatManager.CacheResult cacheResult = RetreatManager.checkCache(player.getUUID());
        if (cacheResult.hasCache && !cacheResult.isNegative) {
            ItemStack displayItem = itemStack.copy();
            displayItem.setCount(1);
            consumeItem(player, itemStack);
            player.getCooldowns().addCooldown(this, 20);
            handleSearchResult(serverLevel, playerPos, player, cacheResult.position,
                    System.currentTimeMillis() - searchStartTime, true, displayItem);
            return;
        }

        // 3. 共享模式 + 配额限制：检查配额
        if (!Config.enablePrivateDimensions && Config.enableSharedQuotaLimit) {
            if (!SharedRetreatManager.hasQuota(player.getServer(), player.getUUID())) {
                player.displayClientMessage(
                        Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.no_quota")
                                .withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        // 4. 提示搜索中
        player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.waiting_for_structure")
                        .withStyle(ChatFormatting.YELLOW), true);

        // 5. 发起搜索
        CompletableFuture<BlockPos> future = RetreatManager.searchStructure(
                serverLevel, player.getUUID(), playerPos);

        // 6. 处理结果
        future.thenAccept(result -> {
            long searchTime = System.currentTimeMillis() - searchStartTime;
            serverLevel.getServer().execute(() -> {
                if (result != null) {
                    // 共享模式 + 配额限制：消耗配额，失败则中止
                    if (!Config.enablePrivateDimensions && Config.enableSharedQuotaLimit) {
                        if (!SharedRetreatManager.tryConsumeQuota(player.getServer(), player.getUUID())) {
                            player.displayClientMessage(
                                    Component.translatable("item.touhou_little_maid_spell.wind_seeking_bell.no_quota")
                                            .withStyle(ChatFormatting.RED), true);
                            return;
                        }
                        RetreatDimensionData data = RetreatDimensionData.get(player.getServer());
                        data.setFoundStructurePos(player.getUUID(), result);
                    }
                    RetreatManager.updateCache(player.getUUID(), result);

                    // 检查玩家手上是否仍持有寻风之铃（异步搜索期间可能已切换物品）
                    boolean holdingBell = player.getMainHandItem().getItem() instanceof WindSeekingBell
                            || player.getOffhandItem().getItem() instanceof WindSeekingBell;
                    if (holdingBell) {
                        ItemStack displayItem = itemStack.copy();
                        displayItem.setCount(1);
                        consumeItem(player, itemStack);
                        player.getCooldowns().addCooldown(WindSeekingBell.this, 20);
                        handleSearchResult(serverLevel, playerPos, player, result, searchTime, true, displayItem);
                    } else {
                        // 铃不在手上：仅展示坐标，不消耗物品，不飞铃
                        handleSearchResult(serverLevel, playerPos, player, result, searchTime, false, ItemStack.EMPTY);
                    }
                    player.sendSystemMessage(Component.translatable(
                            "item.touhou_little_maid_spell.wind_seeking_bell.first_use"
                    ).withStyle(ChatFormatting.LIGHT_PURPLE));
                } else {
                    // 搜索失败：不消耗物品，设置负缓存
                    RetreatManager.updateCache(player.getUUID(), null);
                    handleSearchResult(serverLevel, playerPos, player, null, searchTime, false, ItemStack.EMPTY);
                }
            });
        }).exceptionally(throwable -> {
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
     * 处理搜索结果（展示坐标信息，可选飞行实体）
     *
     * @param launchBell  true = 铃飞出（类似末影之眼）；false = 仅显示坐标
     * @param displayItem 飞行实体使用的显示物品（在消耗前拷贝），launchBell=false 时忽略
     */
    private void handleSearchResult(ServerLevel serverLevel, BlockPos playerPos, Player player,
                                    BlockPos structurePos, long searchTime, boolean launchBell,
                                    ItemStack displayItem) {
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

                if (launchBell) {
                    WindSeekingBellEntity bell = new WindSeekingBellEntity(serverLevel, player);
                    bell.setItem(displayItem);
                    bell.signalTo(structurePosXZ.above(height + 3));
                    serverLevel.addFreshEntity(bell);
                }

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
    public void appendHoverText(@Nonnull ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level,
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
