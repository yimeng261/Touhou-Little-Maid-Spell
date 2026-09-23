package com.github.yimeng261.maidspell.validation;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxBossBar;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    /**
     * Boss 血条的贴图，以及「这条血条是酒狐的」那条识别链。
     *
     * <p>这条链有三段，任何一段断了都不会报错，只会静默降级：
     * <ol>
     *   <li>服务端把称号套在 {@code WinefoxBossBar.NAME_KEY} 上；</li>
     *   <li>两份语言文件都得有这个键，而且值必须是 {@code "%s"} —— 改成别的字，
     *       玩家看到的血条名就会多出一截前缀；血条上真正显示的字另有
     *       {@code WinefoxBossBar.TITLE_KEY} 一条；</li>
     *   <li>客户端 {@code WinefoxBossBarOverlay} 得照同样的键去认，认不出来就整个退回原版血条。</li>
     * </ol>
     *
     * <p>贴图只管尺寸：{@code WinefoxBossBarOverlay} 里那套槽位坐标是照这两张图量出来的，
     * 换了图就得回去重新量，所以这里把图上的实测值和源码里的常量对一遍。
     * 2026.9.22 这一版的规格：画布 {@code 256x96}，画面本体 {@code 256x56} 居中置顶，
     * 下面 40 行整片透明；填充层落在 {@code x=18..237, y=33..37}。
     */
    @Test
    void bossBarTexturesAndNameKeyStayWired() throws IOException {
        BufferedImage base = readTexture("base.png");
        BufferedImage layer = readTexture("layer.png");
        for (BufferedImage texture : List.of(base, layer)) {
            assertEquals(256, texture.getWidth(), "boss bar canvas width");
            assertEquals(96, texture.getHeight(), "boss bar canvas height");
        }

        assertArrayEquals(new int[] {0, 0, 255, 55}, opaqueBounds(base),
                "base.png 的画面本体必须居中置顶铺满 256x56，多出来的 40 行只能是透明画布余量");
        assertArrayEquals(new int[] {18, 33, 237, 37}, opaqueBounds(layer),
                "layer.png 的填充区挪了位置，overlay 里的槽位常量也要跟着重新量");

        String localized = parseObject(ASSETS.resolve("lang/zh_cn.json"))
                .get(WinefoxBossBar.NAME_KEY).getAsString();
        assertEquals("%s", localized,
                "boss bar name must stay a bare %s so the visible name is unchanged");
        assertEquals("%s", parseObject(ASSETS.resolve("lang/en_us.json"))
                .get(WinefoxBossBar.NAME_KEY).getAsString(), "en_us boss bar name");

        // 血条上显示的是这条固定称号，中英文各一份；实体名（头顶名、刷怪蛋）不走这里。
        assertEquals("星之魔女酒狐", parseObject(ASSETS.resolve("lang/zh_cn.json"))
                .get(WinefoxBossBar.TITLE_KEY).getAsString(), "zh_cn boss bar title");
        assertEquals("Stellar Witch Winefox", parseObject(ASSETS.resolve("lang/en_us.json"))
                .get(WinefoxBossBar.TITLE_KEY).getAsString(), "en_us boss bar title");

        Path entitySource = PROJECT_ROOT.resolve(
                "src/main/java/com/github/yimeng261/maidspell/compat/irons_spellbooks/entity/winefox/MagicalWinefoxBossEntity.java");
        String entity = Files.readString(entitySource, StandardCharsets.UTF_8);
        assertTrue(entity.contains("WinefoxBossBar.NAME_KEY"),
                "the boss bar name must be built from WinefoxBossBar.NAME_KEY");
        assertTrue(entity.contains("WinefoxBossBar.TITLE_KEY"),
                "the boss bar must show the fixed WinefoxBossBar.TITLE_KEY title");

        Path overlaySource = PROJECT_ROOT.resolve(
                "src/main/java/com/github/yimeng261/maidspell/client/overlay/WinefoxBossBarOverlay.java");
        String overlay = Files.readString(overlaySource, StandardCharsets.UTF_8);
        assertTrue(overlay.contains("WinefoxBossBar.NAME_KEY"),
                "the client overlay must recognise the bar by the same key");
        assertTrue(overlay.contains("textures/gui/boss_bar/base.png")
                        && overlay.contains("textures/gui/boss_bar/layer.png"),
                "the client overlay must blit both boss bar textures");

        assertEquals(256, intConstant(overlay, "TEXTURE_WIDTH"), "overlay texture canvas width");
        assertEquals(96, intConstant(overlay, "TEXTURE_HEIGHT"), "overlay texture canvas height");
        assertEquals(256, intConstant(overlay, "BAR_WIDTH"), "overlay body width");
        assertEquals(56, intConstant(overlay, "BAR_HEIGHT"), "overlay body height");
        assertEquals(18, intConstant(overlay, "TRACK_X"), "overlay track x");
        assertEquals(33, intConstant(overlay, "TRACK_Y"), "overlay track y");
        assertEquals(220, intConstant(overlay, "TRACK_WIDTH"), "overlay track width");
        assertEquals(5, intConstant(overlay, "TRACK_HEIGHT"), "overlay track height");
        assertEquals(0xC77DFF, intConstant(overlay, "NAME_COLOR"),
                "boss bar name must stay the purple from layer.png");
        // 血条顶着屏幕顶端：减掉原版第一条血条那 12 格，一对一开打时结果就是 y=0。
        assertEquals(12, intConstant(overlay, "VANILLA_BAR_TOP_Y"),
                "overlay must subtract vanilla's first-bar y so the bar sits at the very top");
        assertTrue(overlay.contains("event.getY() - VANILLA_BAR_TOP_Y"),
                "the boss bar must be drawn flush with the top of the screen");
    }

    private static BufferedImage readTexture(String file) throws IOException {
        BufferedImage texture = ImageIO.read(ASSETS.resolve(
                "textures/gui/boss_bar/" + file).toFile());
        assertNotNull(texture, "boss_bar/" + file + " must be a readable PNG");
        return texture;
    }

    /** 不透明像素的外接矩形，返回 {@code {minX, minY, maxX, maxY}}。 */
    private static int[] opaqueBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    /** 从 overlay 源码里读一个 {@code private static final int} 常量，十进制或 {@code 0x} 都行。 */
    private static int intConstant(String source, String name) {
        Matcher matcher = Pattern.compile(
                "\\b" + name + "\\s*=\\s*(0[xX][0-9a-fA-F]+|\\d+)").matcher(source);
        assertTrue(matcher.find(), "overlay constant " + name + " not found");
        return Integer.decode(matcher.group(1));
    }

    private static JsonObject parseObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
