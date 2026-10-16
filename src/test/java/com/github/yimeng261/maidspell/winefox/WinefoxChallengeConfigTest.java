package com.github.yimeng261.maidspell.winefox;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WinefoxChallengeConfigTest {
    @Test
    void armorSurvivesTagAndNetworkRoundTripsAlongsideOtherSettings() {
        WinefoxChallengeConfig config = config(18.0D);
        assertEquals(config, WinefoxChallengeConfig.fromTag(config.toTag()));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            config.writeToBuffer(buffer);
            assertEquals(config, WinefoxChallengeConfig.fromBuffer(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void legacyChallengeAndPresetTagsUseDefaultArmor() {
        CompoundTag legacy = config(18.0D).toTag();
        legacy.remove("Armor");

        assertEquals(12.0D, WinefoxChallengeConfig.defaults().armor());
        assertEquals(config(12.0D), WinefoxChallengeConfig.fromTag(legacy));
    }

    @Test
    void armorUsesHalfTheServerDefaultAsMinimumAndVanillaAttributeMaximum() {
        WinefoxChallengeConfig defaults = config(12.0D);
        assertEquals(6.0D, WinefoxChallengeConfig.minimumBasicValue(1, defaults));
        assertEquals(6.0D, config(-1.0D).sanitized(defaults).armor());
        assertEquals(6.0D, config(Double.NaN).sanitized(defaults).armor());
        assertEquals(30.0D, config(100.0D).sanitized(defaults).armor());
        assertEquals(9.0D, config(0.0D).sanitized(config(18.0D)).armor());
    }

    @Test
    void insertingArmorKeepsExistingBasicLimitsMappedToTheirSettings() {
        WinefoxChallengeConfig defaults = config(12.0D);
        assertEquals(450.0D, WinefoxChallengeConfig.minimumBasicValue(0, defaults));
        assertEquals(0.7D, WinefoxChallengeConfig.minimumBasicValue(2, defaults));
        assertEquals(1.15D, WinefoxChallengeConfig.minimumBasicValue(3, defaults));
        assertEquals(0.06D, WinefoxChallengeConfig.minimumBasicValue(4, defaults));
        assertEquals(5.0D, WinefoxChallengeConfig.minimumBasicValue(5, defaults));
        assertEquals(0.3D, WinefoxChallengeConfig.minimumBasicValue(6, defaults));
        assertEquals(3.5D, WinefoxChallengeConfig.minimumBasicValue(7, defaults));
        assertEquals(0.45D, WinefoxChallengeConfig.minimumBasicValue(8, defaults));
    }

    @Test
    void presetNetworkRoundTripRetainsArmorAndEmptySlots() {
        List<WinefoxChallengeConfig> presets = WinefoxChallengePresets.empty();
        presets.set(0, config(18.0D));
        presets.set(2, config(24.0D));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WinefoxChallengePresets.writeBuffer(buffer, presets);
            List<WinefoxChallengeConfig> restored = WinefoxChallengePresets.readBuffer(buffer);
            assertEquals(presets, restored);
            assertNull(restored.get(1));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static WinefoxChallengeConfig config(double armor) {
        return new WinefoxChallengeConfig(900.0D, armor, 1.4D, 2.3D, 0.12D, 9,
                0.6D, 7.0D, 0.9D, true, false, 40,
                List.of(new WinefoxSpellChoice("irons_spellbooks:magic_missile", 7)),
                List.of(new WinefoxSpellChoice("irons_spellbooks:heal", 4)), true);
    }
}
