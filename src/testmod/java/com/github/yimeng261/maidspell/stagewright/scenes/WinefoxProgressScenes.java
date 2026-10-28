package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.network.message.WinefoxProgressMessage;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.winefox.WinefoxAdvancements;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeProgress;
import com.github.yimeng261.maidspell.winefox.WinefoxVowProgress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女的玩家级誓约进度（文案选择、同步包编解码、存档读写）与「虚空之秘」成就、星锚珍珠教学
 * （成就定义、授予幂等、30 秒无目标判负的二阶段落败也授予、台词与语言键）。
 */
public final class WinefoxProgressScenes {
    private static final ResourceLocation VOID_SECRET = ResourceLocation.fromNamespaceAndPath("touhou_little_maid_spell", "star_journey/void_secret");

    private WinefoxProgressScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("winefox.vowProgress.textsFollowProgress", 5, WinefoxProgressScenes::texts));
        scenes.add(Checks.scene("winefox.vowProgress.packetRoundTrip", 5, WinefoxProgressScenes::packet));
        scenes.add(Checks.scene("winefox.vowProgress.savedDataRoundTrip", 5, WinefoxProgressScenes::savedData));
        scenes.add(Checks.scene("winefox.voidSecret.advancementHiddenUnderEndshore", 5, WinefoxProgressScenes::advancementDefinition));
        scenes.add(Checks.superflat("winefox.voidSecret.grantIsIdempotent", 5, WinefoxProgressScenes::grantTwice));
        scenes.add(Checks.superflat("winefox.voidSecret.thirtySecondLossInPhaseTwoGrants", 10, ctx -> thirtySecondLoss(ctx, true)));
        scenes.add(Checks.superflat("winefox.voidSecret.thirtySecondLossInPhaseOneDoesNot", 10, ctx -> thirtySecondLoss(ctx, false)));
        scenes.add(Checks.scene("winefox.voidLesson.langKeysAndDistinctFirstLine", 5, WinefoxProgressScenes::lessonLang));
        return scenes;
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "<非翻译文本>";
    }

    /** 归星提示与动作栏的文案：0~1 场只有进度，两场后等专属胜利，齐了提示献上归星；未计入时用“未计入”文案；进度显示封顶 2。 */
    private static void texts(SceneContext ctx) {
        String prefix = "item.touhou_little_maid_spell.returning_star.";
        ctx.check(WinefoxVowProgress.nextStep(0, false) == null).as("0 场没有下一步提示").isTrue();
        ctx.check(WinefoxVowProgress.nextStep(1, true) == null).as("1 场即使有专属胜利也没有下一步提示").isTrue();
        ctx.check(key(WinefoxVowProgress.nextStep(2, false))).as("2 场、无专属胜利").isEqualTo(prefix + "awaiting_dance");
        ctx.check(key(WinefoxVowProgress.nextStep(2, true))).as("2 场、有专属胜利").isEqualTo(prefix + "ready");
        ctx.check(key(WinefoxVowProgress.nextStep(7, true))).as("超过 2 场").isEqualTo(prefix + "ready");
        ctx.check(WinefoxVowProgress.displayWins(7)).as("进度显示封顶").isEqualTo(2);
        ctx.check(WinefoxVowProgress.displayWins(-3)).as("进度显示不为负").isEqualTo(0);
        ctx.check(key(WinefoxVowProgress.actionBar(1, false, true))).as("动作栏：计入且只有 1 场").isEqualTo(prefix + "progress");
        ctx.check(key(WinefoxVowProgress.actionBar(2, false, true))).as("动作栏：2 场等共舞").isEqualTo(prefix + "awaiting_dance");
        ctx.check(key(WinefoxVowProgress.actionBar(2, true, true))).as("动作栏：齐了").isEqualTo(prefix + "ready");
        ctx.check(key(WinefoxVowProgress.actionBar(1, false, false))).as("动作栏：未计入").isEqualTo(prefix + "not_counted");
        for (String code : List.of("zh_cn", "en_us", "ja_jp")) {
            Map<String, String> lang = Lang.read(code);
            for (String suffix : List.of("progress", "awaiting_dance", "ready", "not_counted")) {
                ctx.check(lang.containsKey(prefix + suffix)).as(code + " 有 " + suffix).isTrue();
            }
        }
    }

    /** 同步包的编解码保持次数与专属胜利标记；不同进度互不相等。 */
    private static void packet(SceneContext ctx) {
        for (WinefoxProgressMessage original : List.of(
                new WinefoxProgressMessage(0, false), new WinefoxProgressMessage(2, false),
                new WinefoxProgressMessage(3, true))) {
            ByteBuf buffer = Unpooled.buffer();
            WinefoxProgressMessage.STREAM_CODEC.encode(buffer, original);
            ctx.check(WinefoxProgressMessage.STREAM_CODEC.decode(buffer)).as("往返 " + original).isEqualTo(original);
            ctx.check(buffer.readableBytes()).as("解码后没有多余字节").isEqualTo(0);
            buffer.release();
        }
    }

    /** 进度、已听教学写进存档 NBT 再读回来不丢；别的玩家不受影响。 */
    @SuppressWarnings("unchecked")
    private static void savedData(SceneContext ctx) {
        MinecraftServer server = ctx.server();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        WinefoxChallengeProgress.setProgress(server, player, 3, true);
        WinefoxChallengeProgress.markVoidLessonSeen(server, player);

        Class<?> type = WinefoxChallengeProgress.class;
        Object live = Reflect.call(null, type, "get", new Class<?>[]{MinecraftServer.class}, server);
        HolderLookup.Provider registries = server.registryAccess();
        CompoundTag saved = ((net.minecraft.world.level.saveddata.SavedData) live).save(new CompoundTag(), registries);
        Object reloaded = Reflect.call(null, type, "load", new Class<?>[]{CompoundTag.class, HolderLookup.Provider.class}, saved, registries);

        Map<UUID, Integer> wins = Reflect.field(reloaded, type, "wins");
        java.util.Set<UUID> soloWins = Reflect.field(reloaded, type, "soloWins");
        java.util.Set<UUID> seen = Reflect.field(reloaded, type, "voidLessonSeen");
        ctx.check(wins.get(player)).as("读回的胜利次数").isEqualTo(3);
        ctx.check(soloWins.contains(player)).as("读回的专属胜利").isTrue();
        ctx.check(seen.contains(player)).as("读回的已听教学").isTrue();
        ctx.check(wins.containsKey(other) || soloWins.contains(other) || seen.contains(other)).as("别的玩家有记录").isFalse();
    }

    /** 成就挂在星途终岸之下、隐藏、图标是星锚珍珠，数据包加载后能按 id 取到。 */
    private static void advancementDefinition(SceneContext ctx) {
        AdvancementHolder holder = ctx.server().getAdvancements().get(VOID_SECRET);
        ctx.check(holder != null).as("数据包里有 star_journey/void_secret").isTrue();
        if (holder == null) {
            return;
        }
        ctx.check(holder.value().parent().map(ResourceLocation::toString).orElse("")).as("父成就")
                .isEqualTo("touhou_little_maid_spell:star_journey/stellar_endshore");
        var display = holder.value().display();
        ctx.check(display.isPresent()).as("有显示信息").isTrue();
        display.ifPresent(info -> {
            ctx.check(info.isHidden()).as("隐藏成就").isTrue();
            ctx.check(info.shouldShowToast()).as("弹 toast").isTrue();
            ctx.check(info.shouldAnnounceChat()).as("播报到聊天栏").isTrue();
            ctx.check(info.getIcon().getItem().builtInRegistryHolder().key().location().toString()).as("图标")
                    .isEqualTo(NS + "staranchor_pearl");
        });
        for (String code : List.of("zh_cn", "en_us", "ja_jp")) {
            Map<String, String> lang = Lang.read(code);
            ctx.check(lang.containsKey("advancements.touhou_little_maid_spell.star_journey.void_secret.title")).as(code + " 成就标题").isTrue();
            ctx.check(lang.containsKey("advancements.touhou_little_maid_spell.star_journey.void_secret.description")).as(code + " 成就描述").isTrue();
        }
    }

    private static boolean granted(ServerPlayer player) {
        AdvancementHolder holder = player.server.getAdvancements().get(VOID_SECRET);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static ServerPlayer freshPlayer(SceneContext ctx) {
        return Owners.visitor(ctx, "Void" + UUID.randomUUID().toString().substring(0, 8), 0, 0, 0);
    }

    /** 授予一次后成就完成，再授予不报错、不改变状态。 */
    private static void grantTwice(SceneContext ctx) {
        ServerPlayer player = freshPlayer(ctx);
        ctx.check(granted(player)).as("授予前").isFalse();
        WinefoxAdvancements.grantVoidSecret(player);
        ctx.check(granted(player)).as("授予后").isTrue();
        WinefoxAdvancements.grantVoidSecret(player);
        ctx.check(granted(player)).as("重复授予后").isTrue();
        AdvancementHolder holder = player.server.getAdvancements().get(VOID_SECRET);
        if (holder != null) {
            player.getAdvancements().getOrStartProgress(holder).getCompletedCriteria()
                    .forEach(criterion -> player.getAdvancements().revoke(holder, criterion));
        }
    }

    /**
     * 挑战者没跑远却 30 秒没有目标：二阶段同样判负，授予成就；一阶段不授予。
     * 直接把无目标计数拨到宽限的最后一 tick，再跑一次收场判断。
     */
    private static void thirtySecondLoss(SceneContext ctx, boolean phaseTwo) {
        ServerPlayer player = freshPlayer(ctx);
        MagicalWinefoxBossEntity boss = Actors.spawn(ctx, NS + "stellar_witch", 4, 0, 0, true);
        Class<?> type = MagicalWinefoxBossEntity.class;
        EntityDataAccessor<Boolean> phaseTwoData = Reflect.field(null, type, "PHASE_TWO");
        boss.getEntityData().set(phaseTwoData, phaseTwo);
        Reflect.set(boss, type, "challengerId", player.getUUID());
        int grace = Reflect.field(null, type, "BATTLE_OVER_GRACE_TICKS");
        Reflect.set(boss, type, "noTargetTicks", grace - 1);
        boss.setTarget(null);

        Reflect.call(boss, type, "tickBattleOver", new Class<?>[0]);

        ctx.check(granted(player)).as(phaseTwo ? "二阶段 30 秒判负后成就" : "一阶段 30 秒判负后成就").isEqualTo(phaseTwo);
        ctx.record("lessonSeen", WinefoxChallengeProgress.hasSeenVoidLesson(ctx.server(), player.getUUID()));
        AdvancementHolder holder = player.server.getAdvancements().get(VOID_SECRET);
        if (holder != null) {
            player.getAdvancements().getOrStartProgress(holder).getCompletedCriteria()
                    .forEach(criterion -> player.getAdvancements().revoke(holder, criterion));
        }
    }

    /** 教学四句与落败台词在三种语言里都有；简中的教学第一句不再与任何一句落败台词相同；已删的语言键不再出现。 */
    private static void lessonLang(SceneContext ctx) {
        String lesson = "dialogue.touhou_little_maid_spell.winefox.void_lesson_";
        String lost = "dialogue.touhou_little_maid_spell.winefox.challenge_lost_";
        for (String code : List.of("zh_cn", "en_us", "ja_jp")) {
            Map<String, String> lang = Lang.read(code);
            for (int i = 1; i <= 4; i++) {
                ctx.check(lang.containsKey(lesson + i)).as(code + " 教学第 " + i + " 句").isTrue();
            }
            ctx.check(lang.containsKey("entity.touhou_little_maid_spell.stellar_witch.vow_conditions_unmet")).as(code + " 已删除的语言键").isFalse();
            String first = lang.get(lesson + 1);
            List<String> same = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                if (first != null && first.equals(lang.get(lost + i))) {
                    same.add(lost + i);
                }
            }
            ctx.check(same).as(code + " 教学第一句与相同的落败台词").isEmpty();
        }
    }
}
