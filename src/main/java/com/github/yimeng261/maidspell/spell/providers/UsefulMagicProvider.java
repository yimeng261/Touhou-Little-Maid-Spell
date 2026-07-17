package com.github.yimeng261.maidspell.spell.providers;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.spell.data.MaidUsefulMagicSpellData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import cn.coostack.usefulmagic.extend.EntityExtendKt;
import cn.coostack.usefulmagic.items.prop.SpellBagItem;
import cn.coostack.usefulmagic.items.weapon.magic.MagicItem;
import cn.coostack.usefulmagic.items.weapon.wands.MagicWand;
import cn.coostack.usefulmagic.utils.MagicHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * UsefulMagic 法术书提供者。
 * 法术袋(SpellBagItem)=法术书；袋内 MagicItem=法术；女仆背包里的 MagicWand=施法器(提供伤害数值)。
 * 直接驱动 MagicItem 的 startUse/usingTick/release/stopUse（施法者为女仆），不经 mana；
 * 冷却优先取法杖真实冷却（MagicHelper.getFinalCD），无有效值时回退默认冷却。
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-07-18 02:02
 */
public class UsefulMagicProvider extends ISpellBookProvider<MaidUsefulMagicSpellData, UsefulMagicProvider.MagicWandPair> {

    /**
     * 默认冷却（tick）。
     */
    private static final int DEFAULT_COOLDOWN = 60;

    /**
     * 一颗法术球与已配对的法杖：收集阶段即配对，施法阶段直接复用，免二次遍历背包。
     */
    public record MagicWandPair(ItemStack magic, ItemStack wand) {
    }

    public UsefulMagicProvider() {
        super(MaidUsefulMagicSpellData::getOrCreate, MagicWandPair.class);
    }

    @Override
    public boolean isSpellBook(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof SpellBagItem;
    }

    @Override
    protected List<MagicWandPair> collectSpellFromSingleSpellBook(ItemStack spellBook, EntityMaid maid) {
        List<MagicWandPair> available = new ArrayList<>();
        MaidUsefulMagicSpellData data = getData(maid);
        // 法杖列表每本法术书只收集一次，避免对袋内每颗球都重新遍历整个背包。
        List<ItemStack> wands = collectMagicWands(maid);
        if (wands.isEmpty()) {
            return available;
        }
        ItemContainerContents contents = spellBook.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        contents.nonEmptyStream().forEach(magicStack -> {
            if (!(magicStack.getItem() instanceof MagicItem) || data.isSpellOnCooldown(getSpellId(magicStack))) {
                return;
            }
            // 收集时即配对法杖并随法术带出，施法时直接复用，避免重新遍历背包配对。
            ItemStack wand = findCompatibleWand(wands, magicStack);
            if (wand != null) {
                available.add(new MagicWandPair(magicStack, wand));
            }
        });
        return available;
    }

    /**
     * 收集女仆可用背包里的所有法杖（MagicWand）。
     */
    private List<ItemStack> collectMagicWands(EntityMaid maid) {
        List<ItemStack> wands = new ArrayList<>();
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof MagicWand) {
                wands.add(stack);
            }
        }
        return wands;
    }

    /**
     * 在给定法杖列表里找一把能施放该法术的法杖。
     * 只接受两类法杖：已装载同一颗球的（直接可用），或空法杖（可安全装载）。
     * 绝不选择已装载「其它」球的法杖——覆盖会销毁玩家预装的法术球（原版换球会退回袋子，这里没有这条通路）。
     *
     * @return 法杖 ItemStack，找不到返回 null
     */
    private ItemStack findCompatibleWand(List<ItemStack> wands, ItemStack magicStack) {
        ItemStack emptyWand = null;
        for (ItemStack stack : wands) {
            MagicWand wand = (MagicWand) stack.getItem();
            ItemStack loaded = wand.getLoadedMagic(stack);
            if (!loaded.isEmpty()) {
                // 已装载同一颗球：最佳选择，直接可用。
                if (ItemStack.isSameItemSameComponents(loaded, magicStack)) {
                    return stack;
                }
                // 已装载其它球：跳过，避免覆盖丢失。
                continue;
            }
            // 空法杖，等级够就记为候选（优先返回「已装载同球」的，故先不立即返回）。
            if (emptyWand == null && wand.canLoadMagic(stack, magicStack)) {
                emptyWand = stack;
            }
        }
        return emptyWand;
    }

    private String getSpellId(ItemStack magicStack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(magicStack.getItem());
        return id.toString();
    }

    @Override
    protected void initiateCasting(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MaidUsefulMagicSpellData data = getData(maid);
        LivingEntity target = data.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        List<MagicWandPair> spells = collectSpellFromAvailableSpellBooks(maid);
        if (spells.isEmpty()) {
            return;
        }
        // 收集阶段已配好法杖，这里直接取用，无需再次遍历背包。
        MagicWandPair chosen = spells.get((int) (Math.random() * spells.size()));
        ItemStack magicStack = chosen.magic();
        ItemStack wandStack = chosen.wand();
        if (!(magicStack.getItem() instanceof MagicItem magic) || !(wandStack.getItem() instanceof MagicWand wandItem)) {
            return;
        }
        updatePreciseOrientation(maid, target);
        // 施法前必须把法术球装载进法杖：伤害由 getMagicDamage(wand) 读法杖 WAND_MAGIC 组件得出，
        // 而非 release 传入的 ballStack。findCompatibleWand 只会返回「空法杖」或「已装载同球的法杖」，
        // 前者需要装载（并记录，施法结束后卸下还原），后者（玩家预装）保持不动。
        boolean loadedByProvider = wandItem.getLoadedMagic(wandStack).isEmpty();
        if (loadedByProvider) {
            wandItem.setLoadedMagic(wandStack, magicStack);
        }
        magic.startUse(maid, serverLevel, wandStack, magicStack);
        // 让女仆进入 UsefulMagic 的“蓄力”状态：蓄力类法术（陨石等）的粒子体系以 getCharging 判定有效性，
        // 不置位则其充能特效会被立即判为失效。结束时统一 resetChargeState 清除。
        EntityExtendKt.setCharging(maid, true);
        // 充能时长读法杖的 WAND_SPEED_FACTOR 组件，参数必须是法杖而非法术球。
        int maxTick = magic.getMaxTick(maid, wandStack);
        data.initiateCastingState(magicStack, wandStack, maxTick);
        data.setWandLoadedByProvider(loadedByProvider);
    }

    @Override
    public void processContinuousCasting(EntityMaid maid) {
        MaidUsefulMagicSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentMagic() == null) {
            return;
        }
        if (!(data.getCurrentMagic().getItem() instanceof MagicItem magic)) {
            EntityExtendKt.resetChargeState(maid);
            data.resetCastingState();
            return;
        }
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack ball = data.getCurrentMagic();
        ItemStack wand = data.getCurrentWand();

        LivingEntity target = data.getTarget();
        if (target != null && target.isAlive()) {
            updatePreciseOrientation(maid, target);
        }

        magic.usingTick(maid, wand, ball, serverLevel, data.getCastingTime());
        data.incrementCastingTime();

        if (data.getCastingTime() >= data.getMaxCastingTime()) {
            magic.release(maid, serverLevel, wand, ball, data.getCastingTime());
            magic.stopUse(maid, serverLevel, wand, ball, data.getCastingTime(), true);
            // 与其余法术 Provider 一致，正常完成施法时触发春华回响饰品。
            SpringBloomReturnBauble.onSpellCast(maid, "usefulmagic", getSpellId(ball), data.getTarget());
            finishCasting(maid, data);
        }
    }

    @Override
    public void stopCasting(EntityMaid maid) {
        MaidUsefulMagicSpellData data = getData(maid);
        if (data == null || !data.isCasting() || data.getCurrentMagic() == null) {
            return;
        }
        if (maid.level() instanceof ServerLevel serverLevel
                && data.getCurrentMagic().getItem() instanceof MagicItem magic) {
            boolean reachedMax = data.getCastingTime() >= data.getMaxCastingTime();
            magic.stopUse(maid, serverLevel, data.getCurrentWand(), data.getCurrentMagic(),
                    data.getCastingTime(), reachedMax);
        }
        finishCasting(maid, data);
    }

    /**
     * 施法收尾（正常完成与中断共用）：设冷却 → 卸球 → 清蓄力态 → 重置施法状态。
     * setCooldown 必须在卸下法术球之前调用（getFinalCD 依赖法杖上仍装载的球）。
     */
    private void finishCasting(EntityMaid maid, MaidUsefulMagicSpellData data) {
        setCooldown(maid, data, data.getCurrentMagic(), data.getCurrentWand());
        unloadWandIfNeeded(data);
        EntityExtendKt.resetChargeState(maid);
        data.resetCastingState();
    }

    /**
     * 若本次法术球是 Provider 装载进空法杖的，施法结束后卸下，还原法杖为空——
     * 既不占用法杖（下次可换别的球），也不会误删玩家预装的球（那种情况 loadedByProvider=false）。
     */
    private void unloadWandIfNeeded(MaidUsefulMagicSpellData data) {
        if (!data.isWandLoadedByProvider()) {
            return;
        }
        ItemStack wand = data.getCurrentWand();
        if (wand != null && !wand.isEmpty() && wand.getItem() instanceof MagicWand magicWand) {
            magicWand.setLoadedMagic(wand, ItemStack.EMPTY);
        }
    }

    /**
     * 设置法术冷却：优先读法杖 + 法术球算出的真实冷却（getFinalCD），无有效值时回退到默认冷却。
     * 必须在法术球仍装载于法杖时调用；data 由调用方传入（此时已非空）。
     */
    private void setCooldown(EntityMaid maid, MaidUsefulMagicSpellData data, ItemStack magicStack, ItemStack wandStack) {
        if (magicStack == null || magicStack.isEmpty()) {
            return;
        }
        int cooldown = DEFAULT_COOLDOWN;
        if (wandStack != null && !wandStack.isEmpty() && wandStack.getItem() instanceof MagicWand) {
            int finalCd = MagicHelper.INSTANCE.getFinalCD(wandStack);
            if (finalCd > 0) {
                cooldown = finalCd;
            }
        }
        data.setSpellCooldown(getSpellId(magicStack), cooldown, maid);
    }
}
