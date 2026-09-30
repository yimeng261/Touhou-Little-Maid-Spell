package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.player.AnchorPendingChangeData;
import com.github.yimeng261.maidspell.player.ChunkLoadingData;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.utils.PersistentEntityLifecycleGuard;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.DimensionTransition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 锚定核心：强加载记录跟随女仆所在区块（主人在线时写在主人身上，离线时记为待变更）、
 * 死亡或卸下核心时删记录；抓捕、转化、雷击拿不走锚定女仆；卸载再加载、换维度后数据完整。
 * 以及普通生物的交互与转化不受这些保护影响。
 */
public final class AnchorScenes {
    private static final String ANCHOR = NS + "anchor_core";

    private AnchorScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.superflat("anchor.offlineOwnerRecordFollowsChunk", 40, AnchorScenes::offlineRecordFollows).withChunkRadius(3));
        scenes.add(Checks.superflat("anchor.offlineOwnerDeathDeletesRecord", 40, ctx -> offlineRecordDeleted(ctx, true)));
        scenes.add(Checks.superflat("anchor.offlineOwnerTakeOffDeletesRecord", 40, ctx -> offlineRecordDeleted(ctx, false)));
        scenes.add(Checks.superflat("anchor.otherOwnersPendingRemovalIgnored", 40, AnchorScenes::otherOwnersPending));
        scenes.add(Checks.superflat("anchor.captureBlocked", 20, AnchorScenes::captureBlocked));
        scenes.add(Checks.superflat("anchor.conversionAndLightningBlocked", 20, AnchorScenes::conversionBlocked));
        scenes.add(Checks.superflat("anchor.unloadReloadKeepsData", 60, AnchorScenes::reloadKeepsData));
        scenes.add(Checks.superflat("anchor.dimensionChangeKeepsData", 40, AnchorScenes::dimensionKeepsData));
        scenes.add(Checks.superflat("vanilla.mobInteractionsUnaffected", 20, AnchorScenes::vanillaInteractions));
        scenes.add(Checks.superflat("vanilla.mobConversionsUnaffected", 20, AnchorScenes::vanillaConversions));
        return scenes;
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("anchor.onlineOwnerRecordOnPlayer", 40, AnchorScenes::onlineRecord));
        scenes.add(Players.hostScene("anchor.recordUpdatesWhileOwnerInOtherDimension", 60, AnchorScenes::otherDimensionRecord));
        return scenes;
    }

    /** 挂在一个不在线的主人名下、戴锚定核心的无 AI 女仆。 */
    private static EntityMaid anchoredFor(SceneContext ctx, UUID owner, int dx, int dz) {
        EntityMaid maid = Actors.spawn(ctx, Actors.MAID, dx, 0, dz, true, (EntityMaid m) -> {
            m.setTame(true, false);
            m.setOwnerUUID(owner);
        });
        Actors.equipBauble(maid, ANCHOR);
        return maid;
    }

    private static ChunkLoadingData.LevelAndChunkPos here(Entity entity) {
        ChunkPos chunk = entity.chunkPosition();
        return new ChunkLoadingData.LevelAndChunkPos(entity.level().dimension(), chunk.x, chunk.z);
    }

    private static Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> pending(MinecraftServer server, UUID owner) {
        Map<UUID, Optional<ChunkLoadingData.LevelAndChunkPos>> out = new HashMap<>();
        AnchorPendingChangeData.get(server).forEach((o, changes) -> {
            if (o.equals(owner)) {
                out.putAll(changes);
            }
        });
        return out;
    }

    private static void offlineRecordFollows(SceneContext ctx) {
        MinecraftServer server = ctx.server();
        UUID owner = UUID.randomUUID();
        EntityMaid maid = anchoredFor(ctx, owner, 0, 0);
        ctx.await(() -> here(maid).equals(ChunkLoadingData.getRecord(server, owner, maid.getUUID()))).within(10).then(() -> {
            ctx.check(pending(server, owner).get(maid.getUUID())).as("离线主人的待变更").isEqualTo(Optional.of(here(maid)));
            BlockPos far = ctx.rel(0, 0, 32);
            maid.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
            ctx.await(() -> here(maid).equals(ChunkLoadingData.getRecord(server, owner, maid.getUUID()))).within(10).then(() ->
                    ctx.check(pending(server, owner).get(maid.getUUID())).as("换区块后的待变更").isEqualTo(Optional.of(here(maid))));
        });
    }

    private static void offlineRecordDeleted(SceneContext ctx, boolean byDeath) {
        MinecraftServer server = ctx.server();
        UUID owner = UUID.randomUUID();
        EntityMaid maid = anchoredFor(ctx, owner, 0, 0);
        UUID id = maid.getUUID();
        ctx.await(() -> ChunkLoadingData.getRecord(server, owner, id) != null).within(10).then(() -> {
            if (byDeath) {
                Maids.kill(maid);
            } else {
                Maids.takeOffAll(maid);
            }
            ctx.await(() -> ChunkLoadingData.getRecord(server, owner, id) == null).within(byDeath ? 30 : 10).then(() ->
                    ctx.check(pending(server, owner).get(id)).as((byDeath ? "死亡" : "卸下核心") + "后的待变更").isEqualTo(Optional.empty()));
        });
    }

    /** A 离线时对这只女仆留了待删记录，同一只女仆改由 B 锚定：B 的记录照常生效。 */
    private static void otherOwnersPending(SceneContext ctx) {
        MinecraftServer server = ctx.server();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        EntityMaid maid = anchoredFor(ctx, a, 0, 0);
        UUID id = maid.getUUID();
        ctx.await(() -> ChunkLoadingData.getRecord(server, a, id) != null).within(10).then(() -> {
            Maids.takeOffAll(maid);
            ctx.await(() -> ChunkLoadingData.getRecord(server, a, id) == null).within(10).then(() -> {
                maid.setOwnerUUID(b);
                Actors.equipBauble(maid, ANCHOR);
                ctx.await(() -> here(maid).equals(ChunkLoadingData.getRecord(server, b, id))).within(10).then(() -> {
                    ctx.check(pending(server, b).get(id)).as("B 的待变更").isEqualTo(Optional.of(here(maid)));
                    ctx.check(pending(server, a).get(id)).as("A 的待删记录").isEqualTo(Optional.empty());
                });
            });
        });
    }

    /** 抓捕类物品：Goety 秘法超立方左键锚定女仆被拦下，女仆还在、物品没记下实体。 */
    private static void captureBlocked(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsAnchorOwner", 2, 0, 0);
        EntityMaid maid = Owners.maid(ctx, owner, 0, 0, 0);
        Actors.equipBauble(maid, ANCHOR);
        ctx.check(PersistentEntityLifecycleGuard.shouldBlockCapture(maid)).as("锚定女仆受抓捕保护").isTrue();
        ItemStack tesseract = Actors.stack("goety:esoteric_tesseract");
        if (tesseract.isEmpty()) {
            ctx.passNote("没装 Goety，只核对保护判定");
            return;
        }
        String before = tesseract.getComponentsPatch().toString();
        boolean handled = tesseract.getItem().onLeftClickEntity(tesseract, owner, maid);
        ctx.check(handled).as("秘法超立方左键被拦下").isTrue();
        ctx.check(maid.isRemoved()).as("女仆被移除").isFalse();
        ctx.check(tesseract.getComponentsPatch().toString()).as("秘法超立方的数据").isEqualTo(before);
    }

    private static void conversionBlocked(SceneContext ctx) {
        EntityMaid maid = anchoredFor(ctx, UUID.randomUUID(), 0, 0);
        ctx.check(maid.convertTo(EntityType.WITCH, true)).as("锚定女仆转化结果").isNull();
        ServerLevel level = ctx.level();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        bolt.moveTo(maid.position());
        maid.thunderHit(level, bolt);
        ctx.check(maid.isRemoved()).as("雷击后女仆被移除").isFalse();
        ctx.check(maid.getMaidBauble().getStackInSlot(0).getItem().builtInRegistryHolder().getRegisteredName()).as("雷击后第一格饰品").isEqualTo(ANCHOR);
    }

    /** 背包放一件东西，卸载再加载后饰品、背包都在。 */
    private static void reloadKeepsData(SceneContext ctx) {
        EntityMaid maid = anchoredFor(ctx, UUID.randomUUID(), 0, 0);
        maid.getMaidInv().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        ctx.check(PersistentEntityLifecycleGuard.shouldBlockRemoval(maid, Entity.RemovalReason.UNLOADED_TO_CHUNK))
                .as("拦下随区块卸载").isFalse();
        Maids.reload(ctx, maid, 5, reloaded -> {
            if (!(reloaded instanceof EntityMaid again)) {
                ctx.fail("重新加载后不是女仆：" + reloaded);
                return;
            }
            ctx.check(again.getMaidBauble().getStackInSlot(0).getItem().builtInRegistryHolder().getRegisteredName()).as("重新加载后第一格饰品").isEqualTo(ANCHOR);
            ctx.check(again.getMaidInv().getStackInSlot(0).getCount()).as("重新加载后背包里的钻石").isEqualTo(3);
        });
    }

    private static void dimensionKeepsData(SceneContext ctx) {
        MinecraftServer server = ctx.server();
        UUID owner = UUID.randomUUID();
        EntityMaid maid = anchoredFor(ctx, owner, 0, 0);
        maid.getMaidInv().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        UUID id = maid.getUUID();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ChunkPos target = new ChunkPos(new BlockPos(ctx.rel(0, 0, 0).getX() / 8, 64, ctx.rel(0, 0, 0).getZ() / 8));
        nether.setChunkForced(target.x, target.z, true);
        ctx.cleanup(() -> nether.setChunkForced(target.x, target.z, false));
        Vec3 pos = new Vec3(target.getMiddleBlockX() + 0.5, 128, target.getMiddleBlockZ() + 0.5);
        Entity moved = Maids.copyToDimension(ctx, maid, nether, pos);
        if (!(moved instanceof EntityMaid there)) {
            ctx.fail("换维度后不是女仆：" + moved);
            return;
        }
        ctx.cleanup(() -> Actors.cleanupEntity(there));
        ctx.check(there.getMaidBauble().getStackInSlot(0).getItem().builtInRegistryHolder().getRegisteredName()).as("换维度后第一格饰品").isEqualTo(ANCHOR);
        ctx.check(there.getMaidInv().getStackInSlot(0).getCount()).as("换维度后背包里的钻石").isEqualTo(3);
        ctx.await(() -> here(there).equals(ChunkLoadingData.getRecord(server, owner, id))).within(20)
                .then(() -> ctx.passNote("记录跟到下界"));
    }

    /** 普通生物：拴绳、命名牌、刷怪蛋右键照常。 */
    private static void vanillaInteractions(SceneContext ctx) {
        ServerPlayer player = Owners.visitor(ctx, "TlmsFarmer", 2, 0, 0);
        Pig pig = Actors.spawn(ctx, "minecraft:pig", 0, 0, 0, true);
        use(player, pig, new ItemStack(Items.LEAD));
        ctx.check(pig.isLeashed()).as("拴绳拴上").isTrue();
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal("TlmsPig"));
        use(player, pig, tag);
        ctx.check(pig.getCustomName() == null ? null : pig.getCustomName().getString()).as("命名牌命名").isEqualTo("TlmsPig");
        int before = ctx.level().getEntitiesOfClass(Pig.class, new AABB(pig.blockPosition()).inflate(4)).size();
        use(player, pig, new ItemStack(Items.PIG_SPAWN_EGG));
        int after = ctx.level().getEntitiesOfClass(Pig.class, new AABB(pig.blockPosition()).inflate(4)).size();
        ctx.check(after).as("对猪用猪刷怪蛋后附近的猪数").isEqualTo(before + 1);
    }

    private static void use(ServerPlayer player, Entity target, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.interactOn(target, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 普通生物被雷劈照常转化：猪变僵尸猪灵，村民变女巫。 */
    private static void vanillaConversions(SceneContext ctx) {
        ServerLevel level = ctx.level();
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            ctx.skip("和平难度下村民不会被雷劈成女巫");
            return;
        }
        Pig pig = Actors.spawn(ctx, "minecraft:pig", 0, 0, 0, true);
        Villager villager = Actors.spawn(ctx, "minecraft:villager", 4, 0, 0, true);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        bolt.moveTo(pig.position());
        pig.thunderHit(level, bolt);
        villager.thunderHit(level, bolt);
        AABB box = new AABB(ctx.rel(0, 0, 0)).inflate(8);
        ctx.check(level.getEntities(EntityType.ZOMBIFIED_PIGLIN, box, Mob::isAlive).size()).as("猪转化出的僵尸猪灵").isEqualTo(1);
        ctx.check(level.getEntitiesOfClass(Witch.class, box).size()).as("村民转化出的女巫").isEqualTo(1);
        level.getEntities(EntityType.ZOMBIFIED_PIGLIN, box, Mob::isAlive).forEach(Entity::discard);
        level.getEntitiesOfClass(Witch.class, box).forEach(Entity::discard);
    }

    /** 主人在线：记录写在主人身上；女仆被杀后立刻删掉。 */
    private static void onlineRecord(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Owners.maid(ctx, player, 3, 0, 0);
        Actors.equipBauble(maid, ANCHOR);
        UUID id = maid.getUUID();
        ctx.cleanup(() -> player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().remove(id));
        ctx.await(() -> here(maid).equals(player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().get(id))).within(10).then(() -> {
            Maids.kill(maid);
            ctx.await(() -> !player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().containsKey(id)).within(10)
                    .then(() -> ctx.passNote("女仆死亡后主人身上的记录已删"));
        });
    }

    /** 主人在主世界，锚定女仆在下界换区块：主人身上的记录跟着更新。 */
    private static void otherDimensionRecord(SceneContext ctx, ServerPlayer player) {
        ServerLevel nether = ctx.server().getLevel(Level.NETHER);
        ChunkPos first = new ChunkPos(40, 40);
        ChunkPos second = new ChunkPos(42, 40);
        for (ChunkPos chunk : List.of(first, second)) {
            nether.setChunkForced(chunk.x, chunk.z, true);
            ctx.cleanup(() -> nether.setChunkForced(chunk.x, chunk.z, false));
        }
        EntityMaid maid = (EntityMaid) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(Actors.MAID)).create(nether);
        maid.setTame(true, false);
        maid.setOwnerUUID(player.getUUID());
        maid.setNoAi(true);
        maid.moveTo(first.getMiddleBlockX() + 0.5, 128, first.getMiddleBlockZ() + 0.5);
        nether.addFreshEntity(maid);
        ctx.cleanup(() -> Actors.cleanupEntity(maid));
        Actors.equipBauble(maid, ANCHOR);
        UUID id = maid.getUUID();
        ctx.cleanup(() -> player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().remove(id));
        ctx.await(() -> here(maid).equals(player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().get(id))).within(10).then(() -> {
            maid.teleportTo(second.getMiddleBlockX() + 0.5, 128, second.getMiddleBlockZ() + 0.5);
            ctx.await(() -> here(maid).equals(player.getData(ChunkLoadingData.ATTACHMENT_TYPE).maidChunks().get(id))).within(10)
                    .then(() -> ctx.passNote("主人在别的维度时记录跟着换区块"));
        });
    }
}
