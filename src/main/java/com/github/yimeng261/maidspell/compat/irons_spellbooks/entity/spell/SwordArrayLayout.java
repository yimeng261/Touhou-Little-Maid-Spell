package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Immutable trajectories shared by server collision checks and client rendering. */
public final class SwordArrayLayout {
    public static final int RING_INTERVAL = 5;
    private static final int BASE_PLANTED_LIFETIME = 80;
    private static final int PLANTED_TICKS_PER_LEVEL = 4;
    public static final int DAMAGE_INTERVAL = 8;
    public static final double RADIUS_STEP = 1.2D;
    public static final double SWORD_WIDTH = 0.35F;
    public static final double CONTACT_INFLATION = 0.45D;
    private static final double SPEED = 1.45F;
    private static final double GRAVITY = 0.05D;

    private final List<Sword> swords;
    private final AABB bounds;
    private final int lifetime;

    public SwordArrayLayout(int spellLevel, double innerRadius, long seed) {
        int rings = ringCount(spellLevel);
        int plantedLifetime = plantedLifetime(spellLevel);
        Random random = new Random(seed);
        double baseAngle = random.nextDouble() * Math.PI * 2.0D;
        List<Sword> result = new ArrayList<>();
        AABB area = null;
        int end = 0;
        for (int ring = 0; ring < rings; ring++) {
            double radius = innerRadius + ring * RADIUS_STEP;
            int count = swordsForRadius(radius);
            double offset = baseAngle + ring * Math.PI / count;
            for (int i = 0; i < count; i++) {
                double angle = offset + Math.PI * 2.0D * i / count;
                Vec3 landing = new Vec3(Math.cos(angle) * radius + jitter(random), 0,
                        Math.sin(angle) * radius + jitter(random));
                Vec3 spawn = landing.add((random.nextDouble() - 0.5D) * 1.6D,
                        8.0D + random.nextDouble() * 1.5D, (random.nextDouble() - 0.5D) * 1.6D);
                Sword sword = new Sword(ring, spawn, landing.subtract(spawn).normalize().scale(SPEED),
                        random.nextFloat() * 360.0F, plantedLifetime);
                result.add(sword);
            }
        }
        // Append the centre sword so existing ring trajectories and sword indices stay stable.
        result.add(new Sword(0, new Vec3(0, 8.0D + random.nextDouble() * 1.5D, 0),
                new Vec3(0, -SPEED, 0), random.nextFloat() * 360.0F, plantedLifetime));
        for (Sword sword : result) {
            end = Math.max(end, sword.expiryAge());
            for (Vec3 position : sword.positions) {
                AABB box = swordBox(position).inflate(1.0D);
                area = area == null ? box : area.minmax(box);
            }
        }
        swords = List.copyOf(result);
        bounds = area == null ? new AABB(Vec3.ZERO, Vec3.ZERO) : area;
        lifetime = end;
    }

    private static double jitter(Random random) {
        return (random.nextDouble() - 0.5D) * 0.24D;
    }

    public static int ringCount(int spellLevel) {
        return Math.max(1, spellLevel) + 1;
    }

    public static int plantedLifetime(int spellLevel) {
        return BASE_PLANTED_LIFETIME + Math.max(1, spellLevel) * PLANTED_TICKS_PER_LEVEL;
    }

    public static int swordsForRadius(double radius) {
        return Math.max(3, Math.min(48, (int) Math.ceil(Math.PI * 2.0D * radius / 1.5D)));
    }

    public static double innerRadius(double targetHalfWidth) {
        return Math.max(1.2D, targetHalfWidth * 2.0D + 0.65D);
    }

    public static AABB swordBox(Vec3 position) {
        double half = SWORD_WIDTH * 0.5D;
        return new AABB(position.x - half, position.y, position.z - half,
                position.x + half, position.y + SWORD_WIDTH, position.z + half);
    }

    public List<Sword> swords() {
        return swords;
    }

    public AABB bounds() {
        return bounds;
    }

    public int lifetime() {
        return lifetime;
    }

    public static final class Sword {
        private final int ring;
        private final Vec3[] positions;
        private final Vec3 initialVelocity;
        private final Vec3 plantedVelocity;
        private final float roll;
        private final int plantedLifetime;

        private Sword(int ring, Vec3 spawn, Vec3 velocity, float roll, int plantedLifetime) {
            this.ring = ring;
            this.initialVelocity = velocity;
            this.roll = roll;
            this.plantedLifetime = plantedLifetime;
            List<Vec3> path = new ArrayList<>();
            path.add(spawn);
            Vec3 position = spawn;
            Vec3 movement = velocity;
            while (true) {
                Vec3 next = position.add(movement);
                if (next.y <= 0.0D) {
                    double fraction = position.y / (position.y - next.y);
                    Vec3 landing = position.add(movement.scale(fraction));
                    path.add(new Vec3(landing.x, 0.0D, landing.z));
                    break;
                }
                path.add(next);
                position = next;
                movement = movement.add(0.0D, -GRAVITY, 0.0D);
            }
            plantedVelocity = movement;
            positions = path.toArray(Vec3[]::new);
        }

        public int ring() {
            return ring;
        }

        public int launchAge() {
            return ring * RING_INTERVAL;
        }

        public int landingAge() {
            return launchAge() + positions.length - 1;
        }

        public int expiryAge() {
            return landingAge() + plantedLifetime;
        }

        public boolean visible(double age) {
            return age >= launchAge() && age < expiryAge();
        }

        public boolean flying(int age) {
            return age > launchAge() && age <= landingAge();
        }

        public boolean dealsContactDamage(int age) {
            int plantedAge = age - landingAge();
            // Apply the final contact hit before the sword expires.
            return plantedAge > 0 && plantedAge <= plantedLifetime
                    && plantedAge % DAMAGE_INTERVAL == 0;
        }

        public Vec3 position(double age) {
            double time = Math.max(0.0D, Math.min(positions.length - 1, age - launchAge()));
            int step = (int) time;
            if (step >= positions.length - 1) return positions[positions.length - 1];
            return positions[step].lerp(positions[step + 1], time - step);
        }

        public Vec3 velocity(double age) {
            int step = Math.max(0, (int) Math.floor(age - launchAge()));
            return age >= landingAge() ? plantedVelocity
                    : initialVelocity.add(0.0D, -GRAVITY * step, 0.0D);
        }

        public float roll() {
            return roll;
        }
    }
}
