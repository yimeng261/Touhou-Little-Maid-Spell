package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * 管理星途终岸结构内的常驻 BGM。战斗音乐优先；进入战斗时淡出常驻音乐。
 * 播放本模组音乐期间屏蔽其它 MUSIC 声音，但保留两条酒狐曲目。
 */
public final class WinefoxSeatedAmbienceController {

    /** 结构内寻找酒狐声源的监听半径。 */
    private static final double LISTEN_RANGE = 96.0D;

    /** 由服务端按结构边界同步，避免客户端区块包缺少结构引用。 */
    private static boolean insideStellarEndshore;

    @Nullable
    private static SoundInstance current;

    private WinefoxSeatedAmbienceController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }

        if (current != null && (!insideStellarEndshore || hasBattleMusic(minecraft)
                || !minecraft.getSoundManager().isActive(current))) {
            stopCurrent(minecraft);
        }

        if (!insideStellarEndshore) {
            return;
        }
        if (minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F
            || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F) {
            return;
        }
        if (current != null && minecraft.getSoundManager().isActive(current)) {
            return;
        }
        // 原版 MusicManager 可能已经有一首随机音乐在播放；先停掉它，再启动结构音乐。
        minecraft.getMusicManager().stopPlaying();
        current = new WinefoxSeatedAmbienceSoundInstance(seatedOrigin(minecraft));
        minecraft.getSoundManager().play(current);
    }

    /** 常驻或战斗音乐播放时阻止其它 MUSIC 声音叠加。 */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!isOurMusicPlaying()) {
            return;
        }
        SoundInstance sound = event.getSound();
        if (sound != null && sound.getSource() == SoundSource.MUSIC
            && sound != current && sound != WinefoxBossMusicController.currentMusic()) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        insideStellarEndshore = false;
        stopCurrent(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        insideStellarEndshore = false;
        stopCurrent(Minecraft.getInstance());
    }

    /** 接收服务端的结构内状态；只在客户端网络线程上调用。 */
    public static void setInsideStellarEndshore(boolean inside) {
        insideStellarEndshore = inside;
    }

    /** 战斗控制器启动战斗曲时立即让常驻曲退场，避免同一 tick 内短暂叠音。 */
    static void stopForBattle(Minecraft minecraft) {
        stopCurrent(minecraft);
    }

    /**
     * 「现在有没有我方的音乐在响」——两套 BGM 的实例一起问。
     *
     * <p>这是 {@link WinefoxBossMusicController#onPlaySound} 与 {@link #onPlaySound}
     * 共用的静音闸门开关：只看自己那一份的话，另一个在响的时候原版音乐照样能挤进来。
     */
    static boolean isOurMusicPlaying() {
        Minecraft minecraft = Minecraft.getInstance();
        return (current != null && minecraft.getSoundManager().isActive(current))
            || (WinefoxBossMusicController.currentMusic() != null
                && minecraft.getSoundManager().isActive(WinefoxBossMusicController.currentMusic()));
    }

    /**
     * 任何时候只要有一只酒狐在放战斗音乐，常驻 BGM 就得退场。这里故意不加距离条件：
     * 战斗 BGM 生效的距离是 96 格，常驻 BGM 那 48/64 格的音量轴完全包在里面，
     * 用同一个 96 格去问「战斗音乐是不是来了」不会漏判。
     */
    private static boolean hasBattleMusic(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return false;
        }
        return !minecraft.level.getEntitiesOfClass(MagicalWinefoxBossEntity.class,
                player.getBoundingBox().inflate(LISTEN_RANGE),
                 boss -> !boss.isRemoved() && boss.isBattleMusicActive())
            .isEmpty();
    }

    /**
     * 拿附近第一只坐着的酒狐当声源，找不到就退回玩家自己。
     *
     * <p>退回值是必要的：结构判定与这里之间有极小的一帧竞态
     * （实体正在被移除、状态刚翻成 COMBAT），返回玩家位置只会让音量算成满格一瞬，
     * 下一 tick 就被撤下，比抛空指针好得多。
     */
    private static Vec3 seatedOrigin(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return Vec3.ZERO;
        }
        return minecraft.level.getEntitiesOfClass(MagicalWinefoxBossEntity.class,
                player.getBoundingBox().inflate(LISTEN_RANGE),
                 boss -> !boss.isRemoved() && boss.isSeated() && !boss.isBattleMusicActive())
            .stream()
            .findFirst()
            .map(boss -> boss.position())
            .orElseGet(player::position);
    }

    private static void stopCurrent(Minecraft minecraft) {
        if (current != null) {
            minecraft.getSoundManager().stop(current);
            current = null;
        }
    }

}
