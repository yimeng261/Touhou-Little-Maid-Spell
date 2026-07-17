package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆 UsefulMagic 法术数据。每个女仆一份，集中管理施法状态与冷却。
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-07-18 02:01
 */
public class MaidUsefulMagicSpellData extends IMaidSpellData {

    private static final Map<UUID, MaidUsefulMagicSpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();

    // === 施法状态 ===
    private int castingTime = 0;

    private int maxCastingTime = 0;

    /**
     * 当前施放的法术（MagicItem 的 ItemStack，作 ballStack）。
     */
    private ItemStack currentMagic = null;

    /**
     * 与当前法术配对的法杖（作 wandStack，提供伤害数值）。
     */
    private ItemStack currentWand = null;

    /**
     * 本次法术球是否由 Provider 装载进法杖（是则施法结束后需卸下，避免占用法杖/丢失玩家预装的球）。
     */
    private boolean wandLoadedByProvider = false;

    private MaidUsefulMagicSpellData() {
    }

    public static MaidUsefulMagicSpellData getOrCreate(UUID maidUuid) {
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidUsefulMagicSpellData());
    }

    public static void remove(UUID maidUuid) {
        MAID_DATA_MAP.remove(maidUuid);
    }

    public static void clearAll() {
        MAID_DATA_MAP.clear();
    }

    // === 施法状态访问 ===

    public int getCastingTime() {
        return castingTime;
    }

    public void incrementCastingTime() {
        this.castingTime++;
    }

    public int getMaxCastingTime() {
        return maxCastingTime;
    }

    public ItemStack getCurrentMagic() {
        return currentMagic;
    }

    public ItemStack getCurrentWand() {
        return currentWand;
    }

    public boolean isWandLoadedByProvider() {
        return wandLoadedByProvider;
    }

    public void setWandLoadedByProvider(boolean loadedByProvider) {
        this.wandLoadedByProvider = loadedByProvider;
    }

    /**
     * 初始化一次施法状态。
     *
     * @param magic    法术物品栈（ballStack）
     * @param wand     配对法杖栈（wandStack）
     * @param duration 充能时长（tick），至少 1
     */
    public void initiateCastingState(ItemStack magic, ItemStack wand, int duration) {
        this.currentMagic = magic;
        this.currentWand = wand;
        this.castingTime = 0;
        this.maxCastingTime = Math.max(1, duration);
        this.setCasting(true);
    }

    /**
     * 重置施法状态（保留 target，让女仆继续锁敌，与 Goety 一致）。
     */
    @Override
    public void resetCastingState() {
        this.setCasting(false);
        this.castingTime = 0;
        this.maxCastingTime = 0;
        this.currentMagic = null;
        this.currentWand = null;
        this.wandLoadedByProvider = false;
    }
}
