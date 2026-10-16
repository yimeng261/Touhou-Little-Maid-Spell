package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PalmBlackHoleTimelineTest {
    @Test void blastWarningCoversTheLastFiveSecondsAndStopsAtImpact() {
        assertEquals(16.0F, PalmBlackHoleTimeline.BLAST_RADIUS);
        assertFalse(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.WARNING_START_TICK - 0.01));
        assertTrue(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.WARNING_START_TICK));
        assertTrue(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.IMPACT_TICK - 0.01));
        assertFalse(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.IMPACT_TICK));
        int warningTicks = 0;
        for (int age = 0; age <= PalmBlackHoleTimeline.IMPACT_TICK + PalmBlackHoleTimeline.WAVE_TICKS; age++) {
            if (PalmBlackHoleTimeline.isWarningActive(age)) warningTicks++;
        }
        assertEquals(5 * 20, warningTicks);
    }

    @Test void pulsesStartAtTwoAndAHalfSecondsAndRepeatOncePerSecondUntilImpact() {
        var pulses = new java.util.ArrayList<Integer>();
        for (int age = 0; age <= PalmBlackHoleTimeline.IMPACT_TICK + 20; age++) {
            if (PalmBlackHoleTimeline.isPulseTick(age)) pulses.add(age);
        }
        assertEquals(15, pulses.size());
        assertEquals(50, pulses.get(0));
        assertEquals(330, pulses.get(pulses.size() - 1));
        for (int i = 1; i < pulses.size(); i++) assertEquals(20, pulses.get(i) - pulses.get(i - 1));
        assertFalse(PalmBlackHoleTimeline.isPulseTick(PalmBlackHoleTimeline.IMPACT_TICK));
    }

    @Test void damageUsesEachVictimsMaximumHealth() {
        assertEquals(4, PalmBlackHoleTimeline.pulseDamage(20), 1.0E-5);
        assertEquals(14, PalmBlackHoleTimeline.explosionDamage(20), 1.0E-5);
        assertEquals(12, PalmBlackHoleTimeline.pulseDamage(100), 1.0E-5);
        assertEquals(30, PalmBlackHoleTimeline.explosionDamage(100), 1.0E-5);
        assertEquals(102, PalmBlackHoleTimeline.pulseDamage(1000), 1.0E-5);
        assertEquals(210, PalmBlackHoleTimeline.explosionDamage(1000), 1.0E-5);
    }

    @Test void pulseUsesARoundTenBlockRadiusRatherThanTheQueryBoxCorners() {
        assertTrue(PalmBlackHoleTimeline.insidePulse(0, 0));
        assertTrue(PalmBlackHoleTimeline.insidePulse(10, 0));
        assertTrue(PalmBlackHoleTimeline.insidePulse(6, 8));
        assertFalse(PalmBlackHoleTimeline.insidePulse(10.01, 0));
        assertFalse(PalmBlackHoleTimeline.insidePulse(8, 8));
    }
    @Test void locatorReleaseHoldsThreeSecondsThenFallsToGroundInOneSecond() {
        assertEquals(260, PalmBlackHoleTimeline.TOP_TICK);
        assertEquals(60, PalmBlackHoleTimeline.DROP_TICK - PalmBlackHoleTimeline.TOP_TICK);
        assertEquals(20, PalmBlackHoleTimeline.IMPACT_TICK - PalmBlackHoleTimeline.DROP_TICK);
        for (double topHeight : new double[]{1, 4, 20}) {
            for (int tick = 260; tick <= 320; tick++) {
                assertEquals(topHeight, PalmBlackHoleTimeline.dropHeight(tick, topHeight));
            }
            double previous = topHeight;
            for (int tick = 320; tick <= 340; tick++) {
                double height = PalmBlackHoleTimeline.dropHeight(tick, topHeight);
                assertTrue(height <= previous && height >= 0);
                previous = height;
            }
            assertEquals(0, previous);
        }
    }

    @Test void finalLocatorOffsetIsStableAndRotatesWithTheWitch() {
        var front = PalmBlackHoleLocatorPose.topOffset(0);
        var reverse = PalmBlackHoleLocatorPose.topOffset(180);
        assertTrue(front.isFinite());
        assertTrue(front.y > 1);
        assertEquals(front.y, reverse.y, 1.0E-5F);
        assertEquals(front.x, -reverse.x, 1.0E-5F);
        assertEquals(front.z, -reverse.z, 1.0E-5F);
        assertEquals(front, PalmBlackHoleLocatorPose.topOffset(0));
    }

    @Test void detachedPhaseRetainsTheReleaseScaleBeforeTheMarkerDisappears() {
        var scale = PalmBlackHoleLocatorPose.topPose(0).getScale(new org.joml.Vector3f());
        assertEquals(PalmBlackHoleTimeline.MAX_MODEL_SCALE * 0.7F,
                scale.x * PalmBlackHoleTimeline.LOCATOR_SCALE_MULTIPLIER, 1.0E-5F);
        assertEquals(scale.x, scale.y, 1.0E-5F);
        assertEquals(scale.x, scale.z, 1.0E-5F);
    }

    @Test void expandingWaveSweepsEveryDistanceOnceWhenHitsAreRemembered() {
        assertEquals(0, PalmBlackHoleTimeline.waveRadius(339));
        assertEquals(8, PalmBlackHoleTimeline.waveRadius(350));
        assertEquals(16, PalmBlackHoleTimeline.waveRadius(360));
        for (double distance = 0; distance <= 17; distance += 0.25) {
            int firstHit = -1;
            for (int tick = 340; tick <= 360; tick++) {
                if (firstHit < 0 && PalmBlackHoleTimeline.crossedWave(distance,
                        PalmBlackHoleTimeline.waveRadius(tick - 1), PalmBlackHoleTimeline.waveRadius(tick))) {
                    firstHit = tick;
                }
            }
            assertEquals(distance <= 16, firstHit >= 0, "distance=" + distance);
            if (distance > 9 && distance <= 16) assertTrue(firstHit > 350);
        }
    }
}
