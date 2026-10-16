package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SwordArrayCollisionIndexTest {
    @Test
    void includesLargeTargetsAtEdgesAndDeduplicatesTargetsAcrossCells() {
        SwordArrayCollisionIndex<String> index = new SwordArrayCollisionIndex<>(new AABB(-10, -1, -10, 10, 12, 10));
        index.add("large", new AABB(-100, 0, -100, 100, 10, 100));
        index.add("negative", new AABB(-2.2, 0, -2.2, -1.8, 2, -1.8));
        assertEquals(Set.of("large", "negative"), index.query(new AABB(-2.1, 0, -2.1, -1.9, 2, -1.9)));
        assertEquals(Set.of("large"), index.query(new AABB(9, 0, 9, 11, 2, 11)));
        assertTrue(index.query(new AABB(20, 0, 20, 21, 2, 21)).isEmpty());
    }

    @Test
    void broadPhaseNeverMissesAnIntersectionComparedWithABruteForceScan() {
        AABB area = new AABB(-12, -1, -12, 12, 12, 12);
        SwordArrayCollisionIndex<Integer> index = new SwordArrayCollisionIndex<>(area);
        Random random = new Random(42);
        List<AABB> targets = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            double x = random.nextDouble() * 30 - 15;
            double z = random.nextDouble() * 30 - 15;
            AABB box = new AABB(x, 0, z, x + random.nextDouble() * 6,
                    2 + random.nextDouble() * 4, z + random.nextDouble() * 6);
            targets.add(box);
            index.add(i, box);
        }
        for (int trial = 0; trial < 500; trial++) {
            double x = random.nextDouble() * 22 - 11;
            double z = random.nextDouble() * 22 - 11;
            AABB query = new AABB(x, 0, z, x + 0.8D, 1.25D, z + 0.8D);
            Set<Integer> candidates = index.query(query);
            for (int i = 0; i < targets.size(); i++) {
                if (targets.get(i).intersects(query)) assertTrue(candidates.contains(i), "Missing target " + i);
            }
        }
    }

    @Test
    void exactContactBoxesIncludeOneCentreSwordAndRetainTheRingGaps() {
        SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, 42);
        SwordArrayCollisionIndex<Integer> index = new SwordArrayCollisionIndex<>(layout.bounds());
        AABB center = new AABB(-0.3D, 0, -0.3D, 0.3D, 1.8D, 0.3D);
        index.add(1, center);
        int centreContacts = 0;
        for (SwordArrayLayout.Sword sword : layout.swords()) {
            AABB contact = SwordArrayLayout.swordBox(sword.position(sword.landingAge()))
                    .inflate(SwordArrayLayout.CONTACT_INFLATION);
            if (contact.intersects(center)) {
                centreContacts++;
                assertEquals(Vec3.ZERO, sword.position(sword.landingAge()));
                assertTrue(index.query(contact).contains(1));
            }
        }
        assertEquals(1, centreContacts, "Only the centre sword should hit the central target");
        var sword = layout.swords().get(0);
        AABB contact = SwordArrayLayout.swordBox(sword.position(sword.landingAge()))
                .inflate(SwordArrayLayout.CONTACT_INFLATION);
        index.add(2, contact);
        assertTrue(index.query(contact).contains(2));
    }

    @Test
    void worldSpaceQueriesWorkFarFromTheOriginAndDoNotHitDistantTargets() {
        SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, 42);
        for (Vec3 center : new Vec3[]{new Vec3(246004.87, 151, -8176.63),
                new Vec3(-29000000, -30, 29000000)}) {
            SwordArrayCollisionIndex<Integer> index = new SwordArrayCollisionIndex<>(layout.bounds().move(center));
            for (int i = 0; i < layout.swords().size(); i++) {
                var sword = layout.swords().get(i);
                index.add(i, SwordArrayLayout.swordBox(sword.position(sword.landingAge()).add(center)));
            }
            for (int i = 0; i < layout.swords().size(); i++) {
                var sword = layout.swords().get(i);
                AABB query = SwordArrayLayout.swordBox(sword.position(sword.landingAge()).add(center))
                        .inflate(SwordArrayLayout.CONTACT_INFLATION);
                assertTrue(index.query(query).contains(i));
                assertTrue(index.query(query.move(1000, 0, 1000)).isEmpty());
            }
        }
    }
}
