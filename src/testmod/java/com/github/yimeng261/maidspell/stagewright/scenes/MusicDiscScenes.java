package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellCreativeTab;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星海与 Vivaldi 两张音乐唱片：物品属性、jukebox_song 数据、唱片标签、创造栏、三种语言文本、
 * 曲目时长与音频实际时长一致、放进唱片机能播放且比较器读数为 15。
 * <p>唱片不挂铁魔法条件，同一组场景也在只装车万女仆的拓扑里注册；这个类只引用原版与本模组的通用类。
 */
public final class MusicDiscScenes {
    private record Disc(String item, String song, float seconds, int ticks, String soundEvent, String ogg) {
    }

    private static final List<Disc> DISCS = List.of(
            new Disc("music_disc_stellar_endshore", "stellar_endshore", 185.15F, 3703,
                    "music.starfall_endshore", "starfall_endshore_bgm"),
            new Disc("music_disc_concerto_no_10_b_minor", "concerto_no_10_b_minor", 211.35F, 4227,
                    "music.stellar_witch", "stellar_witch"));

    private static final TagKey<Item> MUSIC_DISCS = TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("music_discs"));

    private MusicDiscScenes() {
    }

    public static List<Scene> dedicatedServer() {
        return scenes("musicDisc.");
    }

    public static List<Scene> minimal() {
        return scenes("minimal.musicDisc.");
    }

    private static List<Scene> scenes(String prefix) {
        return List.of(
                Checks.scene(prefix + "itemsAndSongs", 5, MusicDiscScenes::itemsAndSongs),
                Checks.scene(prefix + "songLengthMatchesAudio", 5, MusicDiscScenes::audioLength),
                Checks.superflat(prefix + "jukeboxPlaysWithFullComparator", 10, MusicDiscScenes::jukebox));
    }

    private static ItemStack stack(Disc disc) {
        return Actors.stack(NS + disc.item());
    }

    private static Optional<Holder<JukeboxSong>> song(SceneContext ctx, ItemStack stack) {
        return JukeboxSong.fromStack(ctx.server().registryAccess(), stack);
    }

    /** 不可堆叠、稀有；曲目指向本模组的 jukebox_song，时长、比较器、音效与描述键正确；在唱片标签与杂项页里；三种语言都有文本。 */
    private static void itemsAndSongs(SceneContext ctx) {
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true, ctx.server().registryAccess());
        List<Map<String, String>> langs = List.of(Lang.read("zh_cn"), Lang.read("en_us"), Lang.read("ja_jp"));
        for (Disc disc : DISCS) {
            ItemStack stack = stack(disc);
            String label = disc.item() + " ";
            ctx.check(stack.isEmpty()).as(label + "已注册").isFalse();
            if (stack.isEmpty()) {
                continue;
            }
            ctx.check(stack.getMaxStackSize()).as(label + "最大堆叠").isEqualTo(1);
            ctx.check(stack.getRarity()).as(label + "稀有度").isEqualTo(Rarity.RARE);
            ctx.check(stack.is(MUSIC_DISCS)).as(label + "在 minecraft:music_discs 标签里").isTrue();
            ctx.check(MaidSpellCreativeTab.MAID_SPELL_MISC_TAB.get().getDisplayItems().stream()
                    .anyMatch(shown -> shown.is(stack.getItem()))).as(label + "在杂项页里").isTrue();

            Optional<Holder<JukeboxSong>> song = song(ctx, stack);
            ctx.check(song.isPresent()).as(label + "能取到曲目").isTrue();
            song.ifPresent(holder -> {
                JukeboxSong value = holder.value();
                ctx.check(holder.unwrapKey().map(key -> key.location().toString()).orElse("")).as(label + "曲目 id")
                        .isEqualTo(NS + disc.song());
                ctx.check((double) value.lengthInSeconds()).as(label + "时长（秒）").isCloseTo(disc.seconds(), 1.0E-4);
                ctx.check(value.lengthInTicks()).as(label + "时长（tick，与上游 RecordItem 一致）").isEqualTo(disc.ticks());
                ctx.check(value.comparatorOutput()).as(label + "比较器读数").isEqualTo(15);
                ctx.check(value.soundEvent().value().getLocation().toString()).as(label + "音效")
                        .isEqualTo(NS + disc.soundEvent());
                String description = value.description().getContents() instanceof TranslatableContents translatable
                        ? translatable.getKey() : "<非翻译文本>";
                ctx.check(description).as(label + "描述键").isEqualTo("jukebox_song." + MaidSpellMod.MOD_ID + "." + disc.song());
            });

            for (int i = 0; i < langs.size(); i++) {
                String code = List.of("zh_cn", "en_us", "ja_jp").get(i);
                Map<String, String> lang = langs.get(i);
                ctx.check(lang.containsKey("item." + MaidSpellMod.MOD_ID + "." + disc.item())).as(code + " 有物品名 " + disc.item()).isTrue();
                ctx.check(lang.containsKey("jukebox_song." + MaidSpellMod.MOD_ID + "." + disc.song())).as(code + " 有曲目名 " + disc.song()).isTrue();
            }
        }
    }

    /** jukebox_song 里的时长与打包的 ogg 实际时长相差不到 0.1 秒，唱片机不会提前或延后停止。 */
    private static void audioLength(SceneContext ctx) {
        for (Disc disc : DISCS) {
            double actual = oggSeconds("/assets/" + MaidSpellMod.MOD_ID + "/sounds/music/" + disc.ogg() + ".ogg");
            ctx.record(disc.ogg() + " 实际秒数", actual);
            ctx.check(actual).as(disc.ogg() + ".ogg 时长与 " + disc.song() + " 的 length_in_seconds")
                    .isCloseTo(disc.seconds(), 0.1);
        }
    }

    /** Vorbis 时长：最后一页的 granule position（样本数）除以识别头里的采样率。 */
    private static double oggSeconds(String path) {
        byte[] bytes;
        try (InputStream stream = MaidSpellMod.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("找不到音频 " + path);
            }
            bytes = stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int header = indexOf(bytes, "\u0001vorbis".getBytes(StandardCharsets.ISO_8859_1), 0, false);
        int lastPage = indexOf(bytes, "OggS".getBytes(StandardCharsets.ISO_8859_1), bytes.length - 4, true);
        if (header < 0 || lastPage < 0) {
            throw new IllegalStateException("不是 Ogg Vorbis 文件 " + path);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        // 识别头：类型 1 字节 + "vorbis" 6 字节 + 版本 4 字节 + 声道 1 字节，之后是采样率
        int sampleRate = buffer.getInt(header + 12);
        long granule = buffer.getLong(lastPage + 6);
        return (double) granule / sampleRate;
    }

    private static int indexOf(byte[] bytes, byte[] needle, int from, boolean backwards) {
        for (int i = from; backwards ? i >= 0 : i <= bytes.length - needle.length; i += backwards ? -1 : 1) {
            boolean match = true;
            for (int j = 0; j < needle.length && match; j++) {
                match = bytes[i + j] == needle[j];
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    /** 放进唱片机就开始播放本曲目，比较器读数 15；取出后停止。 */
    private static void jukebox(SceneContext ctx) {
        ServerLevel level = ctx.level();
        List<BlockPos> placed = new ArrayList<>();
        ctx.cleanup(() -> placed.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)));
        for (int i = 0; i < DISCS.size(); i++) {
            Disc disc = DISCS.get(i);
            BlockPos pos = ctx.rel(i * 3, 0, 0);
            level.setBlock(pos, Blocks.JUKEBOX.defaultBlockState(), Block.UPDATE_ALL);
            placed.add(pos);
            if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox)) {
                ctx.fail("唱片机没有方块实体");
                return;
            }
            ItemStack stack = stack(disc);
            jukebox.setTheItem(stack.copy());
            ctx.check(jukebox.getSongPlayer().isPlaying()).as(disc.item() + " 放入后在播放").isTrue();
            Optional<Holder<JukeboxSong>> expected = song(ctx, stack);
            ctx.check(expected.isPresent() && jukebox.getSongPlayer().getSong() == expected.get().value())
                    .as(disc.item() + " 播放的是自己的曲目").isTrue();
            ctx.check(jukebox.getComparatorOutput()).as(disc.item() + " 比较器读数").isEqualTo(15);
            jukebox.splitTheItem(1);
            ctx.check(jukebox.getSongPlayer().isPlaying()).as(disc.item() + " 取出后停止").isFalse();
        }
    }
}
