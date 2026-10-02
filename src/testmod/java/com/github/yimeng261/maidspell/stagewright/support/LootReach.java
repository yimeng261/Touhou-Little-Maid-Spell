package com.github.yimeng261.maidspell.stagewright.support;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * 求服务器实际加载的战利品表能产出哪些物品。
 * <p>把 LootTable 用自身 codec 编码成 JSON 再遍历条目，不依赖私有字段。键是物品 ID，
 * 法术卷轴附带 {@code [spell=…]}、药水附带 {@code [potion=…]}，与 {@link #keyOf(ItemStack, MinecraftServer)} 一致。
 */
public final class LootReach {
    private static final String SPELL_CONTAINER = "irons_spellbooks:spell_container";

    private LootReach() {
    }

    public static ResourceKey<LootTable> key(String id) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id));
    }

    public static Set<String> reachable(MinecraftServer server, String id) {
        Set<String> out = new TreeSet<>();
        Set<String> visiting = new HashSet<>();
        visiting.add(id);
        walkTable(server, encode(server, id), out, visiting);
        return out;
    }

    /** 掉落物的键，规则与可达集合相同。 */
    public static String keyOf(ItemStack stack, MinecraftServer server) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        JsonElement patch = DataComponentPatch.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), stack.getComponentsPatch())
                .result().orElse(null);
        if (patch instanceof JsonObject object && object.has(SPELL_CONTAINER)) {
            return id + spellQualifier(object.getAsJsonObject(SPELL_CONTAINER));
        }
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.potion().isPresent()) {
            return id + "[potion=" + potion.potion().get().getRegisteredName() + "]";
        }
        return id;
    }

    private static JsonObject encode(MinecraftServer server, String id) {
        LootTable table = server.reloadableRegistries().getLootTable(key(id));
        return LootTable.DIRECT_CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), table)
                .getOrThrow(message -> new IllegalStateException("无法编码战利品表 " + id + ": " + message))
                .getAsJsonObject();
    }

    private static void walkTable(MinecraftServer server, JsonObject table, Set<String> out, Set<String> visiting) {
        JsonArray pools = table.getAsJsonArray("pools");
        if (pools == null) {
            return;
        }
        for (JsonElement pool : pools) {
            JsonArray entries = pool.getAsJsonObject().getAsJsonArray("entries");
            if (entries != null) {
                for (JsonElement entry : entries) {
                    walkEntry(server, entry.getAsJsonObject(), out, visiting);
                }
            }
        }
    }

    private static void walkEntry(MinecraftServer server, JsonObject entry, Set<String> out, Set<String> visiting) {
        String type = strip(entry.get("type").getAsString());
        switch (type) {
            case "item" -> out.add(ResourceLocation.parse(entry.get("name").getAsString()) + qualifier(entry));
            case "tag" -> out.add("#" + entry.get("name").getAsString());
            case "loot_table" -> {
                JsonElement value = entry.get("value");
                if (value.isJsonObject()) {
                    walkTable(server, value.getAsJsonObject(), out, visiting);
                } else if (visiting.add(value.getAsString())) {
                    walkTable(server, encode(server, value.getAsString()), out, visiting);
                }
            }
            case "alternatives", "group", "sequence" -> {
                for (JsonElement child : entry.getAsJsonArray("children")) {
                    walkEntry(server, child.getAsJsonObject(), out, visiting);
                }
            }
            default -> {
                // empty / dynamic 不产出固定物品
            }
        }
    }

    private static String qualifier(JsonObject entry) {
        JsonArray functions = entry.getAsJsonArray("functions");
        if (functions == null) {
            return "";
        }
        for (JsonElement element : functions) {
            JsonObject function = element.getAsJsonObject();
            String name = strip(function.get("function").getAsString());
            if (name.equals("set_components")) {
                JsonObject components = function.getAsJsonObject("components");
                if (components.has(SPELL_CONTAINER)) {
                    return spellQualifier(components.getAsJsonObject(SPELL_CONTAINER));
                }
                if (components.has("minecraft:potion_contents")) {
                    JsonElement contents = components.get("minecraft:potion_contents");
                    String potion = contents.isJsonObject()
                            ? contents.getAsJsonObject().get("potion").getAsString()
                            : contents.getAsString();
                    return "[potion=" + potion + "]";
                }
            }
            if (name.equals("set_potion")) {
                return "[potion=" + function.get("id").getAsString() + "]";
            }
        }
        return "";
    }

    /** 空的法术容器（部分护甲自带）不附加限定。 */
    private static String spellQualifier(JsonObject container) {
        JsonArray data = container.getAsJsonArray("data");
        if (data == null || data.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("[spell=");
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(data.get(i).getAsJsonObject().get("id").getAsString());
        }
        return builder.append(']').toString();
    }

    private static String strip(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
