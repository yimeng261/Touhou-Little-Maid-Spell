package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.datafixers.util.Either;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 观星术士、精灵圣卫、星之魔女这几个 NPC：交易互斥与放开、观星术士的报价与中立、星之魔女的属性/床/命令/保底，
 * 以及星辉花簇和改用星云核心的梦云水晶祭坛配方。
 */
public final class NpcScenes {
    private static final String ASTRO = NS + "astro_mancer";
    private static final String TEMPLAR = NS + "elf_templar";
    private static final String WITCH = NS + "stellar_witch";

    private NpcScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        for (String npc : List.of(ASTRO, TEMPLAR)) {
            String name = ResourceLocation.parse(npc).getPath();
            scenes.add(Checks.superflat("trade." + name + ".secondPlayerCannotOpen", 20, ctx -> secondPlayer(ctx, npc)));
            scenes.add(Checks.superflat("trade." + name + ".releasedWhenTraderLeaves", 40, ctx -> traderLeaves(ctx, npc)));
            scenes.add(Checks.superflat("trade." + name + ".menuInvalidWhenNpcGone", 20, ctx -> npcGone(ctx, npc)));
        }
        scenes.add(Checks.superflat("trade.villagerUnaffected", 20, NpcScenes::villager));
        scenes.add(Checks.superflat("astro_mancer.offers", 20, NpcScenes::astroOffers));
        scenes.add(Checks.superflat("astro_mancer.neutralAndRetaliates", 120, NpcScenes::astroNeutral));
        scenes.add(Checks.superflat("astro_mancer.dropsFromTableOnly", 60,
                ctx -> LootScenes.checkKillDrops(ctx, ASTRO, NS + "entities/astro_mancer")));
        scenes.add(Checks.superflat("stellar_witch.hatAddsNoArmor", 10, NpcScenes::witchHat));
        scenes.add(Checks.superflat("stellar_witch.bedUnsafeNearby", 10, NpcScenes::witchBed));
        scenes.add(Checks.superflat("stellar_witch.survivesKill", 40, NpcScenes::witchSurvivesKill));
        scenes.add(Checks.scene("stellar_witch.lifecycleCommandNeedsOp", 5, NpcScenes::witchCommand));
        scenes.add(Checks.superflat("star_glow_flower_cluster.behavesLikePetals", 10, NpcScenes::flowerCluster));
        scenes.add(Checks.scene("dream_crystal.altarRecipesUseNebulaCore", 5, NpcScenes::altarRecipes));
        return scenes;
    }

    private static Mob npc(SceneContext ctx, String type) {
        Mob mob = Actors.spawn(ctx, type, 0, 0, 0, true);
        mob.setPersistenceRequired();
        return mob;
    }

    private static boolean trading(Player player) {
        return player.containerMenu instanceof MerchantMenu;
    }

    private static void secondPlayer(SceneContext ctx, String type) {
        Mob npc = npc(ctx, type);
        ServerPlayer a = Owners.visitor(ctx, "TlmsTraderA", 2, 0, 0);
        ServerPlayer b = Owners.visitor(ctx, "TlmsTraderB", -2, 0, 0);
        a.interactOn(npc, InteractionHand.MAIN_HAND);
        ctx.check(trading(a)).as("A 打开交易").isTrue();
        b.interactOn(npc, InteractionHand.MAIN_HAND);
        ctx.check(trading(b)).as("B 在 A 交易时打开交易").isFalse();
        ctx.check(((Merchant) npc).getTradingPlayer() == a).as("交易对象仍是 A").isTrue();
        ctx.check(a.containerMenu.stillValid(a)).as("A 的交易界面仍有效").isTrue();
    }

    private static void traderLeaves(SceneContext ctx, String type) {
        Mob npc = npc(ctx, type);
        ServerPlayer a = Owners.visitor(ctx, "TlmsTraderA", 2, 0, 0);
        ServerPlayer b = Owners.visitor(ctx, "TlmsTraderB", -2, 0, 0);
        a.interactOn(npc, InteractionHand.MAIN_HAND);
        ctx.check(trading(a)).as("A 打开交易").isTrue();
        var opened = a.containerMenu;
        a.moveTo(a.getX() + 12, a.getY(), a.getZ(), a.getYRot(), a.getXRot());
        ctx.await(() -> ((Merchant) npc).getTradingPlayer() == null).within(20).then(() -> {
            ctx.check(opened.stillValid(a)).as("走远后 A 的交易界面仍有效").isFalse();
            b.interactOn(npc, InteractionHand.MAIN_HAND);
            ctx.check(trading(b)).as("A 走远后 B 打开交易").isTrue();
        });
    }

    private static void npcGone(SceneContext ctx, String type) {
        Mob npc = npc(ctx, type);
        ServerPlayer a = Owners.visitor(ctx, "TlmsTraderA", 2, 0, 0);
        a.interactOn(npc, InteractionHand.MAIN_HAND);
        ctx.check(trading(a)).as("A 打开交易").isTrue();
        npc.discard();
        ctx.check(a.containerMenu.stillValid(a)).as("NPC 消失后 A 的交易界面仍有效").isFalse();
    }

    private static void villager(SceneContext ctx) {
        Villager villager = Actors.spawn(ctx, "minecraft:villager", 0, 0, 0, true);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 2));
        ServerPlayer a = Owners.visitor(ctx, "TlmsTraderA", 2, 0, 0);
        ServerPlayer b = Owners.visitor(ctx, "TlmsTraderB", -2, 0, 0);
        a.interactOn(villager, InteractionHand.MAIN_HAND);
        ctx.check(trading(a)).as("A 与村民交易").isTrue();
        a.closeContainer();
        b.interactOn(villager, InteractionHand.MAIN_HAND);
        ctx.check(trading(b)).as("A 关掉后 B 与村民交易").isTrue();
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 收购墨水、奥术精华和食物；出售星锚珍珠（16 绿宝石，每次补货 1 件）等；星陨石只卖一次；价格不随需求上涨。 */
    private static void astroOffers(SceneContext ctx) {
        Mob npc = npc(ctx, ASTRO);
        Merchant merchant = (Merchant) npc;
        List<MerchantOffer> offers = merchant.getOffers();
        Set<String> bought = new TreeSet<>();
        Map<String, String> sold = new TreeMap<>();
        for (MerchantOffer offer : offers) {
            if (id(offer.getResult()).equals("minecraft:emerald")) {
                bought.add(id(offer.getBaseCostA()));
            } else {
                sold.put(id(offer.getResult()), offer.getBaseCostA().getCount() + "×" + id(offer.getBaseCostA())
                        + (offer.getCostB().isEmpty() ? "" : " + " + offer.getCostB().getCount() + "×" + id(offer.getCostB()))
                        + " 限 " + offer.getMaxUses());
            }
        }
        ctx.record("sold", sold);
        for (String item : List.of("irons_spellbooks:common_ink", "irons_spellbooks:uncommon_ink", "irons_spellbooks:rare_ink",
                "irons_spellbooks:arcane_essence", "minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop",
                "minecraft:cooked_chicken", "minecraft:baked_potato", "minecraft:apple")) {
            ctx.check(bought.contains(item)).as("收购 " + item).isTrue();
        }
        ctx.check(sold.get(NS + "staranchor_pearl")).as("星锚珍珠报价").isEqualTo("16×minecraft:emerald 限 1");
        ctx.check(sold.get(NS + "star_meteorite")).as("星陨石报价").isEqualTo("64×minecraft:emerald + 1×minecraft:nether_star 限 1");
        for (String item : List.of("irons_spellbooks:epic_ink", "irons_spellbooks:ender_rune", "irons_spellbooks:mithril_scrap",
                "minecraft:ender_pearl", "irons_spellbooks:scroll")) {
            ctx.check(sold.containsKey(item)).as("出售 " + item).isTrue();
        }
        MerchantOffer first = offers.getFirst();
        int price = first.getCostA().getCount();
        for (int i = 0; i < 4; i++) {
            first.increaseUses();
        }
        first.updateDemand();
        ctx.check(first.getCostA().getCount()).as("卖了 4 次后同一条的价格").isEqualTo(price);
    }

    /** 不主动打路过的玩家；被打后反击，旁边的同伴不会互相打起来。 */
    private static void astroNeutral(SceneContext ctx) {
        Mob a = Actors.spawn(ctx, ASTRO, 0, 0, 0, false);
        Mob b = Actors.spawn(ctx, ASTRO, 0, 0, 3, false);
        ServerPlayer player = Owners.visitor(ctx, "TlmsStargazer", 4, 0, 0);
        boolean[] targeted = {false};
        Checks.watch(ctx, 60, () -> targeted[0] |= a.getTarget() == player || b.getTarget() == player, () -> {
            ctx.check(targeted[0]).as("观星术士主动以路过的玩家为目标").isFalse();
            a.hurt(a.damageSources().playerAttack(player), 1);
            b.hurt(b.damageSources().indirectMagic(a, a), 1);
            ctx.await(() -> a.getTarget() == player).within(40).then(() ->
                    Checks.after(ctx, 20, () -> {
                        ctx.check(a.getTarget() == b || b.getTarget() == a).as("两只观星术士互相为目标").isFalse();
                    }));
        });
    }

    /** 她戴的法帽不加护甲和韧性。 */
    private static void witchHat(SceneContext ctx) {
        Mob witch = npc(ctx, WITCH);
        ctx.record("head", id(witch.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)));
        ctx.check(witch.getAttributeValue(Attributes.ARMOR)).as("护甲（与基础值对照）").isCloseTo(witch.getAttributeBaseValue(Attributes.ARMOR), 1e-6);
        ctx.check(witch.getAttributeValue(Attributes.ARMOR_TOUGHNESS)).as("韧性（与基础值对照）")
                .isCloseTo(witch.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS), 1e-6);
    }

    /** 她附近 8 格内的床：生存玩家白天晚上都提示不安全、重生点不变；创造模式不受她影响。 */
    private static void witchBed(SceneContext ctx) {
        npc(ctx, WITCH);
        ServerLevel level = ctx.level();
        BlockPos foot = ctx.rel(3, 0, 0);
        BlockPos head = foot.relative(Direction.EAST);
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
        level.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
        ServerPlayer player = Owners.visitor(ctx, "TlmsSleeper", 3, 0, 1);
        long time = level.getDayTime();
        ctx.cleanup(() -> level.setDayTime(time));
        Map<String, String> results = new TreeMap<>();
        for (long day : List.of(6000L, 18000L)) {
            level.setDayTime(day);
            Either<Player.BedSleepingProblem, Unit> result = player.startSleepInBed(head);
            results.put("生存 " + day, result.left().map(Enum::name).orElse("睡下"));
            player.stopSleeping();
        }
        ctx.check(player.getRespawnPosition()).as("重生点").isNull();
        player.setGameMode(GameType.CREATIVE);
        level.setDayTime(18000L);
        Either<Player.BedSleepingProblem, Unit> creative = player.startSleepInBed(head);
        results.put("创造 18000", creative.left().map(Enum::name).orElse("睡下"));
        player.stopSleeping();
        player.setGameMode(GameType.SURVIVAL);
        ctx.check(results).as("上床结果").isEqualTo(Map.of("生存 6000", "NOT_SAFE", "生存 18000", "NOT_SAFE", "创造 18000", "睡下"));
    }

    /** /kill 与掉出世界的伤害都不让她死亡。 */
    private static void witchSurvivesKill(SceneContext ctx) {
        Mob witch = npc(ctx, WITCH);
        witch.kill();
        witch.hurt(witch.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        Checks.after(ctx, 20, () -> {
            ctx.check(witch.isRemoved()).as("被移除").isFalse();
            ctx.check(witch.isDeadOrDying()).as("死亡").isFalse();
        });
    }

    private static void witchCommand(SceneContext ctx) {
        CommandNode<CommandSourceStack> root = ctx.server().getCommands().getDispatcher().getRoot().getChild("maidspell_winefox");
        if (root == null) {
            ctx.fail("没有 maidspell_winefox 命令");
            return;
        }
        CommandSourceStack op = ctx.server().createCommandSourceStack();
        ctx.check(root.canUse(op.withPermission(2))).as("OP 能用").isTrue();
        ctx.check(root.canUse(op.withPermission(0))).as("非 OP 能用").isFalse();
        Set<String> actions = new TreeSet<>();
        root.getChildren().forEach(target -> target.getChildren().forEach(action -> actions.add(action.getName())));
        ctx.check(actions).as("子命令").isEqualTo(new TreeSet<>(List.of("status", "return", "repair", "reward_confirm", "reward_reissue", "destroy")));
    }

    /** 最多 4 朵，破坏时按朵数掉落，属于花。 */
    private static void flowerCluster(SceneContext ctx) {
        ServerLevel level = ctx.level();
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(NS + "star_glow_flower_cluster"));
        BlockState full = block.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 4);
        ctx.check(PinkPetalsBlock.AMOUNT.getPossibleValues().stream().max(Integer::compare).orElse(0)).as("最多朵数").isEqualTo(4);
        ctx.check(full.is(BlockTags.FLOWERS)).as("方块属于 #minecraft:flowers").isTrue();
        ctx.check(new ItemStack(block).is(ItemTags.FLOWERS)).as("物品属于 #minecraft:flowers").isTrue();
        BlockPos pos = ctx.rel(0, 0, 0);
        level.setBlock(pos, full, Block.UPDATE_ALL);
        level.destroyBlock(pos, true);
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(block.asItem())).mapToInt(e -> e.getItem().getCount()).sum();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(Entity::discard);
        ctx.check(dropped).as("4 朵破坏后的掉落").isEqualTo(4);
    }

    private static void altarRecipes(SceneContext ctx) {
        ItemStack core = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(NS + "nebula_core")));
        for (String id : List.of("altar/dream_cat_crystal_accessories", "altar/dream_cat_crystal_crossover")) {
            RecipeHolder<?> recipe = ctx.server().getRecipeManager().byKey(ResourceLocation.parse(NS + id)).orElse(null);
            if (recipe == null) {
                ctx.fail("没有祭坛配方 " + id);
                continue;
            }
            ctx.check(recipe.value() instanceof AltarRecipe).as(id + " 是车万女仆祭坛配方").isTrue();
            ctx.check(recipe.value().getIngredients().stream().anyMatch(ing -> ing.test(core))).as(id + " 用星云核心").isTrue();
        }
    }
}
