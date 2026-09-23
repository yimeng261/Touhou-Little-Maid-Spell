package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.github.yimeng261.maidspell.validation.ValidationFixtures.RESOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.filesUnder;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseObject;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.relative;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 粒子贴图的两个死规矩：一项一帧、一帧一张方图。
 *
 * <h2>为什么</h2>
 * {@code ParticleEngine} 绑 sprite 时是「JSON 里列一个贴图 id，就绑一个 sprite」：
 * {@code MutableSpriteSet.rebind} 拿到几个 id 就有几个 sprite，而 {@code get(age, maxAge)}
 * 只是在 {@code age * (size - 1) / maxAge} 这个下标上取一个。也就是说：
 * <ul>
 *   <li>图集<b>没有</b>自动切帧这一步。把 16x128 的竖排 8 帧写成一个条目，
 *       屏幕上就是 8 帧被压进同一个方块里 —— 曾经真的这么写过；</li>
 *   <li>每一帧得是方图：粒子画的是方形 quad，非方的贴图只会被拉进去。</li>
 * </ul>
 *
 * <p>要按年龄逐帧播，就把图集切成{@code foo_0.png}…{@code foo_7.png} 一帧一个文件，
 * 像 {@code textures/entity/star_shadow_strike/} 那套一样。
 */
class ParticleSpriteResourceTest {

    /** 虚空粒子的帧数：{@code VoidSpellParticle} 的寿命就是照 8 帧一轮定的。 */
    private static final int VOID_SPELL_FRAMES = 8;

    @Test
    void everyParticleTextureIsASingleSquareFrame() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path definition : filesUnder(RESOURCES, ParticleSpriteResourceTest::isParticleDefinition)) {
            JsonArray textures = parseObject(definition).getAsJsonArray("textures");
            if (textures == null) {
                failures.add(relative(definition) + " 没有 textures");
                continue;
            }
            for (JsonElement texture : textures) {
                String id = texture.getAsString();
                if (!id.startsWith("touhou_little_maid_spell:")) {
                    // 别的 namespace 的贴图不在我们的资源里，这里验不了，交给游戏自己报缺失。
                    continue;
                }
                Path png = particleTexture(id);
                if (!Files.isRegularFile(png)) {
                    failures.add(relative(definition) + " 指向不存在的贴图 " + id);
                    continue;
                }
                BufferedImage image = ImageIO.read(png.toFile());
                if (image == null) {
                    failures.add(relative(png) + " 读不出来");
                } else if (image.getWidth() != image.getHeight()) {
                    failures.add(relative(png) + " 是 " + image.getWidth() + "x" + image.getHeight()
                        + "：一项就是一帧，竖排图集会被整个画进同一个方块");
                }
            }
        }

        assertTrue(failures.isEmpty(), () -> "粒子贴图不是单帧方图：\n" + String.join("\n", failures));
    }

    /**
     * 虚空粒子的 8 帧必须按顺序列全。
     *
     * <p>{@code MutableSpriteSet} 是按 list 下标取帧的，顺序错了动画就是倒放或者跳帧；
     * 少列几帧只是动画变短，屏幕上完全看不出来是配置错了。
     */
    @Test
    void voidSpellListsItsFramesInOrder() throws IOException {
        JsonArray textures = parseObject(RESOURCES.resolve(
            "assets/touhou_little_maid_spell/particles/void_spell.json")).getAsJsonArray("textures");

        List<String> actual = new ArrayList<>();
        textures.forEach(texture -> actual.add(texture.getAsString()));

        List<String> expected = new ArrayList<>();
        for (int index = 0; index < VOID_SPELL_FRAMES; index++) {
            expected.add("touhou_little_maid_spell:void_spell_" + index);
        }
        assertEquals(expected, actual, "虚空粒子的帧必须按 0..7 的顺序列全");
    }

    private static boolean isParticleDefinition(Path path) {
        Path parent = path.getParent();
        return path.toString().endsWith(".json")
            && parent != null
            && "particles".equals(parent.getFileName().toString());
    }

    /** {@code namespace:name} → {@code assets/namespace/textures/particle/name.png}。 */
    private static Path particleTexture(String id) {
        int split = id.indexOf(':');
        return RESOURCES.resolve("assets/" + id.substring(0, split)
            + "/textures/particle/" + id.substring(split + 1) + ".png");
    }
}
