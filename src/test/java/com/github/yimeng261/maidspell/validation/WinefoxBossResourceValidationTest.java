package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 万法酒狐还归我们自己管的那两份资产：剑牢弹体的枪模型、玩家版举枪动画。
 *
 * <p><b>本文件曾经有五条断言盯着 {@code animations/magical_winefox_boss.animation.json}</b>
 * （施法动画不得覆盖躯干姿势、手部挂点不得被缩成 0、转阶段甩武器的隐藏窗口、
 * 战败定格、GeckoLib 能否 bake）。渲染迁到 TLM 的女仆渲染器之后那份文件删掉了，
 * 这五条一并走 —— 其中两条守的还是新包<b>有意反过来做</b>的事：
 *
 * <ul>
 *   <li>新包 22 条 {@code iss:*} 里有 16~21 条正大光明地写 {@code AllBody} / {@code MAllbody}
 *       / {@code Root} / {@code MTail}，施法姿势本来就该由 {@code magic_casting} 通道盖过
 *       {@code main}。</li>
 *   <li>{@code RightHandLocator} 的 {@code scale} 归零现在是<b>藏原版手持物品的正规手段</b>
 *       （TLM 的 {@code GeckoLayerMaidHeld} 见到 scale 为 0 就跳过），不再是 bug。</li>
 * </ul>
 *
 * <p>新包那四份动画文件另有把关：{@code ResourceValidationTest} 走遍所有 json 验能不能解析，
 * {@code WinefoxActionDataTest} 逐条对账轨道名、时长与 {@code loop}。这里不再重复，
 * 也不用 GeckoLib 4 的 {@code BakedAnimations} 去 bake —— 那份包由 TLM 自带的
 * geckolib3 分支加载，拿 4.x 的解析器验它是拿错了尺子。
 */
class WinefoxBossResourceValidationTest {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty(
            "maidspell.projectDir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
    private static final Path ASSETS = PROJECT_ROOT.resolve(
            "src/main/resources/assets/touhou_little_maid_spell");

    /**
     * 投枪只剩这一份模型：手持、投掷、剑牢弹体共用。
     *
     * <p>以前还有一份 {@code winefox_spear_projectile.geo.json}，是同一把枪的另一次导出
     * （骨骼数、立方体数、贴图尺寸全都一样，只有根骨骼旋转不同），已经删掉 ——
     * 同一件东西留两份模型，改了一份另一份就悄悄对不上。
     */
    @Test
    void spearProjectileKeepsCompleteModelAndTexture() throws IOException {
        JsonObject root = parseObject(ASSETS.resolve("geo/star_shadow_spear.geo.json"));
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        assertEquals(1, geometries.size());
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        assertEquals("geometry.star_shadow_spear",
                geometry.getAsJsonObject("description").get("identifier").getAsString());

        JsonArray bones = geometry.getAsJsonArray("bones");
        int cubeCount = 0;
        for (int index = 0; index < bones.size(); index++) {
            JsonArray cubes = bones.get(index).getAsJsonObject().getAsJsonArray("cubes");
            cubeCount += cubes == null ? 0 : cubes.size();
        }
        assertEquals(9, bones.size());
        assertEquals(103, cubeCount);

        BufferedImage texture = ImageIO.read(ASSETS.resolve(
                "textures/entity/winefox_spear_projectile.png").toFile());
        assertNotNull(texture, "Spear texture must be a readable PNG");
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
    }

    /** 玩家版举枪动画必须存在，否则玩家施放剑牢时铁魔法静默回落到默认抬手。 */
    @Test
    void swordPrisonHasAPlayerAnimation() throws IOException {
        JsonObject root = parseObject(ASSETS.resolve("player_animation/spear_throw.json"));
        JsonObject animations = root.getAsJsonObject("animations");
        assertNotNull(animations, "player_animation/spear_throw.json has no animations block");
        // PlayerAnimationRegistry 是按**动画名**建索引的，不是按文件名 ——
        // 名字对不上，getAnimation(touhou_little_maid_spell:spear_throw) 就是 null。
        assertTrue(animations.has("spear_throw"),
                () -> "expected an animation named spear_throw, found " + animations.keySet());
    }

    private static JsonObject parseObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
