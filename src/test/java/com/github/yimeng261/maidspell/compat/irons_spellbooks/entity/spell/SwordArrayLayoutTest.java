package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwordArrayLayoutTest {
    @Test
    void retainsSwordCountsAndIndependentFiveTickRingTiming() {
        int[] totals = {18, 34, 55, 81, 112, 148};
        for (int level = 1; level <= 6; level++) {
            SwordArrayLayout layout = new SwordArrayLayout(level, SwordArrayLayout.innerRadius(0.3D), 123L);
            assertEquals(totals[level - 1], layout.swords().size());
            for (SwordArrayLayout.Sword sword : layout.swords()) {
                assertEquals(sword.ring() * 5, sword.launchAge());
                assertFalse(sword.visible(sword.launchAge() - 0.01D));
                assertTrue(sword.visible(sword.launchAge()));
            }
        }
        assertEquals(48, SwordArrayLayout.swordsForRadius(100));
    }

    @Test
    void trajectoriesMatchTheOldPerTickGravityAndStopExactlyAtTheTargetPlane() {
        for (long seed = 0; seed < 32; seed++) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, seed);
            for (SwordArrayLayout.Sword sword : layout.swords()) {
                Vec3 point = sword.position(sword.launchAge());
                Vec3 velocity = sword.velocity(sword.launchAge());
                assertEquals(1.45F, velocity.length(), 1.0E-12D);
                for (int age = sword.launchAge() + 1; age <= sword.landingAge(); age++) {
                    Vec3 next = point.add(velocity);
                    if (next.y <= 0.0D) {
                        double fraction = point.y / (point.y - next.y);
                        Vec3 hit = point.add(velocity.scale(fraction));
                        next = new Vec3(hit.x, 0, hit.z);
                        assertEquals(sword.landingAge(), age);
                    }
                    assertPoint(next, sword.position(age));
                    assertPoint(point.lerp(next, 0.5D), sword.position(age - 0.5D));
                    assertTrue(layout.bounds().contains(next));
                    point = next;
                    velocity = velocity.add(0, -0.05D, 0);
                }
                assertEquals(0.0D, point.y);
                assertPoint(point, sword.position(sword.expiryAge()));
            }
        }
    }

    @Test
    void lifetimeScalesWithLevelAndKeepsSeparateContactHitsThroughTheFinalTick() {
        assertEquals(8, SwordArrayLayout.DAMAGE_INTERVAL);
        for (int level = 1; level <= 6; level++) {
            SwordArrayLayout layout = new SwordArrayLayout(level, 1.25D, 42L);
            int latestExpiry = 0;
            for (SwordArrayLayout.Sword sword : layout.swords()) {
                assertEquals(80 + level * 4, sword.expiryAge() - sword.landingAge());
                latestExpiry = Math.max(latestExpiry, sword.expiryAge());
                int hits = 0;
                for (int age = 0; age <= layout.lifetime() + 1; age++) {
                    if (sword.dealsContactDamage(age)) {
                        hits++;
                        assertEquals(sword.landingAge() + hits * SwordArrayLayout.DAMAGE_INTERVAL, age);
                    }
                }
                assertEquals((80 + level * 4) / SwordArrayLayout.DAMAGE_INTERVAL, hits);
                assertFalse(sword.dealsContactDamage(sword.landingAge()));
                int lastDamageAge = sword.landingAge()
                        + ((80 + level * 4) / SwordArrayLayout.DAMAGE_INTERVAL)
                        * SwordArrayLayout.DAMAGE_INTERVAL;
                assertTrue(sword.dealsContactDamage(lastDamageAge));
                assertEquals((80 + level * 4) % 8 == 0, sword.dealsContactDamage(sword.expiryAge()));
                assertFalse(sword.dealsContactDamage(sword.landingAge() + 4));
                assertFalse(sword.visible(sword.expiryAge()));
                assertFalse(sword.dealsContactDamage(sword.expiryAge() + SwordArrayLayout.DAMAGE_INTERVAL));
            }
            assertEquals(latestExpiry, layout.lifetime());
        }
    }

    @Test
    void exactlyOneCentreSwordFallsVerticallyWithTheInnermostRing() {
        for (long seed = 0; seed < 32; seed++) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 3.5D, seed);
            int centreCount = 0;
            for (var sword : layout.swords()) {
                Vec3 landing = sword.position(sword.landingAge());
                if (landing.x == 0 && landing.z == 0) {
                    centreCount++;
                    assertEquals(0, sword.launchAge());
                    assertEquals(104, sword.expiryAge() - sword.landingAge());
                    for (int age = 0; age <= sword.landingAge(); age++) {
                        assertEquals(0.0D, sword.position(age).x);
                        assertEquals(0.0D, sword.position(age).z);
                    }
                    assertTrue(sword.dealsContactDamage(sword.landingAge() + SwordArrayLayout.DAMAGE_INTERVAL));
                }
            }
            assertEquals(1, centreCount);
        }
    }

    @Test
    void lateTrackingAndReloadReconstructTheSameCurrentStateFromTheDescriptor() {
        SwordArrayLayout server = new SwordArrayLayout(6, 2.5D, 8675309L);
        SwordArrayLayout client = new SwordArrayLayout(6, 2.5D, 8675309L);
        assertEquals(server.lifetime(), client.lifetime());
        for (int i = 0; i < server.swords().size(); i++) {
            var a = server.swords().get(i);
            var b = client.swords().get(i);
            assertEquals(a.roll(), b.roll());
            assertEquals(a.landingAge(), b.landingAge());
            for (double age : new double[]{0, 5.5, 28, 54.25, 90, 200}) {
                assertPoint(a.position(age), b.position(age));
                assertEquals(a.visible(age), b.visible(age));
            }
        }
    }

    private static void assertPoint(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-12D);
        assertEquals(expected.y, actual.y, 1.0E-12D);
        assertEquals(expected.z, actual.z, 1.0E-12D);
    }
}
