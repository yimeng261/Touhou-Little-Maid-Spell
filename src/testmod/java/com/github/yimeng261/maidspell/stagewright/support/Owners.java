package com.github.yimeng261.maidspell.stagewright.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.authlib.GameProfile;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 场景里的"谁属于谁"：第二名玩家（无客户端连接，登记进服务器玩家表和关卡实体表）、
 * 带主人的女仆、宠物、召唤物和法术实体，以及记分板队伍。生成的实体和队伍都登记清理。
 */
public final class Owners {
    /** 各模组召唤物、法术实体设置主人的公开方法，按顺序尝试第一个存在的。 */
    private static final List<String> OWNER_SETTERS = List.of("setSummoner", "setTrueOwner", "setOwner", "setOwnerID", "setOwnerUUID");

    private Owners() {
    }

    /** 无网络输入的玩家由关卡 tick 补上通常由连接触发的生存 tick。 */
    private static final class TickingPlayer extends ServerPlayer {
        TickingPlayer(net.minecraft.server.MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile, ClientInformation.createDefault());
        }

        @Override
        public void tick() {
            super.tick();
            super.doTick();
        }
    }

    /** 按名字固定 UUID 的服务端玩家，使用不发送数据包的连接；场景结束时移出所有登记表。 */
    public static ServerPlayer visitor(SceneContext ctx, String name, int dx, int dy, int dz) {
        ServerLevel level = ctx.level();
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer player = new TickingPlayer(ctx.server(), level, profile);
        try {
            var constructor = FakePlayerFactory.get(level, profile).connection.getClass()
                    .getDeclaredConstructor(net.minecraft.server.MinecraftServer.class, ServerPlayer.class);
            constructor.setAccessible(true);
            player.connection = (ServerGamePacketListenerImpl) constructor.newInstance(ctx.server(), player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法创建测试玩家连接", e);
        }
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        Reflect.set(connection, net.minecraft.network.Connection.class, "channel", channel);
        Reflect.set(player.connection, net.minecraft.server.network.ServerCommonPacketListenerImpl.class, "connection", connection);
        ctx.cleanup(channel::finishAndReleaseAll);
        BlockPos pos = ctx.rel(dx, dy, dz);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        player.setNoGravity(true);
        player.setHealth(player.getMaxHealth());
        Reflect.set(player, ServerPlayer.class, "spawnInvulnerableTime", 0);
        PlayerList playerList = ctx.server().getPlayerList();
        Map<UUID, ServerPlayer> byUUID = Reflect.field(playerList, PlayerList.class, "playersByUUID");
        List<ServerPlayer> players = Reflect.field(playerList, PlayerList.class, "players");
        ctx.cleanup(() -> {
            try {
                player.closeContainer();
            } finally {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                players.remove(player);
                byUUID.remove(player.getUUID(), player);
            }
        });
        if (byUUID.putIfAbsent(player.getUUID(), player) != null) {
            throw new IllegalStateException("测试玩家 UUID 已被占用：" + player.getUUID());
        }
        players.add(player);
        level.addNewPlayer(player);
        if (playerList.getPlayer(player.getUUID()) != player || level.getEntity(player.getUUID()) != player) {
            throw new IllegalStateException("测试玩家未完整登记");
        }
        return player;
    }

    /** 主人为 owner 的女仆（入世前写好主人，与游戏里读档进来的女仆一样登记到主人名下），无 AI、位置固定。 */
    public static EntityMaid maid(SceneContext ctx, Player owner, int dx, int dy, int dz) {
        return Actors.spawn(ctx, Actors.MAID, dx, dy, dz, true, (EntityMaid maid) -> {
            maid.setTame(true, false);
            maid.setOwnerUUID(owner.getUUID());
        });
    }

    /** 驯服给 owner 的原版宠物（狼、猫等）。 */
    public static TamableAnimal pet(SceneContext ctx, String type, Player owner, int dx, int dy, int dz) {
        return Actors.spawn(ctx, type, dx, dy, dz, true, (TamableAnimal pet) -> pet.tame(owner));
    }

    /** 生成 type 并把主人设为 owner（见 {@link #own}）；type 未注册时返回 null 并记一条。 */
    public static Entity owned(SceneContext ctx, String type, LivingEntity owner, int dx, int dy, int dz) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(type))) {
            ctx.record("unregistered:" + type, true);
            return null;
        }
        if (type.equals("minecraft:tnt")) {
            BlockPos pos = ctx.rel(dx, dy, dz);
            PrimedTnt tnt = new PrimedTnt(ctx.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, owner);
            tnt.setFuse(Short.MAX_VALUE);
            ctx.level().addFreshEntity(tnt);
            ctx.cleanup(tnt::discard);
            return tnt;
        }
        return Actors.spawn(ctx, type, dx, dy, dz, true, (Entity entity) -> own(entity, owner));
    }

    /**
     * 把 entity 的主人设为 owner：女仆写主人 UUID，原版可驯服生物用 tame，弹射物用 setOwner，
     * 其余按 {@link #OWNER_SETTERS} 反射找第一个参数类型匹配的公开方法。找不到时抛异常，场景据此报错。
     */
    public static void own(Entity entity, LivingEntity owner) {
        if (entity instanceof EntityMaid maid) {
            maid.setTame(true, false);
            maid.setOwnerUUID(owner.getUUID());
            return;
        }
        if (isArsSummon(entity.getClass())) {
            try {
                entity.getClass().getMethod("setOwnerID", UUID.class).invoke(entity, owner.getUUID());
                entity.getClass().getMethod("setTicksLeft", int.class).invoke(entity, 12000);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法设置新生魔艺召唤物的主人或寿命", e);
            }
            return;
        }
        if (entity instanceof TamableAnimal pet && owner instanceof Player player) {
            pet.tame(player);
            return;
        }
        if (entity instanceof Projectile projectile) {
            projectile.setOwner(owner);
            return;
        }
        for (String name : OWNER_SETTERS) {
            for (Method method : entity.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                Object argument = parameter == UUID.class ? owner.getUUID() : parameter.isInstance(owner) ? owner : null;
                if (argument == null) {
                    continue;
                }
                try {
                    method.invoke(entity, argument);
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("无法设置 " + entity.getType() + " 的主人", e);
                }
            }
        }
        throw new IllegalStateException(entity.getType() + " 没有可用的设置主人方法");
    }

    private static boolean isArsSummon(Class<?> type) {
        if (type == null) return false;
        if (type.getName().equals("com.hollingsworth.arsnouveau.api.entity.ISummon")) return true;
        for (Class<?> contract : type.getInterfaces()) {
            if (isArsSummon(contract)) return true;
        }
        return isArsSummon(type.getSuperclass());
    }

    /** 新建记分板队伍并把这些玩家加进去；场景结束时删除。 */
    public static PlayerTeam team(SceneContext ctx, String name, boolean friendlyFire, Player... members) {
        Scoreboard scoreboard = ctx.server().getScoreboard();
        PlayerTeam existing = scoreboard.getPlayerTeam(name);
        if (existing != null) {
            scoreboard.removePlayerTeam(existing);
        }
        PlayerTeam team = scoreboard.addPlayerTeam(name);
        team.setAllowFriendlyFire(friendlyFire);
        for (Player member : members) {
            scoreboard.addPlayerToTeam(member.getScoreboardName(), team);
        }
        ctx.cleanup(() -> {
            PlayerTeam current = scoreboard.getPlayerTeam(name);
            if (current != null) {
                scoreboard.removePlayerTeam(current);
            }
        });
        return team;
    }

    /** 让 attacker 以 target 为目标（走原版 setTarget，会触发改目标事件），返回事件处理后的实际目标。 */
    public static LivingEntity aim(Mob attacker, LivingEntity target) {
        attacker.setTarget(target);
        LivingEntity actual = attacker.getTarget();
        attacker.setTarget(null);
        return actual;
    }

    /** 对每个实体运行 action，跳过 null（未注册的类型）。 */
    public static <T> void each(List<T> entities, Consumer<T> action) {
        entities.stream().filter(java.util.Objects::nonNull).forEach(action);
    }
}
