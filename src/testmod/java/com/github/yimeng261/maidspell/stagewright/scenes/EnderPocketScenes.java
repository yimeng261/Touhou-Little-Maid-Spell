package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidModelPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidTaskPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.RequestEffectPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SendNameTagPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SetMaidSoundIdPackage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketOpenInventory;
import com.github.yimeng261.maidspell.network.message.C2SEnderPocketTeleport;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketHudUpdate;
import com.github.yimeng261.maidspell.network.message.S2CEnderPocketMaidList;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Packets;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 末影腰包：按女仆 UUID 请求的授权校验、远程会话的建立与失效、请求限流、列表与名字的编码上限、
 * 远程界面里的车万女仆操作（切任务、改配置、换模型、改名、换音效、看效果）作用到远处或其他维度的女仆，
 * 别人伪造实体 ID 打不中；传送到锚定女仆身边（集成服）；经真实网络加入的客户端远程打开背包（专用服 + 客户端）。
 */
public final class EnderPocketScenes {
    private static final String POCKET = NS + "ender_pocket";
    private static final String ANCHOR = NS + "anchor_core";
    /** 远程女仆与玩家的距离：超出视距与实体追踪范围。 */
    private static final int REMOTE = 1000;

    private EnderPocketScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.superflat("ender_pocket.listShowsOwnLivingPocketMaids", 20, EnderPocketScenes::listFilters));
        scenes.add(Checks.superflat("ender_pocket.sessionEndsWhenPocketRemoved", 20, ctx -> sessionEnds(ctx, "pocket")));
        scenes.add(Checks.superflat("ender_pocket.sessionEndsWhenMaidSleeps", 20, ctx -> sessionEnds(ctx, "sleep")));
        scenes.add(Checks.superflat("ender_pocket.sessionEndsWhenMaidDies", 20, ctx -> sessionEnds(ctx, "death")));
        scenes.add(Checks.superflat("ender_pocket.sessionEndsWhenMaidAdopted", 20, ctx -> sessionEnds(ctx, "adopted")));
        scenes.add(Checks.superflat("ender_pocket.sessionEndsWhenMaidInSlab", 20, ctx -> sessionEnds(ctx, "slab")));
        scenes.add(Checks.superflat("ender_pocket.sessionExpires", 20, EnderPocketScenes::sessionExpires));
        scenes.add(Checks.superflat("ender_pocket.openRequestsRateLimited", 20, EnderPocketScenes::openRateLimited));
        scenes.add(Checks.superflat("ender_pocket.listCappedAt64", 40, EnderPocketScenes::listCapped));
        scenes.add(Checks.superflat("ender_pocket.longNameTruncated", 20, EnderPocketScenes::longName));
        scenes.add(Checks.superflat("ender_pocket.remoteOperationsApplyAcrossDimensions", 40, EnderPocketScenes::remoteOperations));
        scenes.add(Checks.superflat("ender_pocket.forgedEntityIdMissesOthersSession", 40, EnderPocketScenes::forgedPacket));
        return scenes;
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("ender_pocket.teleportNeedsAnchorCore", 40, EnderPocketScenes::teleportNeedsAnchor));
        scenes.add(Players.hostScene("ender_pocket.teleportToSafeSpot", 60, EnderPocketScenes::teleportSafe));
        scenes.add(Players.hostScene("ender_pocket.teleportAcrossDimensions", 80, EnderPocketScenes::teleportAcross));
        scenes.add(Players.hostScene("ender_pocket.teleportDismountsFirst", 60, EnderPocketScenes::teleportDismounts));
        scenes.add(Players.hostScene("ender_pocket.teleportCooldown", 60, EnderPocketScenes::teleportCooldown));
        return scenes;
    }

    public static List<Scene> dedicatedServerWithClient() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("remote.openFarMaidOverNetwork", 200, (ctx, player) -> openOverNetwork(ctx, player, false)));
        scenes.add(Players.hostScene("remote.openCrossDimensionMaidOverNetwork", 200, (ctx, player) -> openOverNetwork(ctx, player, true)));
        scenes.add(Players.hostScene("remote.fullListStaysConnected", 80, EnderPocketScenes::fullListOverNetwork));
        scenes.add(Players.hostScene("remote.remoteDataPushedWhileOpen", 280, EnderPocketScenes::pushedWhileOpen));
        return scenes;
    }

    // ---- 工具 ----

    /** 在 level 的 pos 处放一只主人为 owner、戴着末影腰包的女仆（强加载所在区块，场景结束时解除）。 */
    static EntityMaid pocketMaid(SceneContext ctx, ServerLevel level, BlockPos pos, Player owner, String... extraBaubles) {
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        ctx.cleanup(() -> level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false));
        if (level.getBlockState(pos.below()).isAir()) {
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        }
        EntityMaid maid = (EntityMaid) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(Actors.MAID)).create(level);
        maid.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        maid.setNoAi(true);
        maid.setPersistenceRequired();
        maid.setTame(true, false);
        maid.setOwnerUUID(owner.getUUID());
        level.addFreshEntity(maid);
        ctx.cleanup(() -> Actors.cleanupEntity(maid));
        List<String> baubles = new ArrayList<>(List.of(POCKET));
        baubles.addAll(List.of(extraBaubles));
        Maids.equip(maid, baubles.toArray(String[]::new));
        return maid;
    }

    static EntityMaid pocketMaid(SceneContext ctx, Player owner, int dx, int dy, int dz, String... extraBaubles) {
        return pocketMaid(ctx, ctx.level(), ctx.rel(dx, dy, dz), owner, extraBaubles);
    }

    private static List<UUID> listed(ServerPlayer player) {
        return EnderPocketService.getPlayerEnderPocketMaids(player).stream().map(EnderPocketService.EnderPocketMaidInfo::getMaidUUID).toList();
    }

    /** 当前远程会话（没有时为 null）。 */
    private static Object session(ServerPlayer player) {
        Map<UUID, ?> sessions = Reflect.field(null, EnderPocketService.class, "REMOTE_SESSIONS");
        return sessions.get(player.getUUID());
    }

    private static UUID sessionId(Object session) {
        return Reflect.field(session, session.getClass(), "sessionId");
    }

    /** 走完"请求打开 → 客户端确认代理就绪"：模拟玩家没有客户端，这里直接用会话 ID 确认。 */
    static boolean openRemote(ServerPlayer player, EntityMaid maid) {
        if (!EnderPocketService.openMaidInventory(player, maid.getUUID())) {
            return false;
        }
        Object session = session(player);
        return session != null && EnderPocketService.completeRemoteOpen(player, sessionId(session));
    }

    // ---- 专用服 ----

    /** 列表只有自己的、存活的、戴末影腰包的女仆；别人的和没戴的打不开。 */
    private static void listFilters(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketOwner", 0, 0, -3);
        ServerPlayer stranger = Owners.visitor(ctx, "TlmsPocketStranger", 0, 0, -5);
        EntityMaid listed = pocketMaid(ctx, owner, 2, 0, 0);
        EntityMaid bare = Owners.maid(ctx, owner, -2, 0, 0);
        EntityMaid dead = pocketMaid(ctx, owner, 0, 0, 2);
        EntityMaid others = pocketMaid(ctx, stranger, 0, 0, 4);
        Maids.kill(dead);
        ctx.check(listed(owner)).as("主人的末影腰包列表").isEqualTo(List.of(listed.getUUID()));
        ctx.check(EnderPocketService.openMaidInventory(owner, bare.getUUID())).as("打开没戴末影腰包的自有女仆").isFalse();
        ctx.check(EnderPocketService.openMaidInventory(owner, dead.getUUID())).as("打开已死亡的女仆").isFalse();
        ctx.check(EnderPocketService.openMaidInventory(owner, others.getUUID())).as("打开别人的女仆").isFalse();
        ctx.check(EnderPocketService.openMaidInventory(owner, listed.getUUID())).as("打开自己的末影腰包女仆").isTrue();
    }

    /** 已打开的远程会话在女仆摘下腰包、睡觉、死亡、被别人收养、被收进魂符后失效；再点残留按钮打不开，也不报错。 */
    private static void sessionEnds(SceneContext ctx, String how) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketOwner", 0, 0, -3);
        ServerLevel nether = ctx.server().getLevel(Level.NETHER);
        EntityMaid maid = pocketMaid(ctx, nether, new BlockPos(ctx.originX(), 70, ctx.originZ()), owner);
        UUID id = maid.getUUID();
        ctx.await(() -> nether.getEntity(maid.getUUID()) == maid && listed(owner).contains(id)).within(30).then(() -> {
            ctx.check(openRemote(owner, maid)).as("建立远程会话").isTrue();
            ctx.check(EnderPocketService.isGuiStillValid(owner, maid)).as("会话建立后界面有效").isTrue();
            switch (how) {
                case "pocket" -> Maids.takeOffAll(maid);
                case "sleep" -> maid.startSleeping(maid.blockPosition());
                case "death" -> Maids.kill(maid);
                case "adopted" -> maid.setOwnerUUID(UUID.nameUUIDFromBytes("TlmsPocketAdopter".getBytes()));
                default -> Players.storeInSlab(owner, maid);
            }
            ctx.check(EnderPocketService.resolveRemoteMaid(owner, maid.getId())).as(how + " 后远程会话仍解析到女仆").isNull();
            ctx.check(EnderPocketService.isGuiStillValid(owner, maid)).as(how + " 后界面仍有效").isFalse();
            ctx.check(EnderPocketService.openMaidInventory(owner, id)).as(how + " 后再点列表按钮打开").isFalse();
            ctx.check(EnderPocketService.teleportToMaid(owner, id)).as(how + " 后再点传送按钮").isFalse();
        });
    }

    /** 会话过期（10 分钟不操作）后不再解析到女仆。 */
    private static void sessionExpires(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketOwner", 0, 0, -3);
        EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
        ctx.check(openRemote(owner, maid)).as("建立远程会话").isTrue();
        Object session = session(owner);
        try {
            var field = session.getClass().getDeclaredField("expiresAtNanos");
            field.setAccessible(true);
            field.setLong(session, System.nanoTime() - 1);
        } catch (ReflectiveOperationException e) {
            ctx.fail("无法改写会话截止时间: " + e);
            return;
        }
        ctx.check(EnderPocketService.resolveRemoteMaid(owner, maid.getId())).as("过期后远程会话仍解析到女仆").isNull();
    }

    /** 1 秒内连发打开请求：只有第一次真的建立会话，其余被限流丢弃，不报错。 */
    private static void openRateLimited(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketRate", 0, 0, -3);
        EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
        C2SEnderPocketOpenInventory request = new C2SEnderPocketOpenInventory(maid.getUUID());
        request.handle(owner);
        Object first = session(owner);
        ctx.check(first).as("第一次请求建立的会话").isNotNull();
        for (int i = 0; i < 20; i++) {
            request.handle(owner);
        }
        ctx.check(session(owner) == first).as("连发 20 次后会话未被替换").isTrue();
    }

    /** 70 只戴腰包的女仆：列表消息截到 64 条，编码解码不出错。 */
    private static void listCapped(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketMany", 0, 0, -3);
        for (int i = 0; i < 70; i++) {
            pocketMaid(ctx, owner, i % 10 - 5, 0, i / 10);
        }
        List<EnderPocketService.EnderPocketMaidInfo> infos = EnderPocketService.getPlayerEnderPocketMaids(owner);
        ctx.check(infos.size()).as("服务端收集到的女仆数").isEqualTo(70);
        S2CEnderPocketMaidList list = roundTrip(S2CEnderPocketMaidList.STREAM_CODEC, new S2CEnderPocketMaidList(infos, false));
        ctx.check(list.maidInfos().size()).as("列表消息解码后的条数").isEqualTo(64);
        S2CEnderPocketHudUpdate hud = roundTrip(S2CEnderPocketHudUpdate.STREAM_CODEC, new S2CEnderPocketHudUpdate(infos));
        ctx.check(hud.maidInfos().size()).as("状态栏消息解码后的条数").isEqualTo(64);
    }

    /** 名字超过 64 个字符：列表里截到 64 个字符，编码解码不出错。 */
    private static void longName(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketName", 0, 0, -3);
        EntityMaid maid = pocketMaid(ctx, owner, 0, 0, 3);
        maid.setCustomName(Component.literal("名".repeat(100)));
        S2CEnderPocketMaidList list = roundTrip(S2CEnderPocketMaidList.STREAM_CODEC,
                new S2CEnderPocketMaidList(EnderPocketService.getPlayerEnderPocketMaids(owner), false));
        ctx.check(list.maidInfos().size()).as("解码后的条数").isEqualTo(1);
        ctx.check(list.maidInfos().getFirst().maidName.length()).as("解码后的名字长度").isEqualTo(64);
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    /**
     * 主人在主世界、女仆在下界：远程会话里发来的车万女仆操作包（切任务、改拾取/骑乘配置、换模型、改名、换音效、
     * 请求药水效果）都作用到下界的女仆。
     */
    private static void remoteOperations(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketRemote", 0, 0, -3);
        ServerLevel nether = ctx.server().getLevel(Level.NETHER);
        EntityMaid maid = pocketMaid(ctx, nether, new BlockPos(ctx.originX(), 70, ctx.originZ()), owner);
        ctx.check(ctx.level().getEntity(maid.getId())).as("主世界按实体 ID 找到下界女仆").isNull();
        ctx.check(openRemote(owner, maid)).as("建立跨维度远程会话").isTrue();
        var context = Packets.from(owner);
        int id = maid.getId();
        MaidTaskPackage.handle(new MaidTaskPackage(id, SpellCombatFarTask.UID), context);
        ctx.check(maid.getTask().getUid()).as("切换后的任务").isEqualTo(SpellCombatFarTask.UID);
        MaidConfigPackage.handle(new MaidConfigPackage(id, maid.isHomeModeEnable(), !maid.isPickup(), !maid.isRideable(), maid.getSchedule()), context);
        ctx.record("pickup/ride", maid.isPickup() + "/" + maid.isRideable());
        ResourceLocation model = ResourceLocation.parse("touhou_little_maid:hakurei_reimu");
        MaidModelPackage.handle(new MaidModelPackage(id, model), context);
        ctx.check(maid.getModelId()).as("换模型后的模型").isEqualTo(model.toString());
        Players.hold(owner, new ItemStack(Items.NAME_TAG));
        SendNameTagPackage.handle(new SendNameTagPackage(id, "远程改名", false), context);
        ctx.check(maid.getName().getString()).as("改名后的名字").isEqualTo("远程改名");
        String sound = maid.getSoundPackId().equals("touhou_little_maid") ? "maidspell_test" : "touhou_little_maid";
        SetMaidSoundIdPackage.handle(new SetMaidSoundIdPackage(id, sound), context);
        ctx.check(maid.getSoundPackId()).as("换音效包后的音效包").isEqualTo(sound);
        RequestEffectPackage.handle(new RequestEffectPackage(id), context);
        ctx.passNote("请求药水效果未抛异常");
    }

    /** 另一名玩家伪造 Dev 远程女仆的实体 ID 发包：打不中这只女仆，也不给 Dev 的会话续期。 */
    private static void forgedPacket(SceneContext ctx) {
        ServerPlayer owner = Owners.visitor(ctx, "TlmsPocketDev", 0, 0, -3);
        ServerPlayer forger = Owners.visitor(ctx, "TlmsPocketDev2", 0, 0, -5);
        ServerLevel nether = ctx.server().getLevel(Level.NETHER);
        EntityMaid maid = pocketMaid(ctx, nether, new BlockPos(ctx.originX(), 70, ctx.originZ()), owner);
        ctx.await(() -> nether.getEntity(maid.getUUID()) == maid && listed(owner).contains(maid.getUUID())).within(30).then(() -> {
            ctx.check(openRemote(owner, maid)).as("建立跨维度远程会话").isTrue();
            Object session = session(owner);
            if (session == null) {
                ctx.fail("跨维度会话未建立");
                return;
            }
            long expiry = Reflect.field(session, session.getClass(), "expiresAtNanos");
            ResourceLocation before = maid.getTask().getUid();
            MaidTaskPackage.handle(new MaidTaskPackage(maid.getId(), SpellCombatFarTask.UID), Packets.from(forger));
            ctx.check(maid.getTask().getUid()).as("伪造包之后女仆的任务").isEqualTo(before);
            long after = Reflect.field(session, session.getClass(), "expiresAtNanos");
            ctx.check(after).as("伪造包之后会话的截止时间").isEqualTo(expiry);
        });
    }

    // ---- 集成服：传送 ----

    /** 只有同时戴锚定核心的女仆才能传送过去。 */
    private static void teleportNeedsAnchor(SceneContext ctx, ServerPlayer player) {
        EntityMaid plain = pocketMaid(ctx, player, 0, 0, 40);
        Vec3 start = player.position();
        ctx.check(EnderPocketService.teleportToMaid(player, plain.getUUID())).as("传送到没戴锚定核心的女仆").isFalse();
        ctx.check(player.position().distanceTo(start)).as("被拒绝后玩家位移").isLessThan(0.01);
    }

    /** 传送到女仆旁边：不卡进方块，站在实心地面上，摔落距离清零。 */
    private static void teleportSafe(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = pocketMaid(ctx, player, 0, 0, 40, ANCHOR);
        // 女仆东南西北都是墙，只有西北角空着
        BlockPos at = maid.blockPosition();
        for (BlockPos pos : List.of(at.north(), at.south(), at.east(), at.west(), at.north().east(), at.south().east(), at.south().west())) {
            ctx.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            ctx.level().setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
            ctx.cleanup(() -> {
                ctx.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                ctx.level().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            });
        }
        player.fallDistance = 10;
        ctx.check(EnderPocketService.teleportToMaid(player, maid.getUUID())).as("传送").isTrue();
        Checks.after(ctx, 5, () -> {
            ctx.check(player.blockPosition()).as("落点").isEqualTo(at.north().west());
            ctx.check(player.isInWall()).as("落点卡在方块里").isFalse();
            ctx.check(player.fallDistance).as("落地后的摔落距离").isEqualTo(0F);
        });
    }

    /** 跨维度传送到下界的锚定女仆身边。 */
    private static void teleportAcross(SceneContext ctx, ServerPlayer player) {
        ServerLevel nether = ctx.server().getLevel(Level.NETHER);
        BlockPos pos = new BlockPos(ctx.originX(), 70, ctx.originZ());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                nether.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 3; y++) {
                    nether.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        EntityMaid maid = pocketMaid(ctx, nether, pos, player, ANCHOR);
        ctx.cleanup(() -> player.teleportTo(ctx.level(), ctx.originX() + 0.5, ctx.originY(), ctx.originZ() + 0.5, 0, 0));
        ctx.await(() -> nether.getEntity(maid.getUUID()) == maid && nether.getEntity(maid.getId()) == maid
                && listed(player).contains(maid.getUUID())).within(60).then(() -> {
            ctx.check(EnderPocketService.teleportToMaid(player, maid.getUUID())).as("跨维度传送").isTrue();
            ctx.await(() -> !player.isChangingDimension()).within(60).then(() -> {
                ctx.check(player.level().dimension()).as("传送后所在维度").isEqualTo(Level.NETHER);
                ctx.check(player.distanceTo(maid)).as("传送后与女仆的距离").isLessThan(2F);
                ctx.check(player.isInWall()).as("落点卡在方块里").isFalse();
            });
        });
    }

    /** 骑乘状态下传送：先下坐骑再传送。 */
    private static void teleportDismounts(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = pocketMaid(ctx, player, 0, 0, 40, ANCHOR);
        Entity boat = Actors.spawn(ctx, "minecraft:boat", 1, 0, 0, false);
        player.startRiding(boat, true);
        ctx.check(player.isPassenger()).as("玩家在船上").isTrue();
        ctx.check(EnderPocketService.teleportToMaid(player, maid.getUUID())).as("传送").isTrue();
        Checks.after(ctx, 5, () -> {
            ctx.check(player.isPassenger()).as("传送后仍在骑乘").isFalse();
            ctx.check(player.distanceTo(maid)).as("传送后与女仆的距离").isLessThan(2F);
            ctx.check(boat.distanceTo(maid)).as("船跟着传送过去").isGreaterThan(10F);
        });
    }

    /** 传送请求有 1 秒冷却：连点两次只传送一次。 */
    private static void teleportCooldown(SceneContext ctx, ServerPlayer player) {
        EntityMaid first = pocketMaid(ctx, player, 0, 0, 40, ANCHOR);
        EntityMaid second = pocketMaid(ctx, player, 40, 0, 0, ANCHOR);
        new C2SEnderPocketTeleport(first.getUUID()).handle(player);
        new C2SEnderPocketTeleport(second.getUUID()).handle(player);
        Checks.after(ctx, 5, () -> {
            ctx.check(player.distanceTo(first)).as("第一次请求后与第一只女仆的距离").isLessThan(2F);
            ctx.check(player.distanceTo(second)).as("冷却内的第二次请求后与第二只女仆的距离").isGreaterThan(10F);
        });
    }

    // ---- 专用服 + 真实客户端 ----

    /** 经网络：服务端发女仆快照，客户端建代理并回确认，服务端再打开车万女仆界面。 */
    private static void openOverNetwork(SceneContext ctx, ServerPlayer player, boolean crossDimension) {
        EntityMaid maid = crossDimension
                ? pocketMaid(ctx, ctx.server().getLevel(Level.NETHER), new BlockPos(ctx.originX(), 70, ctx.originZ()), player)
                : pocketMaid(ctx, player, 0, 0, REMOTE);
        ctx.await(() -> EnderPocketService.getPlayerEnderPocketMaids(player).stream()
                .anyMatch(info -> info.getMaidUUID().equals(maid.getUUID()))).within(60).then(() -> {
            ctx.check(EnderPocketService.openMaidInventory(player, maid.getUUID())).as("请求打开").isTrue();
            ctx.await(() -> player.containerMenu instanceof AbstractMaidContainer container && container.getMaid() == maid)
                    .within(100).then(() -> {
                        ctx.check(EnderPocketService.isRemoteSessionActive(player, maid)).as("客户端确认后会话激活").isTrue();
                        player.closeContainer();
                    });
        });
    }

    /** 经网络推送 70 只女仆的列表（截到 64 条）：客户端不断开。 */
    private static void fullListOverNetwork(SceneContext ctx, ServerPlayer player) {
        for (int i = 0; i < 70; i++) {
            pocketMaid(ctx, player, i % 10 - 5, 0, 3 + i / 10);
        }
        player.connection.send(new S2CEnderPocketMaidList(EnderPocketService.getPlayerEnderPocketMaids(player), false));
        Checks.after(ctx, 40, () -> {
            ctx.check(ctx.server().getPlayerList().getPlayer(player.getUUID())).as("推送后客户端仍在线").isNotNull();
            ctx.check(player.hasDisconnected()).as("推送后客户端断开").isFalse();
        });
    }

    /** 远程界面开着时，客户端代理的生命值随服务端女仆变化。 */
    private static void pushedWhileOpen(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = pocketMaid(ctx, player, 0, 0, REMOTE);
        ctx.await(() -> EnderPocketService.getPlayerEnderPocketMaids(player).stream()
                .anyMatch(info -> info.getMaidUUID().equals(maid.getUUID()))).within(60).then(() -> {
            ctx.check(EnderPocketService.openMaidInventory(player, maid.getUUID())).as("请求打开").isTrue();
            ctx.await(() -> player.containerMenu instanceof AbstractMaidContainer).within(100).then(() -> {
                com.github.yimeng261.maidspell.stagewright.support.ProxyHealthProbe.awaitHealth(
                        ctx, player, maid.getId(), maid.getHealth(), 40, () -> {
                            float health = maid.getMaxHealth() / 2;
                            Actors.setMaidHealth(maid, health);
                            com.github.yimeng261.maidspell.stagewright.support.ProxyHealthProbe.awaitHealth(
                                    ctx, player, maid.getId(), health, 40, player::closeContainer);
                        });
            });
        });
    }
}
