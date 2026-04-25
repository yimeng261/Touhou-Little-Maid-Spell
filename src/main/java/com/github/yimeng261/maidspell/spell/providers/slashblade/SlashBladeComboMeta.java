package com.github.yimeng261.maidspell.spell.providers.slashblade;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

public final class SlashBladeComboMeta {
    public enum ComboPhase {
        WINDUP,
        TRANSITION,
        ATTACK,
        RECOVERY
    }

    public record Entry(ComboPhase phase, int firstAttackTick, int lastAttackTick, int cancelAfterTicks) {
        public boolean canCancel(long comboElapsedTicks) {
            if (phase == ComboPhase.RECOVERY) {
                return comboElapsedTicks >= cancelAfterTicks;
            }
            return lastAttackTick >= 0 && comboElapsedTicks > lastAttackTick + cancelAfterTicks;
        }

        public ComboPhase phaseAt(long comboElapsedTicks) {
            if (phase != ComboPhase.ATTACK || firstAttackTick < 0 || lastAttackTick < 0) {
                return phase;
            }
            if (comboElapsedTicks < firstAttackTick) {
                return ComboPhase.WINDUP;
            }
            if (comboElapsedTicks <= lastAttackTick) {
                return ComboPhase.ATTACK;
            }
            return ComboPhase.RECOVERY;
        }
    }

    private static final Entry UNKNOWN = new Entry(ComboPhase.WINDUP, -1, -1, 0);
    private static final Map<String, Entry> META = createMeta();

    private SlashBladeComboMeta() {
    }

    public static Entry get(ResourceLocation combo) {
        if (combo == null) {
            return UNKNOWN;
        }
        return META.getOrDefault(combo.toString(), META.getOrDefault(combo.getPath(), UNKNOWN));
    }

    private static Map<String, Entry> createMeta() {
        Map<String, Entry> meta = new HashMap<>();
        addVanillaSlashBlade(meta);
        addSlashBladeJapaneseAddon(meta);
        addShizukuBlade(meta);
        addMurasame(meta);
        return Collections.unmodifiableMap(meta);
    }

    private static void addVanillaSlashBlade(Map<String, Entry> meta) {
        add(meta, ComboPhase.ATTACK, -1, -1, 0,
                "judgement_cut", "judgement_cut_slash", "judgement_cut_slash_air",
                "judgement_cut_slash_just", "judgement_cut_slash_just2", "judgement_cut_end",
                "void_slash", "sakura_end_left", "sakura_end_right", "sakura_end_left_air",
                "sakura_end_right_air", "circle_slash", "drive_horizontal", "drive_vertical",
                "piercing", "piercing_2", "piercing_just");
        add(meta, ComboPhase.TRANSITION, -1, -1, 0,
                "sakura_end_finish", "sakura_end_finish_air", "circle_slash_end", "piercing_end");
        add(meta, ComboPhase.RECOVERY, -1, -1, 1,
                "judgement_cut_sheath", "judgement_cut_sheath_air", "judgement_cut_slash_just_sheath",
                "void_slash_sheath", "sakura_end_finish2", "sakura_end_finish2_air",
                "circle_slash_end2", "drive_horizontal_end", "drive_vertical_end", "piercing_end2");
    }

    private static void addSlashBladeJapaneseAddon(Map<String, Entry> meta) {
        add(meta, "slashblade_addon", ComboPhase.ATTACK, 2, 3, 1,
                "rapid_blistering_swords", "gale_swords", "lighting_swords", "water_drive");
        add(meta, "slashblade_addon", ComboPhase.ATTACK, 4, 7, 1,
                "spiral_edge", "fire_spiral");
        add(meta, "slashblade_addon", ComboPhase.RECOVERY, -1, -1, 1,
                "rapid_blistering_swords_end", "spiral_edge_end", "gale_swords_end",
                "lighting_swords_end", "fire_spiral_end", "water_drive_end");
        add(meta, "slashblade_addon", ComboPhase.ATTACK, 2, 3, 1,
                "blistering_terra_swords");
        add(meta, "slashblade_addon", ComboPhase.RECOVERY, -1, -1, 1,
                "blistering_terra_swords_swords_end");
    }

    private static void addShizukuBlade(Map<String, Entry> meta) {
        add(meta, "shizuku", ComboPhase.ATTACK, 2, 3, 1,
                "shizukusa");
        add(meta, "shizuku", ComboPhase.ATTACK, 3, 3, 1,
                "leisa", "ymsa");
        add(meta, "shizuku", ComboPhase.RECOVERY, -1, -1, 1,
                "all_reuse");
    }

    private static void addMurasame(Map<String, Entry> meta) {
        add(meta, "murasame", ComboPhase.ATTACK, 0, 18, 6,
                "spatial_slash");
    }

    private static void add(Map<String, Entry> meta, ComboPhase phase, int firstAttackTick, int lastAttackTick, int cancelAfterTicks, String... paths) {
        add(meta, "slashblade", phase, firstAttackTick, lastAttackTick, cancelAfterTicks, paths);
    }

    private static void add(Map<String, Entry> meta, String namespace, ComboPhase phase, int firstAttackTick, int lastAttackTick, int cancelAfterTicks, String... paths) {
        Entry entry = new Entry(phase, firstAttackTick, lastAttackTick, cancelAfterTicks);
        for (String path : paths) {
            meta.put(namespace + ":" + path, entry);
            if ("slashblade".equals(namespace)) {
                meta.put(path, entry);
            }
        }
    }
}
