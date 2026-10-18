package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 从结构模板里读出各来源的初始永久效果，供还没有效果快照的旧来源标记迁移用。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class MaidOriginPresetReloadListener extends SimplePreparableReloadListener<Map<String, ListTag>> {
    private static volatile Map<String, ListTag> presets = Map.of();

    @Nullable
    static ListTag find(String source) {
        return presets.get(source);
    }

    @SubscribeEvent
    public static void addListener(AddReloadListenerEvent event) {
        event.addListener(new MaidOriginPresetReloadListener());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        presets = Map.of();
    }

    @Override
    protected Map<String, ListTag> prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<String, ListTag> result = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        resources.listResources("structure", id -> MaidSpellMod.MOD_ID.equals(id.getNamespace())
                && id.getPath().endsWith(".nbt")).forEach((id, resource) -> {
            try (var input = resource.open()) {
                CompoundTag template = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                for (Tag element : template.getList("entities", Tag.TAG_COMPOUND)) {
                    CompoundTag maid = ((CompoundTag) element).getCompound("nbt");
                    if (!"touhou_little_maid:maid".equals(maid.getString("id"))) {
                        continue;
                    }
                    String source = MaidOriginData.getOrigin(maid).getString("Source");
                    if (source.isEmpty()) {
                        continue;
                    }
                    ListTag effects = MaidOriginData.collectPresetEffects(maid.getList("active_effects", Tag.TAG_COMPOUND));
                    if (!mergeSource(result, source, effects)) {
                        ambiguous.add(source);
                    }
                }
            } catch (IOException exception) {
                MaidSpellMod.LOGGER.warn("读取结构女仆的预设效果失败: {}", id, exception);
            }
        });
        // 同一来源下有多只初始效果不同的女仆时，无法确定旧数据该用哪份预设，一律不迁移。
        ambiguous.forEach(result::remove);
        return Map.copyOf(result);
    }

    static boolean mergeSource(Map<String, ListTag> result, String source, ListTag effects) {
        ListTag previous = result.putIfAbsent(source, effects);
        return previous == null || new HashSet<>(previous).equals(new HashSet<>(effects));
    }

    @Override
    protected void apply(Map<String, ListTag> loaded, ResourceManager resources, ProfilerFiller profiler) {
        presets = loaded;
    }
}
