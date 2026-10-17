package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.sound.MaidSpellSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 酒狐坐姿时循环播放的距离音效，随玩家距离调节音量并支持淡出。
 * 使用 MUSIC 音源以遵循音乐音量设置。
 */
public final class WinefoxSeatedAmbienceSoundInstance extends AbstractTickableSoundInstance {

    /** 满音量半径。 */
    private static final double MAX_VOLUME_RANGE = 48.0D;
    private static final double MAX_VOLUME_RANGE_SQR = MAX_VOLUME_RANGE * MAX_VOLUME_RANGE;
    /** 静音半径。 */
    private static final double SILENT_RANGE = 64.0D;
    private static final double SILENT_RANGE_SQR = SILENT_RANGE * SILENT_RANGE;

    /** 每 tick 的淡出量。 */
    private static final float END_TRANSITION_STEP = 1.0F / 100.0F;

    /** 她坐的那把秋千。坐姿期间她被 {@code tickSeatedAnchor} 钉在原地，所以取一次就够。 */
    private final Vec3 origin;
    private boolean ending;

    WinefoxSeatedAmbienceSoundInstance(Vec3 origin) {
        super(MaidSpellSounds.WINEFOX_STARFALL_ENDSHORE_BGM.get(), SoundSource.MUSIC,
                SoundInstance.createUnseededRandom());
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = true;
        this.delay = 0;
        // 允许从静音启动，下一 tick 再按距离计算音量。
        this.volume = 0.0F;
        this.origin = origin;
    }

    @Override
    public void tick() {
        if (ending) {
            this.volume -= END_TRANSITION_STEP;
            if (this.volume <= 0.0F) {
                this.stop();
            }
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            double distanceSqr = player.distanceToSqr(this.origin);
            this.volume = 1.0F - (float) Mth.clamp(
                    (distanceSqr - MAX_VOLUME_RANGE_SQR) / SILENT_RANGE_SQR, 0.0D, 1.0D);
        }
    }

    /** 允许在音量为 0 时开始播放。 */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** 幂等地开始淡出。 */
    void triggerStop() {
        this.ending = true;
    }
}
