package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Template-derived migration for old origin markers that predate effect snapshots. */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
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
        resources.listResources("structures", id -> MaidSpellMod.MOD_ID.equals(id.getNamespace())
                && id.getPath().endsWith(".nbt")).forEach((id, resource) -> {
            try (var input = resource.open()) {
                CompoundTag template = NbtIo.readCompressed(input);
                for (Tag element : template.getList("entities", Tag.TAG_COMPOUND)) {
                    CompoundTag maid = ((CompoundTag) element).getCompound("nbt");
                    if (!"touhou_little_maid:maid".equals(maid.getString("id"))) {
                        continue;
                    }
                    String source = MaidOriginData.getOrigin(maid).getString("Source");
                    if (source.isEmpty()) {
                        continue;
                    }
                    ListTag effects = MaidOriginData.collectPresetEffects(maid.getList("ActiveEffects", Tag.TAG_COMPOUND));
                    if (!mergeSource(result, source, effects)) {
                        ambiguous.add(source);
                    }
                }
            } catch (IOException exception) {
                MaidSpellMod.LOGGER.warn("Cannot read structure maid presets from {}", id, exception);
            }
        });
        // Multiple distinct maids sharing a source cannot safely migrate old data.
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
