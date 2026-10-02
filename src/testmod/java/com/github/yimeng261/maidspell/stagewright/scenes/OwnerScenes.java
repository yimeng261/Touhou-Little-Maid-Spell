package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedOwner;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.SpellWhiteListContainer;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.SpellWhiteListSpellManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 需要宿主玩家的女仆功能（集成服）：末影腰包远程打开女仆背包、法术白名单界面、
 * 喋血之心治疗主人、熔岩狐叶共享保护与主人踏熔岩、馥郁巧思经真实喂食任务给主人加效果、春花-返保护主人。
 * <p>女仆都经真实交互入口驯服（手持驯服物品右键），与游戏里一样登记到主人名下。
 */
public final class OwnerScenes {
    /** 远程女仆与玩家的距离：超出集成服默认视距，玩家不在追踪她。 */
    private static final int REMOTE = 320;

    private OwnerScenes() {
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("owner.ender_pocket.opensRemoteMaid", 100, OwnerScenes::enderPocket));
        scenes.add(Players.hostScene("owner.blue_note.menuStoresScroll", 40, OwnerScenes::blueNote));
        scenes.add(Players.hostScene("owner.bleeding_heart.healsOwner", 40, OwnerScenes::bleedingHeart));
        scenes.add(Players.hostScene("owner.molten_fox_leaf.sharedProtection", 40, OwnerScenes::moltenProtection));
        scenes.add(Players.hostScene("owner.molten_fox_leaf.ownerWalksOnLava", 140, OwnerScenes::moltenOwnerLava));
        scenes.add(Players.hostScene("owner.fragrant_ingenuity.feedOwnerBuff", 300, OwnerScenes::fragrantFeed));
        scenes.add(Players.hostScene("owner.spring_bloom_return.protectsOwner", 40, OwnerScenes::springBloomOwner));
        return scenes;
    }

    /**
     * 末影腰包：装备它的自有女仆出现在主人的末影腰包列表里（没装备的不在），
     * 远在视距外也能打开她的背包界面（客户端确认代理就绪后才打开）；别人的女仆打不开。
     */
    private static void enderPocket(SceneContext ctx, ServerPlayer player) {
        ServerLevel level = ctx.level();
        BlockPos far = ctx.rel(0, 0, REMOTE);
        level.getChunk(far);
        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        ctx.cleanup(() -> level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false));
        EntityMaid remote = Actors.stillMaid(ctx, 0, 0, REMOTE);
        EntityMaid plain = Actors.stillMaid(ctx, 2, 0, 0);
        EntityMaid stranger = Actors.stillMaid(ctx, -2, 0, 0);
        ctx.check(Players.tame(player, remote)).as("驯服远程女仆").isTrue();
        ctx.check(Players.tame(player, plain)).as("驯服无腰包女仆").isTrue();
        Actors.equipBauble(remote, NS + "ender_pocket");
        Actors.equipBauble(stranger, NS + "ender_pocket");
        // 远程区块刚强制加载，女仆要等区块进入实体追踪后才会登记到 ChunkMap（游戏里远处被加载的女仆都已如此）
        ctx.await(() -> listed(player).contains(remote.getUUID()) && tracked(level, remote)).within(60).then(() -> {
            ctx.record("remote", "owned=" + remote.isOwnedBy(player) + " sleeping=" + remote.isSleeping() + " alive=" + remote.isAlive());
            ctx.check(listed(player).contains(plain.getUUID())).as("没装备末影腰包的女仆在列表里").isFalse();
            ctx.check(listed(player).contains(stranger.getUUID())).as("别人的女仆在列表里").isFalse();
            ctx.record("watchingRemote", level.getChunkSource().chunkMap.getPlayers(new ChunkPos(far), false).contains(player));
            ctx.check(EnderPocketService.openMaidInventory(player, stranger.getUUID()))
                    .as("用末影腰包打开别人女仆的背包").isFalse();
            ctx.check(EnderPocketService.openMaidInventory(player, plain.getUUID()))
                    .as("用末影腰包打开没装备末影腰包的自有女仆背包").isFalse();
            ctx.check(EnderPocketService.openMaidInventory(player, remote.getUUID()))
                    .as("用末影腰包打开远程女仆的背包").isTrue();
            // 服务端先同步女仆代理，客户端确认后才打开界面
            ctx.await(() -> player.containerMenu instanceof AbstractMaidContainer).within(40).then(() -> {
                AbstractContainerMenu menu = player.containerMenu;
                ctx.check(menu instanceof AbstractMaidContainer container && container.getMaid() == remote)
                        .as("打开的界面是远程女仆的背包（实际 " + menu.getClass().getSimpleName() + "）").isTrue();
            });
        });
    }

    private static boolean tracked(ServerLevel level, EntityMaid maid) {
        return level.getChunkSource().chunkMap.entityMap.containsKey(maid.getId());
    }

    private static List<UUID> listed(ServerPlayer player) {
        return EnderPocketService.getPlayerEnderPocketMaids(player).stream().map(EnderPocketService.EnderPocketMaidInfo::getMaidUUID).toList();
    }

    /** 法术白名单：右键打开 27 格卷轴界面；放入卷轴不消耗原物品，关闭后法术记录到白名单物品上。 */
    private static void blueNote(SceneContext ctx, ServerPlayer player) {
        ItemStack note = Players.hold(player, Actors.stack(NS + "blue_note"));
        Players.use(player);
        AbstractContainerMenu menu = player.containerMenu;
        ctx.check(String.valueOf(BuiltInRegistries.MENU.getKey(menu.getType()))).as("打开的界面类型")
                .isEqualTo(NS + "blue_note_container");
        if (!(menu instanceof SpellWhiteListContainer)) {
            return;
        }
        ctx.check(menu.slots.size() - 36).as("卷轴格数").isEqualTo(27);
        ItemStack scroll = Actors.parse(ctx, "irons_spellbooks:scroll[irons_spellbooks:spell_container="
                + "{maxSpells:1,mustEquip:false,spellWheel:false,data:[{id:\"irons_spellbooks:firebolt\",index:0,level:1,locked:true}]}]");
        menu.setCarried(scroll);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        ctx.check(menu.getCarried().getCount()).as("放入卷轴后鼠标上仍持有的卷轴数").isEqualTo(1);
        ctx.check(menu.getSlot(0).getItem().isEmpty()).as("卷轴格 0 为空").isFalse();
        menu.setCarried(ItemStack.EMPTY);
        player.closeContainer();
        ctx.check(SpellWhiteListSpellManager.getStoredSpellIds(note, player.registryAccess()))
                .as("关闭界面后白名单记录的法术").isEqualTo(List.of("irons_spellbooks:firebolt"));
    }

    /** 喋血之心：自有女仆打出伤害时主人按比例回血；别人的女仆打出伤害不给这个玩家回血。 */
    private static void bleedingHeart(SceneContext ctx, ServerPlayer player) {
        EntityMaid own = Players.ownedMaid(ctx, player, 2, 0, 0, NS + "bleeding_heart");
        EntityMaid stranger = Actors.stillMaid(ctx, -2, 0, 0);
        Actors.equipBauble(stranger, NS + "bleeding_heart");
        Mob target = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 3, true);
        Mob otherTarget = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -3, true);
        Checks.after(ctx, 2, () -> {
            player.setHealth(10);
            otherTarget.hurt(stranger.damageSources().mobAttack(stranger), 8);
            ctx.check(player.getHealth()).as("别人的喋血之心女仆打出 8 点后玩家生命").isCloseTo(10, 1e-4);
            target.hurt(own.damageSources().mobAttack(own), 8);
            ctx.check(player.getHealth()).as("自有喋血之心女仆打出 8 点后主人生命")
                    .isCloseTo(10 + 8 * Config.bleedingHeartHealRatio, 1e-4);
        });
    }

    /** 熔岩狐叶：范围内自有女仆装备时主人免疫火焰/熔岩伤害并熄火；女仆离开范围、或只有别人的女仆时不保护。 */
    private static void moltenProtection(SceneContext ctx, ServerPlayer player) {
        EntityMaid own = Players.ownedMaid(ctx, player, 3, 0, 0, NS + "molten_fox_leaf");
        ctx.record("damageState", Players.damageState(player));
        player.setHealth(20);
        player.invulnerableTime = 0;
        ctx.check(player.hurt(player.damageSources().lava(), 2)).as("范围内有自有女仆时主人受到熔岩伤害").isFalse();
        player.igniteForSeconds(5);
        player.invulnerableTime = 0;
        ctx.check(player.hurt(player.damageSources().onFire(), 1)).as("范围内有自有女仆时主人受到燃烧伤害").isFalse();
        ctx.check(player.isOnFire()).as("受保护后主人仍着火").isFalse();
        BlockPos beyond = ctx.rel((int) Config.moltenFoxLeafOwnerRange + 4, 0, 0);
        own.teleportTo(beyond.getX() + 0.5, beyond.getY(), beyond.getZ() + 0.5);
        EntityMaid stranger = Actors.stillMaid(ctx, -3, 0, 0);
        Actors.equipBauble(stranger, NS + "molten_fox_leaf");
        player.invulnerableTime = 0;
        ctx.check(player.hurt(player.damageSources().lava(), 2))
                .as("自有女仆在范围外、只有别人的女仆在旁时主人受到熔岩伤害").isTrue();
    }

    /** 熔岩狐叶：自有女仆在旁时，主人（真实客户端物理）站在熔岩池上不下沉、不掉血、不着火。 */
    private static void moltenOwnerLava(SceneContext ctx, ServerPlayer player) {
        Actors.lavaPool(ctx, 3);
        Players.ownedMaid(ctx, player, 5, 0, 0, NS + "molten_fox_leaf");
        player.setHealth(20);
        int lavaTop = ctx.originY();
        Checks.after(ctx, 100, () -> {
            ctx.record("playerY", player.getY());
            ctx.check(player.getY()).as("主人脚底高度（熔岩面 y=" + lavaTop + "）").isAtLeast(lavaTop - 0.25);
            ctx.check(player.isInLava()).as("主人在熔岩里").isFalse();
            ctx.check(player.getHealth()).as("100 tick 后主人生命").isAtLeast(20F);
            ctx.check(player.isOnFire()).as("100 tick 后主人着火").isFalse();
        });
    }

    /**
     * 馥郁巧思：女仆经 TLM 真实的"喂食主人"任务把食物喂给饥饿的主人后，主人获得一个 1 级（放大 0）的正面效果，
     * 持续时间为配置值。
     */
    private static void fragrantFeed(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Actors.spawn(ctx, Actors.MAID, 1, 0, 0, false);
        maid.setPersistenceRequired();
        ctx.check(Players.tame(player, maid)).as("驯服女仆").isTrue();
        maid.setOrderedToSit(false);
        maid.setInSittingPose(false);
        maid.setSchedule(MaidSchedule.ALL);
        maid.setTask(TaskManager.findTask(TaskFeedOwner.UID).orElseThrow());
        Actors.equipBauble(maid, NS + "fragrant_ingenuity");
        maid.getMaidInv().setStackInSlot(0, new ItemStack(Items.BREAD, 4));
        player.removeAllEffects();
        player.getFoodData().setFoodLevel(6);
        ctx.await(() -> !player.getActiveEffects().isEmpty()).within(280).then(() -> {
            List<MobEffectInstance> effects = List.copyOf(player.getActiveEffects());
            ctx.record("effects", effects.stream().map(MobEffectInstance::toString).toList());
            ctx.check(maid.getMaidInv().getStackInSlot(0).getCount()).as("女仆背包剩余面包").isAtMost(3);
            ctx.check(effects.size()).as("主人获得的效果数").isEqualTo(1);
            MobEffectInstance effect = effects.getFirst();
            ctx.check(effect.getEffect().value().isBeneficial()).as(effect + " 是正面效果").isTrue();
            ctx.check(effect.getAmplifier()).as("效果放大等级").isEqualTo(0);
            ctx.check(effect.getDuration()).as("效果剩余时长").isAtLeast(Config.fragrantIngenuityBuffDuration - 40);
        });
    }

    /** 春花-返：主人受到较大伤害时，同维度内有层数的自有女仆消耗一层给主人回复最大生命的 5%；小伤害不触发。 */
    private static void springBloomOwner(SceneContext ctx, ServerPlayer player) {
        EntityMaid own = Players.ownedMaid(ctx, player, 2, 0, 0, NS + "spring_bloom_return");
        ctx.record("damageState", Players.damageState(player));
        Actors.castSpringBloom(own);
        ctx.check(Actors.springBloomExpiries(own).size()).as("施法后的层数").isEqualTo(1);
        player.setHealth(10);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), 1.5F);
        ctx.check(Actors.springBloomExpiries(own).size()).as("主人受到 1.5 点伤害后的层数").isEqualTo(1);
        ctx.check(player.getHealth()).as("主人受到 1.5 点伤害后的生命").isCloseTo(8.5, 1e-4);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), 6);
        ctx.check(Actors.springBloomExpiries(own).size()).as("主人受到 6 点伤害后的层数").isEqualTo(0);
        ctx.check(player.getHealth()).as("主人受到 6 点伤害并回复 5% 最大生命后的生命")
                .isCloseTo(8.5 - 6 + player.getMaxHealth() * Config.springBloomReturnHealRatio, 1e-4);
    }
}
