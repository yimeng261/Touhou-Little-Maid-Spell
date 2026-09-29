package com.github.yimeng261.maidspell.stagewright.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.function.BiConsumer;

/** 集成服拓扑里操作宿主玩家的小工具：用真实的服务端交互入口，场景结束时恢复玩家状态。 */
public final class Players {
    /** 等客户端确认传送的上限。 */
    private static final int SETTLE_TICKS = 60;

    private Players() {
    }

    /**
     * 宿主玩家场景（超平坦场地）：把玩家带到场地、{@link #keepState 保存状态}，等传送落定后运行 body。
     * 预算自动加上等待落定的时间。
     */
    public static Scene hostScene(String name, int budget, BiConsumer<SceneContext, ServerPlayer> body) {
        return Checks.superflat(name, budget + SETTLE_TICKS, ctx -> {
            ServerPlayer player = ctx.playerHere();
            keepState(ctx, player);
            settled(ctx, player, () -> body.accept(ctx, player));
        });
    }

    /**
     * 切到生存模式（受伤、消耗物品按正常规则），场景结束时（含失败/超时）恢复游戏模式、背包、生命、饥饿、
     * 效果、着火、潜行和打开的界面。
     */
    public static void keepState(SceneContext ctx, ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.SURVIVAL);
        ctx.cleanup(() -> player.setGameMode(mode));
        ListTag inventory = player.getInventory().save(new ListTag());
        int selected = player.getInventory().selected;
        float health = player.getHealth();
        int food = player.getFoodData().getFoodLevel();
        float saturation = player.getFoodData().getSaturationLevel();
        ctx.cleanup(() -> {
            player.closeContainer();
            player.getInventory().load(inventory);
            player.getInventory().selected = selected;
            player.removeAllEffects();
            player.clearFire();
            player.setShiftKeyDown(false);
            player.setHealth(health);
            player.getFoodData().setFoodLevel(food);
            player.getFoodData().setSaturation(saturation);
            player.inventoryMenu.broadcastChanges();
        });
    }

    /**
     * 等客户端确认最近一次传送后再运行 next。跨维度传送后服务端把玩家视为"正在换维度"（免疫一切伤害），
     * 直到客户端确认的是最新一次传送；场景间连续传送时确认总落后一步，要测受伤的场景需先等它落定。
     */
    private static void settled(SceneContext ctx, ServerPlayer player, Runnable next) {
        ctx.await(() -> !player.isChangingDimension()).within(SETTLE_TICKS).then(next);
    }

    /** 与"玩家能否受伤"有关的状态，记进场景结果便于排查。 */
    public static String damageState(ServerPlayer player) {
        return "mode=" + player.gameMode.getGameModeForPlayer()
                + " abilities.invulnerable=" + player.getAbilities().invulnerable
                + " invulnerable=" + player.isInvulnerable()
                + " invulnerableToGeneric=" + player.isInvulnerableTo(player.damageSources().generic())
                + " spawnInvulnerableTime=" + Reflect.<Integer>field(player, ServerPlayer.class, "spawnInvulnerableTime")
                + " changingDimension=" + player.isChangingDimension()
                + " difficulty=" + player.level().getDifficulty()
                + " invulnerableTime=" + player.invulnerableTime
                + " effects=" + player.getActiveEffects();
    }

    /** 放到主手，返回玩家手上实际的那份物品。 */
    public static ItemStack hold(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player.getMainHandItem();
    }

    /** 右键使用主手物品（与收到使用物品数据包时相同的服务端入口）。 */
    public static InteractionResult use(ServerPlayer player) {
        return player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    /** 手持女仆配置的驯服物品右键女仆（与收到交互数据包时相同的服务端入口），返回是否驯服成功。 */
    public static boolean tame(ServerPlayer player, EntityMaid maid) {
        ItemStack[] items = maid.getTamedItem().getItems();
        if (items.length == 0) {
            throw new IllegalStateException("女仆驯服物品配置为空");
        }
        ItemStack previous = player.getMainHandItem().copy();
        hold(player, items[0].copy());
        player.interactOn(maid, InteractionHand.MAIN_HAND);
        hold(player, previous);
        return maid.isOwnedBy(player);
    }

    /** 无 AI 的女仆经真实交互驯服到玩家名下（失败记一条），第一个饰品槽放入 bauble。 */
    public static EntityMaid ownedMaid(SceneContext ctx, ServerPlayer player, int dx, int dy, int dz, String bauble) {
        EntityMaid maid = Actors.stillMaid(ctx, dx, dy, dz);
        ctx.check(tame(player, maid)).as("驯服女仆").isTrue();
        Actors.equipBauble(maid, bauble);
        return maid;
    }
}
