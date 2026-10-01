package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.StructureStage;
import com.github.yimeng261.maidspell.stagewright.support.WorldExtract;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星途终岸、观星塔、星落之庭相关：结构放置、观星术士名字、四幅新画、星之旅程与万法皆通两个成就页、观星罗盘。
 */
public final class StellarStructureScenes {
    private static final String COMPASS = NS + "starwatch_compass";
    private static final List<String> PAINTINGS = List.of("astronomical_object", "falling_star", "magic_wine_fox", "starry_flower_sea");

    private StellarStructureScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.superflat("stellar_endshore.placedWithWitch", 80, StellarStructureScenes::endshore).withChunkRadius(5));
        scenes.add(Checks.superflat("starwatch_tower.placedWithAstroMancer", 80, StellarStructureScenes::tower).withChunkRadius(4));
        scenes.add(Checks.superflat("starwatch_tower.bookshelfKeepsSweepingEdge", 80, StellarStructureScenes::sweepingEdgeBook)
                .withChunkRadius(4));
        scenes.add(Checks.scene("lang.astroMancerName", 5, StellarStructureScenes::astroName));
        scenes.add(Checks.scene("paintings.placeableAndTitled", 5, StellarStructureScenes::paintings));
        scenes.add(Checks.scene("advancements.tabs", 5, StellarStructureScenes::advancements));
        scenes.add(Checks.superflat("starwatch_compass.wrongDimensionCooldown", 10, StellarStructureScenes::compassOverworld));
        scenes.add(Checks.scene("starwatch_compass.endSearchFinishesQuickly", 40, StellarStructureScenes::compassEnd));
        scenes.add(Checks.superflat("starwatch_compass.lodestoneKeepsTargetAndName", 10, StellarStructureScenes::compassLodestone));
        scenes.add(Checks.scene("starfall_garden.onlyOnePerWorld", 200, StellarStructureScenes::onlyOneGarden));
        return scenes;
    }

    /** 起始件加 7 个部件都放下，里面有一只星之魔女。 */
    private static void endshore(SceneContext ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, NS + "stellar_endshore");
        ctx.check(WorldExtract.ownPieces(placed).size()).as("星途终岸的拼图片数").isEqualTo(8);
        long witches = WorldExtract.entities(ctx.level(), placed).stream()
                .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(NS + "stellar_witch")).count();
        ctx.check(witches).as("结构里的星之魔女数").isEqualTo(1L);
    }

    private static void tower(SceneContext ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, NS + "starwatch_tower");
        long astro = WorldExtract.entities(ctx.level(), placed).stream()
                .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(NS + "astro_mancer")).count();
        ctx.check(astro).as("塔里的观星术士数").isEqualTo(1L);
    }

    private static void sweepingEdgeBook(SceneContext ctx) {
        StructureStage.Placed placed = StructureStage.place(ctx, NS + "starwatch_tower");
        var sweepingEdge = ctx.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SWEEPING_EDGE);
        List<Integer> levels = new ArrayList<>();
        for (StructureStage.Piece piece : WorldExtract.ownPieces(placed)) {
            for (var block : piece.blocks()) {
                if (!(ctx.level().getBlockEntity(block.pos()) instanceof ChiseledBookShelfBlockEntity bookshelf)) {
                    continue;
                }
                for (int slot = 0; slot < bookshelf.getContainerSize(); slot++) {
                    ItemStack book = bookshelf.getItem(slot);
                    int level = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                            .getLevel(sweepingEdge);
                    if (level > 0) {
                        ctx.check(book.is(Items.ENCHANTED_BOOK)).as("横扫之刃所在物品是附魔书").isTrue();
                        ctx.check(book.getCount()).as("横扫之刃附魔书数量").isEqualTo(1);
                        levels.add(level);
                    }
                }
            }
        }
        ctx.check(levels).as("放置后的观星塔书架有一本横扫之刃 III 附魔书").isEqualTo(List.of(3));
    }

    private static void astroName(SceneContext ctx) {
        String key = "entity.touhou_little_maid_spell.astro_mancer";
        ctx.check(Lang.read("en_us").get(key)).as("英文名").isEqualTo("Astro Mancer");
        ctx.check(Lang.read("zh_cn").get(key)).as("中文名").isEqualTo("观星术士");
    }

    /** 四幅画都在 #minecraft:placeable 里（普通画随机能挂出来），中英文都有标题。 */
    private static void paintings(SceneContext ctx) {
        var registry = ctx.server().registryAccess().registryOrThrow(Registries.PAINTING_VARIANT);
        Map<String, String> en = Lang.read("en_us");
        Map<String, String> zh = Lang.read("zh_cn");
        for (String id : PAINTINGS) {
            Optional<Holder.Reference<PaintingVariant>> variant = registry.getHolder(ResourceLocation.fromNamespaceAndPath("touhou_little_maid_spell", id));
            ctx.check(variant.isPresent() && variant.get().is(PaintingVariantTags.PLACEABLE)).as(id + " 可随机挂出").isTrue();
            String title = "painting.touhou_little_maid_spell." + id + ".title";
            ctx.check(en.containsKey(title) && zh.containsKey(title)).as(title + " 两种语言都有").isTrue();
        }
    }

    /** 星之旅程：启程之地是根（末地背景），星途终岸挂在它下面；万法皆通是单独一页，背景是樱花木板。 */
    private static void advancements(SceneContext ctx) {
        var advancements = ctx.server().getAdvancements();
        AdvancementHolder departure = advancements.get(ResourceLocation.parse(NS + "star_journey/departure_place"));
        AdvancementHolder endshore = advancements.get(ResourceLocation.parse(NS + "star_journey/stellar_endshore"));
        AdvancementHolder mastered = advancements.get(ResourceLocation.parse(NS + "dream_crystal/all_spells_mastered"));
        if (departure == null || endshore == null || mastered == null) {
            ctx.fail("缺少进度：启程之地=" + departure + " 星途终岸=" + endshore + " 万法皆通=" + mastered);
            return;
        }
        ctx.check(departure.value().parent().isEmpty()).as("启程之地是页签的根").isTrue();
        ctx.check(background(departure)).as("启程之地页签背景").isEqualTo("minecraft:textures/gui/advancements/backgrounds/end.png");
        ctx.check(endshore.value().parent().map(ResourceLocation::toString).orElse(null)).as("星途终岸的父进度").isEqualTo(departure.id().toString());
        ctx.check(mastered.value().parent().isEmpty()).as("万法皆通是页签的根").isTrue();
        ctx.check(background(mastered)).as("万法皆通页签背景").isEqualTo("minecraft:textures/block/cherry_planks.png");
        long journeyRoots = advancements.getAllAdvancements().stream()
                .filter(a -> a.id().getNamespace().equals("touhou_little_maid_spell") && a.id().getPath().startsWith("star_journey/"))
                .filter(a -> a.value().parent().isEmpty()).count();
        ctx.check(journeyRoots).as("星之旅程的根进度数").isEqualTo(1L);
    }

    private static String background(AdvancementHolder holder) {
        return holder.value().display().flatMap(DisplayInfo::getBackground).map(ResourceLocation::toString).orElse(null);
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    /** 主世界里右键：不绑定，进入冷却，冷却中再点无反应。 */
    private static void compassOverworld(SceneContext ctx) {
        FakePlayer player = player(ctx.level(), "TlmsCompassOverworld");
        ItemStack compass = Actors.stack(COMPASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        player.getCooldowns().removeCooldown(compass.getItem());
        compass.getItem().use(ctx.level(), player, InteractionHand.MAIN_HAND);
        ctx.check(compass.has(DataComponents.LODESTONE_TRACKER)).as("主世界右键后绑定了坐标").isFalse();
        ctx.check(player.getCooldowns().isOnCooldown(compass.getItem())).as("右键后进入冷却").isTrue();
        player.getCooldowns().removeCooldown(compass.getItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 末地里右键：一次搜索在 1 秒内结束（找到则绑定坐标，找不到则进入较长冷却）。 */
    private static void compassEnd(SceneContext ctx) {
        ServerLevel end = ctx.server().getLevel(Level.END);
        FakePlayer player = player(end, "TlmsCompassEnd");
        player.moveTo(0, 100, 0);
        ItemStack compass = Actors.stack(COMPASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        player.getCooldowns().removeCooldown(compass.getItem());
        long start = System.nanoTime();
        compass.getItem().use(end, player, InteractionHand.MAIN_HAND);
        long millis = (System.nanoTime() - start) / 1_000_000;
        ctx.record("searchMillis", millis);
        ctx.record("found", compass.get(DataComponents.LODESTONE_TRACKER));
        ctx.check(millis).as("末地搜索耗时（毫秒）").isLessThan(1000L);
        player.getCooldowns().removeCooldown(compass.getItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 已绑定的罗盘右键磁石：坐标不变；物品名仍是观星罗盘。 */
    private static void compassLodestone(SceneContext ctx) {
        ServerLevel level = ctx.level();
        FakePlayer player = player(level, "TlmsCompassLodestone");
        BlockPos lodestone = ctx.rel(1, 0, 0);
        level.setBlock(lodestone, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack compass = Actors.stack(COMPASS);
        LodestoneTracker target = new LodestoneTracker(Optional.of(GlobalPos.of(Level.END, new BlockPos(3000, 150, 3000))), false);
        compass.set(DataComponents.LODESTONE_TRACKER, target);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        compass.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(lodestone), Direction.UP, lodestone, false)));
        ctx.check(compass.get(DataComponents.LODESTONE_TRACKER)).as("右键磁石后的目标").isEqualTo(target);
        ctx.check(compass.getItem().getDescriptionId(compass)).as("绑定后的物品名键").isEqualTo("item.touhou_little_maid_spell.starwatch_compass");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 出生点环带内的两次手动放置最多成功一次；已有庭院时都被拒绝，原版冰屋仍能放置。 */
    private static void onlyOneGarden(SceneContext ctx) {
        var level = ctx.server().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        List<BlockPos> positions = List.of(spawn.offset(256, 0, 0), spawn.offset(352, 0, 0), spawn.offset(600, 0, 0));
        var tickets = com.github.yimeng261.maidspell.stagewright.support.Reflect.<net.minecraft.server.level.DistanceManager>field(
                level.getChunkSource(), net.minecraft.server.level.ServerChunkCache.class, "distanceManager");
        var ticket = net.minecraft.server.level.TicketType.create("tlms_manual_structure", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
        int ticketLevel = net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FULL);
        List<java.util.concurrent.CompletableFuture<?>> loading = new ArrayList<>();
        for (BlockPos position : positions) {
            var center = new net.minecraft.world.level.ChunkPos(position);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    var chunk = new net.minecraft.world.level.ChunkPos(center.x + dx, center.z + dz);
                    tickets.addTicket(ticket, chunk, ticketLevel, chunk);
                    ctx.cleanup(() -> tickets.removeTicket(ticket, chunk, ticketLevel, chunk));
                    loading.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> level.getChunkSource()
                                    .getChunkFuture(chunk.x, chunk.z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true),
                            net.minecraft.Util.backgroundExecutor()).thenCompose(future -> future)
                            .thenApply(result -> result.orElseThrow(() -> new IllegalStateException("测试区块生成失败：" + chunk))));
                }
            }
        }
        var loaded = java.util.concurrent.CompletableFuture.allOf(loading.toArray(java.util.concurrent.CompletableFuture[]::new));
        ctx.await(loaded::isDone).within(180).then(() -> {
            loaded.join();
            boolean alreadyPlaced = com.github.yimeng261.maidspell.worldgen.StarfallGardenData.isPlaced();
            ctx.record("gardenAlreadyPlaced", alreadyPlaced);
            int gardens = 0;
            for (BlockPos position : positions.subList(0, 2)) {
                gardens += place("place structure " + NS + "starfall_garden " + position.getX() + " 100 " + position.getZ(), ctx);
            }
            ctx.check(gardens).as("两次放置星落之庭成功的次数").isAtMost(alreadyPlaced ? 0 : 1);
            ctx.check(com.github.yimeng261.maidspell.worldgen.StarfallGardenData.isPlaced()).as("存档已有或本次生成一座庭院").isTrue();
            BlockPos igloo = positions.getLast();
            ctx.check(place("place structure minecraft:igloo " + igloo.getX() + " 100 " + igloo.getZ(), ctx))
                    .as("放置冰屋成功的次数").isEqualTo(1);
        });
    }

    private static int place(String command, SceneContext ctx) {
        CommandSourceStack source = ctx.server().createCommandSourceStack().withSuppressedOutput()
                .withLevel(ctx.server().overworld()).withPermission(4);
        try {
            return ctx.server().getCommands().getDispatcher().execute(command, source) > 0 ? 1 : 0;
        } catch (CommandSyntaxException e) {
            ctx.record("failed: " + command, e.getMessage());
            return 0;
        }
    }
}
