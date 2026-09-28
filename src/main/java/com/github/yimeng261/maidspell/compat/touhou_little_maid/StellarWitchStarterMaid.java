package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 创建绑定玩家的星之魔女女仆魂符。
 * 已装魂符由 TLM 在放出时校验女仆数据里的 {@code Owner}。
 */
public final class StellarWitchStarterMaid {

    /** 出厂 SNBT 的路径（数据包资源，跟着 jar 走）。 */
    private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(
            "touhou_little_maid_spell", "maid/stellar_witch_maid.snbt");

    /** 缓存内置模板；失败后不再重复读取和记录错误。 */
    @Nullable
    private static CompoundTag cached;
    private static boolean loadFailed;

    private StellarWitchStarterMaid() {
    }

    /**
     * 创建直接装入女仆数据的已装魂符。
     *
     * <p>TLM 的 {@code SMART_SLAB_HAS_MAID} 会在放出前校验 {@code MAID_INFO} 组件里的 {@code Owner}，
     * 所以主人 UUID 在奖励生成时就被固定，不再依赖放出位置附近的玩家。</p>
     */
    public static ItemStack createSoulCharm(UUID ownerId) {
        CompoundTag maidInfo = template();
        if (maidInfo == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag boundMaidInfo = maidInfo.copy();
        boundMaidInfo.putUUID("Owner", ownerId);
        boundMaidInfo.putBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED, true);
        boundMaidInfo.remove("UUID");
        CompoundTag persistentData = boundMaidInfo.getCompound("NeoForgeData");
        persistentData.putBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED, true);
        boundMaidInfo.put("NeoForgeData", persistentData);
        MaidOriginData.upgradeStarWitchSpellBookData(boundMaidInfo);

        ItemStack soulCharm = new ItemStack(
                com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_HAS_MAID.get());
        soulCharm.set(InitDataComponent.MAID_INFO, CustomData.of(boundMaidInfo));
        return soulCharm;
    }

    /**
     * 从数据包读模板并缓存。
     *
     * <p>{@code TagParser.parseTag} 就是原版 {@code /give ... {…}} 用的那个解析器，
     * 所以作者给的 SNBT（含 {@code 1b}、{@code 0.08d}、{@code [I; …]} 这类字面量）它原样认。
     */
    @Nullable
    private static synchronized CompoundTag template() {
        if (cached != null || loadFailed) {
            return cached;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        try {
            Optional<Resource> resource = server.getResourceManager().getResource(TEMPLATE);
            if (resource.isEmpty()) {
                loadFailed = true;
                com.github.yimeng261.maidspell.MaidSpellMod.LOGGER.error(
                        "誓约信物：找不到女仆模板 {}，驯服奖励将退化为空", TEMPLATE);
                return null;
            }
            String snbt;
            try (BufferedReader reader = resource.get().openAsReader()) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                snbt = sb.toString();
            }
            cached = TagParser.parseTag(snbt);
            return cached;
        } catch (IOException | CommandSyntaxException | RuntimeException e) {
            loadFailed = true;
            com.github.yimeng261.maidspell.MaidSpellMod.LOGGER.error(
                    "誓约信物：女仆模板解析失败，驯服奖励将退化为空", e);
            return null;
        }
    }

    /** 调试用：模板是否已经成功载入。 */
    public static boolean templateAvailable() {
        return template() != null;
    }
}
