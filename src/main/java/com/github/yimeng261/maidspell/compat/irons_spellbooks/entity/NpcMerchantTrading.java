package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import io.redspace.ironsspellbooks.entity.mobs.wizards.IMerchantWizard;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.OptionalInt;

/**
 * 结构 NPC 商人（精灵圣卫、观星术士、星之魔女）共用的交易流程。
 * <p>
 * 三者父类各不相同，由各自在 {@code mobInteract}、{@code aiStep}、死亡和移出世界时调用这里。
 */
public final class NpcMerchantTrading {
    /**
     * 精灵圣卫、观星术士报价的默认每日上限：交易界面里 {@code maxUses} 就是它，卖完要等半天补货。
     * <p>观星术士不是全表统一：星锚珍珠按 {@code AstroMancerTrades#dailyLimitFor} 只给 1，星陨石那一条更是全局一次。
     */
    public static final int DAILY_TRADE_MAX_USES = 5;

    /** 交易对象离开这么多格就放开交易 */
    private static final double TRADING_MAX_DISTANCE = 8.0D;

    private NpcMerchantTrading() {
    }

    /**
     * 玩家右键时打开交易（精灵圣卫、观星术士）：打架或正被激怒、没有报价、已有人在交易时返回 false，
     * 由调用方按普通交互处理，与原版村民一致，也不会关掉前一个玩家的界面；否则在服务端按需补货后打开交易界面。
     */
    public static <T extends Mob & IMerchantWizard> boolean tryOpenTrade(T npc, Player player) {
        boolean preventTrade = npc.isAggressive() || npc.getTarget() != null
                || (!npc.level().isClientSide && npc.getOffers().isEmpty());
        if (preventTrade || npc.isTrading()) {
            return false;
        }
        if (!npc.level().isClientSide) {
            if (npc.shouldRestock()) {
                npc.restock();
            }
            startTrading(npc, player, 0);
        }
        return true;
    }

    /**
     * 打开交易界面并发送报价；菜单没打开时清除交易对象，未打开的菜单不会调用 {@code MerchantMenu.removed()}，否则这只商人会一直被占着。
     */
    public static <T extends Entity & Merchant> void startTrading(T npc, Player player, int villagerLevel) {
        npc.setTradingPlayer(player);
        OptionalInt containerId = player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> new MerchantMenu(id, inventory, npc), npc.getDisplayName()));
        if (containerId.isEmpty()) {
            npc.setTradingPlayer(null);
            return;
        }
        MerchantOffers offers = npc.getOffers();
        if (!offers.isEmpty()) {
            player.sendMerchantOffers(containerId.getAsInt(), offers, villagerLevel,
                    npc.getVillagerXp(), npc.showProgressBar(), npc.canRestock());
        }
    }

    /**
     * 交易对象死亡、下线、换维度或走出 {@value #TRADING_MAX_DISTANCE} 格后放开交易，界面随之关闭。
     */
    public static <T extends Entity & Merchant> void releaseAbsentTrader(T npc) {
        Player trader = npc.getTradingPlayer();
        if (trader != null && !npc.level().isClientSide && (!trader.isAlive() || trader.level() != npc.level()
                || npc.distanceToSqr(trader) > TRADING_MAX_DISTANCE * TRADING_MAX_DISTANCE)) {
            npc.setTradingPlayer(null);
        }
    }
}
