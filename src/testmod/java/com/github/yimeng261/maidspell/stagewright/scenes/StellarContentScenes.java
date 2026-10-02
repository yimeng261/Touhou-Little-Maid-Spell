package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.effect.MaidSpellEffects;
import com.github.yimeng261.maidspell.item.MaidSpellCreativeTab;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女系列内容（需要铁魔法）：9 个法术的获取限制与文案、创造物品栏两页、长剑/法杖/法帽/投枪的附魔与属性、
 * 星锚珍珠、归星配方、投枪投掷。
 */
public final class StellarContentScenes {
    private static final List<String> EXCLUSIVE_SPELLS = List.of("starfall_modified", "magic_shotgun", "void_phase",
            "sword_prison", "teleport_modified", "star_shadow_strike", "triple_star_arrow");
    private static final List<String> OBTAINABLE_SPELLS = List.of("companion_black_hole", "spellbreaking_echo");
    private static final String SPEAR = NS + "star_shadow_spear";

    private StellarContentScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("stellar.spells.exclusiveNotCraftableOrLootable", 5, StellarContentScenes::exclusiveSpells));
        scenes.add(Checks.scene("stellar.spells.companionSpellsCraftable", 5, StellarContentScenes::obtainableSpells));
        scenes.add(Checks.scene("stellar.spells.namesDescriptionsIcons", 5, StellarContentScenes::spellTexts));
        scenes.add(Checks.scene("stellar.creativeTabs", 5, StellarContentScenes::creativeTabs));
        scenes.add(Checks.scene("stellar.equipment.enchantability", 5, StellarContentScenes::enchantability));
        scenes.add(Checks.scene("stellar.equipment.longswordAndHat", 5, StellarContentScenes::longswordAndHat));
        scenes.add(Checks.scene("stellar.returningStarRecipe", 5, StellarContentScenes::returningStarRecipe));
        scenes.add(Checks.superflat("stellar.staranchor_pearl.playerUse", 20, StellarContentScenes::pearlPlayer));
        scenes.add(Checks.superflat("stellar.staranchor_pearl.maidBlocksVoid", 20, StellarContentScenes::pearlMaid));
        scenes.add(Checks.superflat("stellar.spear.throwSurvivalAndCreative", 20, StellarContentScenes::spearThrow));
        return scenes;
    }

    private static AbstractSpell spell(String path) {
        return SpellRegistry.getSpell(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, path));
    }

    private static void exclusiveSpells(SceneContext ctx) {
        for (String id : EXCLUSIVE_SPELLS) {
            AbstractSpell spell = spell(id);
            ctx.check(spell.getSpellName()).as(id + " 已注册").isEqualTo(id);
            ctx.check(spell.allowCrafting()).as(id + " 能在抄写台合成").isFalse();
            ctx.check(spell.allowLooting()).as(id + " 会出现在战利品卷轴里").isFalse();
        }
    }

    private static void obtainableSpells(SceneContext ctx) {
        for (String id : OBTAINABLE_SPELLS) {
            ctx.check(spell(id).allowCrafting()).as(id + " 能在抄写台合成").isTrue();
        }
    }

    /** 名称（spell.&lt;ns&gt;.&lt;id&gt;）与说明（.guide）两种语言都有，图标贴图存在。 */
    private static void spellTexts(SceneContext ctx) {
        Map<String, String> en = Lang.read("en_us");
        Map<String, String> zh = Lang.read("zh_cn");
        List<String> missing = new ArrayList<>();
        for (String id : concat(EXCLUSIVE_SPELLS, OBTAINABLE_SPELLS)) {
            AbstractSpell spell = spell(id);
            for (String key : List.of(spell.getComponentId(), spell.getComponentId() + ".guide")) {
                if (!en.containsKey(key)) {
                    missing.add("en_us " + key);
                }
                if (!zh.containsKey(key)) {
                    missing.add("zh_cn " + key);
                }
            }
            ResourceLocation icon = spell.getSpellIconResource();
            if (StellarContentScenes.class.getResource("/assets/" + icon.getNamespace() + "/" + icon.getPath()) == null) {
                missing.add("图标 " + icon);
            }
        }
        ctx.check(missing).as("缺少的法术文案与图标").isEmpty();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static Set<String> tabItems(SceneContext ctx, CreativeModeTab tab) {
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, ctx.server().registryAccess());
        Set<String> out = new TreeSet<>();
        tab.getDisplayItems().forEach(stack -> out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        return out;
    }

    /** 饰品页有星锚珍珠；杂项页有装备、材料、寻风之铃、花草、压制石和管理员工具，且排在饰品页之后。 */
    private static void creativeTabs(SceneContext ctx) {
        Set<String> baubles = tabItems(ctx, MaidSpellCreativeTab.MAID_SPELL_BAUBLE_TAB.get());
        Set<String> misc = tabItems(ctx, MaidSpellCreativeTab.MAID_SPELL_MISC_TAB.get());
        ctx.check(baubles.contains(NS + "staranchor_pearl")).as("饰品页有星锚珍珠").isTrue();
        List<String> missing = new ArrayList<>();
        for (String id : List.of("star_shadow_longsword", "star_shadow_staff", "star_witch_hat", "starglint_dagger",
                "nebula_core", "star_meteorite", "ritual_hilt", "returning_star", "wind_seeking_bell", "scarlet_zhuhua",
                "yue_linglan", "jingxu_youlan", "star_glow_flower_cluster", "suppression_stone", "owner_clear_tool")) {
            if (!misc.contains(NS + id)) {
                missing.add(id);
            }
        }
        ctx.check(missing).as("杂项页缺少的物品").isEmpty();
        List<CreativeModeTab> tabs = CreativeModeTabs.allTabs();
        ctx.check(tabs.indexOf(MaidSpellCreativeTab.MAID_SPELL_MISC_TAB.get()))
                .as("杂项页的位置（饰品页在 " + tabs.indexOf(MaidSpellCreativeTab.MAID_SPELL_BAUBLE_TAB.get()) + "）")
                .isGreaterThan(tabs.indexOf(MaidSpellCreativeTab.MAID_SPELL_BAUBLE_TAB.get()));
    }

    private static Holder<Enchantment> enchantment(SceneContext ctx, ResourceKey<Enchantment> key) {
        return ctx.server().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    /** 附魔台/铁砧上能附的附魔：长剑剑类、法帽头盔类、法杖可附耐久、经验修补及剑类附魔、投枪三叉戟类。 */
    private static void enchantability(SceneContext ctx) {
        Map<String, Boolean> actual = new TreeMap<>();
        Map<String, Boolean> expected = new TreeMap<>();
        record Case(String item, ResourceKey<Enchantment> enchantment, boolean allowed) {
        }
        List<Case> cases = List.of(
                new Case("star_shadow_longsword", Enchantments.SHARPNESS, true),
                new Case("star_shadow_longsword", Enchantments.LOOTING, true),
                new Case("star_witch_hat", Enchantments.PROTECTION, true),
                new Case("star_witch_hat", Enchantments.RESPIRATION, true),
                new Case("star_witch_hat", Enchantments.SHARPNESS, false),
                new Case("star_shadow_staff", Enchantments.UNBREAKING, true),
                new Case("star_shadow_staff", Enchantments.MENDING, true),
                new Case("star_shadow_staff", Enchantments.SHARPNESS, true),
                new Case("star_shadow_spear", Enchantments.LOYALTY, true),
                new Case("star_shadow_spear", Enchantments.CHANNELING, true),
                new Case("star_shadow_spear", Enchantments.IMPALING, true),
                new Case("star_shadow_spear", Enchantments.RIPTIDE, true),
                new Case("star_shadow_spear", Enchantments.UNBREAKING, true),
                new Case("star_shadow_spear", Enchantments.MENDING, true));
        for (Case c : cases) {
            ItemStack stack = Actors.stack(NS + c.item());
            String name = c.item() + " + " + c.enchantment().location().getPath();
            actual.put(name, enchantment(ctx, c.enchantment()).value().canEnchant(stack));
            expected.put(name, c.allowed());
        }
        ctx.check(actual).as("能否附魔").isEqualTo(expected);
    }

    /** 长剑自带虚空相变、可用紫水晶修复；法帽加法力上限、法术强度、冷却缩减；投枪伤害与三叉戟相同。 */
    private static void longswordAndHat(SceneContext ctx) {
        ItemStack sword = Actors.stack(NS + "star_shadow_longsword");
        List<String> spells = new ArrayList<>();
        if (ISpellContainer.isSpellContainer(sword)) {
            for (SpellSlot slot : ISpellContainer.get(sword).getAllSpells()) {
                spells.add(slot.getSpell().getSpellResource().toString());
            }
        }
        ctx.check(spells.contains(NS + "void_phase")).as("长剑自带的法术 " + spells + " 含虚空相变").isTrue();
        ctx.check(sword.getItem().isValidRepairItem(sword, new ItemStack(Items.AMETHYST_SHARD))).as("紫水晶能修长剑").isTrue();
        Set<String> hat = new TreeSet<>();
        Actors.stack(NS + "star_witch_hat").getAttributeModifiers().modifiers()
                .forEach(entry -> hat.add(entry.attribute().getRegisteredName()));
        for (String attribute : List.of("irons_spellbooks:max_mana", "irons_spellbooks:spell_power", "irons_spellbooks:cooldown_reduction")) {
            ctx.check(hat.contains(attribute)).as("法帽属性 " + hat + " 含 " + attribute).isTrue();
        }
        ctx.check(attackDamage(Actors.stack(SPEAR))).as("投枪的攻击伤害加成（与三叉戟对照）")
                .isEqualTo(attackDamage(new ItemStack(Items.TRIDENT)));
    }

    private static List<Double> attackDamage(ItemStack stack) {
        List<Double> out = new ArrayList<>();
        ItemAttributeModifiers modifiers = stack.getAttributeModifiers();
        modifiers.modifiers().stream()
                .filter(e -> e.attribute().is(Attributes.ATTACK_DAMAGE) && e.slot() == EquipmentSlotGroup.MAINHAND)
                .forEach(e -> out.add(e.modifier().amount()));
        return out;
    }

    private static void returningStarRecipe(SceneContext ctx) {
        ItemStack mithril = Actors.stack("irons_spellbooks:mithril_ingot");
        ItemStack meteorite = Actors.stack(NS + "star_meteorite");
        ItemStack e = ItemStack.EMPTY;
        CraftingInput input = CraftingInput.of(3, 3, List.of(e, mithril, e, mithril, meteorite, mithril, e, mithril, e));
        ServerLevel level = ctx.level();
        String result = ctx.server().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(r -> BuiltInRegistries.ITEM.getKey(r.value().assemble(input, level.registryAccess()).getItem()).toString())
                .orElse("<无配方>");
        ctx.check(result).as("四个秘银锭围着星陨石合成的结果").isEqualTo(NS + "returning_star");
    }

    /** 玩家右键：耗 8 点耐久、获得 30 秒虚空漫步，期间虚空伤害无效；耐久见底时碎掉；不可修复。 */
    private static void pearlPlayer(SceneContext ctx) {
        ServerPlayer player = Owners.visitor(ctx, "TlmsPearlUser", 0, 0, 0);
        ItemStack pearl = Actors.stack(NS + "staranchor_pearl");
        player.setItemInHand(InteractionHand.MAIN_HAND, pearl);
        pearl.getItem().use(ctx.level(), player, InteractionHand.MAIN_HAND);
        ctx.check(pearl.getDamageValue()).as("用一次后的耐久损耗").isEqualTo(8);
        ctx.check(player.hasEffect(MaidSpellEffects.VOID_WALK)).as("获得虚空漫步").isTrue();
        ctx.check(player.getEffect(MaidSpellEffects.VOID_WALK).getDuration()).as("虚空漫步时长").isBetween(590, 600);
        float before = player.getHealth();
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().fellOutOfWorld(), 4);
        ctx.check(player.getHealth()).as("虚空漫步期间受虚空伤害后的生命").isCloseTo(before, 1e-3f);
        ctx.check(pearl.isRepairable()).as("星锚珍珠可修复").isFalse();
        int damage = pearl.getDamageValue();
        pearl.getItem().use(ctx.level(), player, InteractionHand.MAIN_HAND);
        ctx.check(pearl.getDamageValue()).as("虚空漫步仍在时重复使用不消耗耐久").isEqualTo(damage);
        player.removeEffect(MaidSpellEffects.VOID_WALK);
        pearl.setDamageValue(pearl.getMaxDamage() - 4);
        pearl.getItem().use(ctx.level(), player, InteractionHand.MAIN_HAND);
        ctx.check(player.getMainHandItem().isEmpty()).as("耐久耗尽后珍珠碎掉").isTrue();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void pearlMaid(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        ItemStack pearl = Actors.equipBauble(maid, NS + "staranchor_pearl");
        Checks.after(ctx, 2, () -> {
            float before = maid.getHealth();
            maid.invulnerableTime = 0;
            maid.hurt(maid.damageSources().fellOutOfWorld(), 4);
            ctx.check(maid.getHealth()).as("女仆受虚空伤害后的生命").isCloseTo(before, 1e-3f);
            ctx.check(maid.getMaidBauble().getStackInSlot(0).getDamageValue()).as("挡一次后的耐久损耗").isEqualTo(1);
        });
    }

    /** 生存模式投出：手上的投枪用掉，实体可捡回且捡回的是星影投枪；创造模式投出：手上不少，实体只许创造模式捡。 */
    private static void spearThrow(SceneContext ctx) {
        ServerLevel level = ctx.level();
        ServerPlayer player = Owners.visitor(ctx, "TlmsSpearThrower", 0, 0, 0);
        ctx.check(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(SPEAR)).create(level) != null).as("能召唤投枪实体").isTrue();
        Map<String, String> actual = new TreeMap<>();
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            player.setGameMode(mode);
            ItemStack spear = Actors.stack(SPEAR);
            player.setItemInHand(InteractionHand.MAIN_HAND, spear);
            spear.getItem().releaseUsing(spear, level, player, spear.getUseDuration(player) - 20);
            AbstractArrow thrown = level.getEntitiesOfClass(AbstractArrow.class, new AABB(player.blockPosition()).inflate(4),
                    e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(SPEAR)).stream().findFirst().orElse(null);
            if (thrown == null) {
                actual.put(mode.getName(), "没投出");
                continue;
            }
            CompoundTag saved = thrown.saveWithoutId(new CompoundTag());
            actual.put(mode.getName(), "手上剩 " + player.getMainHandItem().getCount() + "，拾取 " + thrown.pickup
                    + "，捡回 " + saved.getCompound("item").getString("id"));
            thrown.discard();
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ctx.check(actual).as("投出后的状态").isEqualTo(Map.of(
                "survival", "手上剩 0，拾取 ALLOWED，捡回 " + SPEAR,
                "creative", "手上剩 1，拾取 CREATIVE_ONLY，捡回 " + SPEAR));
    }
}
