package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTrades;
import com.github.yimeng261.maidspell.item.MaidSpellCreativeTab;
import com.github.yimeng261.maidspell.mixin.MaidSpellMixinPlugin;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.github.yimeng261.maidspell.task.TaskTestAccess;
import com.github.yimeng261.maidspell.worldgen.structure.LandJigsawStructure;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 跟进上游后补的验收：联动 mixin 的加载开关、酒狐交易栏、法术射表、语言文案、咖啡厅的 Youkais' Feasts 回退池与梦云水晶默认白名单。
 */
public final class UpstreamSyncScenes {
    /** 只在对应模组在场时才应用的联动 mixin 包。 */
    private static final Map<String, String> OPTIONAL_MIXIN_PACKAGES = Map.of(
            "legendary.", "legendary_monsters",
            "explorerscompass.", "explorerscompass",
            "ftbquests.", "ftbquests",
            "callresponse.", "callresponse");

    private static final List<String> NEW_STRUCTURES = List.of("cherry_cabin", "enchantress_footsteps_crimson_cabin",
            "enchantress_footsteps_basalt_forge", "enchantress_footsteps_warped_perch", "enchantress_footsteps_soulvalley_nook");

    private UpstreamSyncScenes() {
    }

    public static List<Scene> dedicatedServer() {
        return List.of(
                Checks.scene("mixin.optionalCompatFollowsLoadedMods", 5, UpstreamSyncScenes::optionalCompatMixins),
                Checks.scene("winefox.trades.enchantedBooksAndScrolls", 5, UpstreamSyncScenes::trades),
                Checks.scene("spell_range_table.itemTabAndAltarRecipe", 5, UpstreamSyncScenes::rangeTableItem),
                Checks.superflat("spell_range_table.ridePoseGate", 10, UpstreamSyncScenes::ridePoseGate),
                Checks.scene("lang.newStructureAndRetreatNames", 5, UpstreamSyncScenes::structureNames),
                Checks.scene("lang.dreamCrystalDescribesMaidSlotsOnly", 5, UpstreamSyncScenes::dreamCrystalDesc5),
                Checks.scene("fairy_maid_cafe.youkaisFeastsFallbackPool", 5, UpstreamSyncScenes::cafeFallback),
                Checks.scene("config.dreamCrystalWhitelistCoversYoukaiMods", 5, UpstreamSyncScenes::whitelist));
    }

    /** 传奇怪物、Explorer's Compass、FTB Quests 的 mixin 只在对应模组在场时应用，其余 mixin 不受这几个开关影响。 */
    private static void optionalCompatMixins(SceneContext ctx) {
        JsonObject config;
        try (InputStream stream = MaidSpellMod.class.getResourceAsStream("/touhou_little_maid_spell.mixins.json");
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String root = config.get("package").getAsString() + ".";
        MaidSpellMixinPlugin plugin = new MaidSpellMixinPlugin();
        plugin.onLoad(root);
        Map<String, Boolean> expected = new TreeMap<>();
        Map<String, Boolean> actual = new TreeMap<>();
        for (String side : List.of("mixins", "client", "server")) {
            JsonArray mixins = config.has(side) ? config.getAsJsonArray(side) : new JsonArray();
            for (JsonElement element : mixins) {
                String mixin = element.getAsString();
                OPTIONAL_MIXIN_PACKAGES.forEach((pkg, mod) -> {
                    if (mixin.startsWith(pkg)) {
                        expected.put(mixin, ModList.get().isLoaded(mod));
                        actual.put(mixin, plugin.shouldApplyMixin("", root + mixin));
                    }
                });
            }
        }
        ctx.record("联动 mixin", expected);
        ctx.check(expected.keySet()).as("配置里的联动 mixin").isEqualTo(new TreeSet<>(List.of(
                "callresponse.CallResponseCageMixin", "explorerscompass.StructureUtilsMixin", "ftbquests.DimensionTaskMixin",
                "ftbquests.KnownServerRegistriesMixin", "legendary.SmallAnnihilationBombEntityMixin")));
        ctx.check(actual).as("插件是否应用（期望与模组是否在场一致）").isEqualTo(expected);
    }

    private record Row(String result, int emeralds) {
    }

    private static List<Row> rows(MerchantOffers offers) {
        List<Row> rows = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            int price = offer.getBaseCostA().is(Items.EMERALD) ? offer.getBaseCostA().getCount() : -1;
            if (result.is(Items.ENCHANTED_BOOK)) {
                ItemEnchantments stored = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                stored.entrySet().forEach(entry -> rows.add(new Row("book " + entry.getKey().getRegisteredName()
                        + " " + entry.getIntValue(), price)));
            } else if (ISpellContainer.isSpellContainer(result) && result.is(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("irons_spellbooks:scroll")))) {
                for (SpellSlot slot : ISpellContainer.get(result).getAllSpells()) {
                    rows.add(new Row("scroll " + slot.getSpell().getSpellResource() + " " + slot.getLevel(), price));
                }
            }
        }
        return rows;
    }

    /** 补给里有经验修补 I 与保护 IV 附魔书（战胜前 5/4 绿宝石，战胜后半价）；战胜后多出 1 级破法回响与七星引路卷轴，各 10 绿宝石。 */
    private static void trades(SceneContext ctx) {
        List<Row> locked = rows(WinefoxTrades.build(false));
        List<Row> unlocked = rows(WinefoxTrades.build(true));
        ctx.record("战胜前", locked);
        ctx.record("战胜后", unlocked);
        ctx.check(locked).as("战胜前的附魔书与卷轴").isEqualTo(List.of(
                new Row("book minecraft:mending 1", 5), new Row("book minecraft:protection 4", 4)));
        for (Row row : List.of(new Row("book minecraft:mending 1", 3), new Row("book minecraft:protection 4", 2),
                new Row("scroll " + NS + "spellbreaking_echo 1", 10), new Row("scroll " + NS + "seven_star_guidance 1", 10))) {
            ctx.check(unlocked.contains(row)).as("战胜后有 " + row).isTrue();
        }
    }

    /** 法术射表注册为史诗稀有度、在饰品页里，祭坛配方为书、纸 ×3、箭、青金石，耗 0.25 灵力。 */
    private static void rangeTableItem(SceneContext ctx) {
        ItemStack table = Actors.stack(NS + "spell_range_table");
        ctx.check(table.getRarity()).as("稀有度").isEqualTo(Rarity.EPIC);
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, ctx.server().registryAccess());
        ctx.check(MaidSpellCreativeTab.MAID_SPELL_BAUBLE_TAB.get().getDisplayItems().stream()
                .anyMatch(stack -> stack.is(table.getItem()))).as("饰品页有法术射表").isTrue();
        RecipeHolder<?> recipe = ctx.server().getRecipeManager().byKey(ResourceLocation.parse(NS + "altar/spell_range_table")).orElse(null);
        if (recipe == null || !(recipe.value() instanceof AltarRecipe altar)) {
            ctx.fail("没有法术射表的祭坛配方");
            return;
        }
        ctx.check(altar.getResultItem(ctx.server().registryAccess()).is(table.getItem())).as("配方产出法术射表").isTrue();
        ctx.check((double) altar.getPower()).as("灵力消耗").isCloseTo(0.25, 1.0E-6);
        List<String> ingredients = new ArrayList<>();
        altar.getIngredients().forEach(ingredient -> {
            ItemStack[] items = ingredient.getItems();
            ingredients.add(items.length == 0 ? "?" : BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString());
        });
        ingredients.sort(null);
        ctx.check(ingredients).as("配方材料").isEqualTo(List.of("minecraft:arrow", "minecraft:book", "minecraft:lapis_lazuli",
                "minecraft:paper", "minecraft:paper", "minecraft:paper"));
    }

    /** 坐下或骑乘时只有戴着射表才允许施法；站着的女仆不走这条判定。 */
    private static void ridePoseGate(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 1, 0);
        ctx.check(TaskTestAccess.ridePoseSpellcastingEnabled(maid)).as("站着、不戴射表").isFalse();
        Actors.equipBauble(maid, NS + "spell_range_table");
        ctx.check(TaskTestAccess.ridePoseSpellcastingEnabled(maid)).as("站着、戴射表").isFalse();
        maid.setInSittingPose(true);
        ctx.check(TaskTestAccess.ridePoseSpellcastingEnabled(maid)).as("坐下、戴射表").isTrue();
        Maids.takeOff(maid, 0);
        ctx.check(TaskTestAccess.ridePoseSpellcastingEnabled(maid)).as("坐下、不戴射表").isFalse();
        maid.setInSittingPose(false);
        Boat boat = EntityType.BOAT.create(ctx.level());
        if (boat == null) {
            ctx.fail("造不出船");
            return;
        }
        boat.moveTo(ctx.rel(2, 1, 0).getBottomCenter());
        ctx.level().addFreshEntity(boat);
        ctx.cleanup(boat::discard);
        ctx.check(maid.startRiding(boat, true)).as("女仆坐上船").isTrue();
        ctx.check(TaskTestAccess.ridePoseSpellcastingEnabled(maid)).as("骑乘、不戴射表").isFalse();
        Actors.equipBauble(maid, NS + "spell_range_table");
        ctx.check(TaskTestAccess.ridePoseSpellcastingEnabled(maid)).as("骑乘、戴射表").isTrue();
        maid.stopRiding();
    }

    /** 5 个新结构的名称和「归隐之地」「独立归隐之地」在中英日三份语言文件里都有。 */
    private static void structureNames(SceneContext ctx) {
        Map<String, String> zh = Lang.read("zh_cn");
        List<String> keys = new ArrayList<>();
        NEW_STRUCTURES.forEach(id -> keys.add("structure." + MaidSpellMod.MOD_ID + "." + id));
        keys.add("dimension." + MaidSpellMod.MOD_ID + ".the_retreat");
        for (String code : List.of("zh_cn", "en_us", "ja_jp")) {
            Map<String, String> lang = Lang.read(code);
            ctx.check(keys.stream().filter(key -> lang.getOrDefault(key, "").isBlank()).toList()).as(code + " 缺少的名称").isEmpty();
        }
        ctx.check(zh.get("dimension." + MaidSpellMod.MOD_ID + ".the_retreat")).as("维度中文名").isEqualTo("归隐之地");
        ctx.check(zh.values().stream().anyMatch(value -> value.contains("独立归隐之地"))).as("中文里有「独立归隐之地」").isTrue();
        ctx.check(zh.get("structure." + MaidSpellMod.MOD_ID + ".cherry_cabin")).as("落樱厨舍").isEqualTo("落樱厨舍");
    }

    /** 梦云水晶 desc5 只说女仆的栏位 +1，三种语言都不再提玩家佩戴。 */
    private static void dreamCrystalDesc5(SceneContext ctx) {
        String key = "item." + MaidSpellMod.MOD_ID + ".dream_cat_crystal.desc5_base";
        ctx.check(Lang.read("zh_cn").get(key)).as("中文").isEqualTo("取消法术冷却；安装 Curios 时女仆的每种栏位类型 +1");
        ctx.check(Lang.read("en_us").get(key)).as("英文")
                .isEqualTo("Removes spell cooldowns; with Curios installed, grants maids +1 to every slot type");
        String ja = Lang.read("ja_jp").getOrDefault(key, "");
        ctx.check(ja.isBlank() || ja.contains("プレイヤー")).as("日文 " + ja + " 不提玩家").isFalse();
    }

    private static String templateOf(Holder<StructureTemplatePool> pool) {
        return pool.value().getShuffledTemplates(RandomSource.create(0)).stream()
                .filter(SinglePoolElement.class::isInstance).map(SinglePoolElement.class::cast)
                .map(Worldgen::templateId).findFirst().orElse("");
    }

    /**
     * 咖啡厅默认起始池用妖怪们的归家移植版模板；没装妖怪们的归家时改用 Youkais' Feasts 版，
     * 装的是没有 danmaku_api 的官方版时改用妖怪们的归家版；实际选用的模板与当前模组组合一致。
     */
    private static void cafeFallback(SceneContext ctx) {
        Structure structure = ctx.server().registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.parse(NS + "fairy_maid_cafe"));
        if (!(structure instanceof LandJigsawStructure cafe)) {
            ctx.fail("咖啡厅没有注册为 land_jigsaw 结构（妖怪们的归家或 Youkais' Feasts 在场时应注册）");
            return;
        }
        Holder<StructureTemplatePool> start = Reflect.field(cafe, LandJigsawStructure.class, "startPool");
        List<LandJigsawStructure.PoolFallback> fallback = Reflect.field(cafe, LandJigsawStructure.class, "fallback");
        ctx.check(templateOf(start)).as("默认起始模板").isEqualTo(NS + "fairy_maid_cafe/fairy_maid_cafe_yh_port");
        ctx.check(fallback.stream().map(f -> f.mod() + " -> " + templateOf(f.startPool())).toList()).as("回退池（按顺序）")
                .isEqualTo(List.of("youkaishomecoming -> " + NS + "fairy_maid_cafe/fairy_maid_cafe_youkaisfeasts",
                        "danmaku_api -> " + NS + "fairy_maid_cafe/fairy_maid_cafe"));
        String expected = !ModList.get().isLoaded("youkaishomecoming") ? "fairy_maid_cafe_youkaisfeasts"
                : !ModList.get().isLoaded("danmaku_api") ? "fairy_maid_cafe" : "fairy_maid_cafe_yh_port";
        ctx.check(templateOf(cafe.startPool())).as("当前模组组合选用的模板").isEqualTo(NS + "fairy_maid_cafe/" + expected);
    }

    /** 梦云水晶效果白名单默认放行妖怪们的归家和 Youkais' Feasts 的效果。 */
    private static void whitelist(SceneContext ctx) {
        Class<?> baubles;
        try {
            baubles = Class.forName("com.github.yimeng261.maidspell.ConfigBaubles");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到饰品配置类", e);
        }
        List<String> defaults = Reflect.field(null, baubles, "DEFAULT_WHITELIST");
        ctx.check(defaults.contains("regex:youkaishomecoming:.*")).as("默认白名单含妖怪们的归家").isTrue();
        ctx.check(defaults.contains("regex:youkaisfeasts:.*")).as("默认白名单含 Youkais' Feasts").isTrue();
    }
}
