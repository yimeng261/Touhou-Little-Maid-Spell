package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.util.JsonUtil;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PalmBlackHoleResourcesTest {
    private static final Path PACK = Path.of(System.getProperty("maidspell.projectDir"),
            "src/main/resources/assets/touhou_little_maid_spell/tlm_custom_pack/star_witch_winefox-1.0.0",
            "assets/touhou_little_maid_spell");

    @Test void witchModelContainsOnlyAnEmptyPalmMarkerAndItsOwnTexture() throws Exception {
        Model model = JsonUtil.GEO_GSON.fromJson(Files.readString(PACK.resolve("models/entity/stellar_witch.json"),
                StandardCharsets.UTF_8), Model.class);
        var baked = BakedModelFactory.getForNamespace("touhou_little_maid_spell")
                .constructGeoModel(GeometryTree.fromModel(model));
        GeoBone attachment = baked.getBone("Blackhole").orElseThrow();
        assertEquals("LeftHand", attachment.getParent().getName());
        List<GeoBone> bones = new ArrayList<>();
        collect(attachment, bones);
        assertEquals(0, bones.stream().mapToInt(bone -> bone.getCubes().size()).sum());
        var texture = ImageIO.read(PACK.resolve("textures/entity/stellar_witch.png").toFile());
        assertEquals(1024, texture.getWidth());
        assertEquals(1024, texture.getHeight());
    }

    @Test void suppliedLocatorAnimationRetainsItsPositionAndScale() throws Exception {
        var root = JsonParser.parseString(Files.readString(PACK.resolve(
                "animation/touhou_little_maid_spell.stellar_witch.iss.animation.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("animations");
        var palm = root.getAsJsonObject("iss:palm_black_hole");
        assertNotNull(palm);
        // The black hole is repositioned at 13s, but the supplied cast animation
        // continues through the 16s drop and 17s impact.
        assertEquals(PalmBlackHoleTimeline.IMPACT_TICK, palm.get("animation_length").getAsDouble() * 20);
        assertEquals("hold_on_last_frame", palm.get("loop").getAsString());
        var locator = palm.getAsJsonObject("bones").getAsJsonObject("Blackhole");
        var scale = locator.getAsJsonObject("scale");
        assertEquals(0, scale.getAsJsonArray("1.0").get(0).getAsFloat());
        assertEquals(0.05F, scale.getAsJsonArray("1.5").get(0).getAsFloat());
        assertEquals(0.1F, scale.getAsJsonArray("5.0").get(0).getAsFloat());
        assertEquals(PalmBlackHoleTimeline.MAX_MODEL_SCALE,
                scale.getAsJsonArray("13.0").get(0).getAsFloat() * PalmBlackHoleTimeline.LOCATOR_SCALE_MULTIPLIER);
        assertEquals(-150, locator.getAsJsonObject("position").getAsJsonArray("13.0").get(0).getAsInt());
        for (String name : List.of("heobo-z", "heobo-z2")) {
            var ripplePosition = palm.getAsJsonObject("bones").getAsJsonObject(name).getAsJsonObject("position");
            assertEquals(21.39, ripplePosition.getAsJsonArray("5.0").get(1).getAsDouble(), 1.0E-6);
            assertEquals(160, ripplePosition.getAsJsonArray("13.8333").get(1).getAsInt());
        }
        assertTrue(root.has("iss:charge_black_hole"));
    }

    private static void collect(GeoBone bone, List<GeoBone> result) {
        result.add(bone);
        bone.getChildBones().forEach(child -> collect(child, result));
    }

    @Test void bothBlackHoleSoundsResolveToMonoVorbisAssets() throws Exception {
        Path assets = Path.of(System.getProperty("maidspell.projectDir"),
                "src/main/resources/assets/touhou_little_maid_spell");
        var sounds = JsonParser.parseString(Files.readString(assets.resolve("sounds.json")))
                .getAsJsonObject();
        for (String name : List.of("palm_black_hole_loop", "palm_black_hole_explosion")) {
            String resource = sounds.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsString();
            assertEquals("touhou_little_maid_spell:spell/" + name, resource);
            byte[] audio = Files.readAllBytes(assets.resolve("sounds/spell/" + name + ".ogg"));
            assertEquals("OggS", new String(audio, 0, 4, StandardCharsets.US_ASCII));
            // The first Ogg packet contains the Vorbis identification header.
            int header = 27 + Byte.toUnsignedInt(audio[26]);
            assertEquals(1, audio[header]);
            assertEquals("vorbis", new String(audio, header + 1, 6, StandardCharsets.US_ASCII));
            assertEquals(1, audio[header + 11], "Positional audio must be mono");
            assertEquals(48000, ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN).getInt(header + 12));
        }
    }
}
