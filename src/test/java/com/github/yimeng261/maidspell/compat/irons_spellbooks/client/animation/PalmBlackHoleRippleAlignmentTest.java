package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PalmBlackHoleRippleAlignmentTest {
    @Test void openingRemainsAtItsAuthoredHeight() {
        AnimatedGeoModel model = model();
        model.bones().get("heobo-z").setPositionY(21.39F);
        model.bones().get("heobo-z2").setPositionY(23.39F);
        PalmBlackHoleRippleAlignment.align(model, 100);
        assertEquals(21.39F, model.bones().get("heobo-z").getPositionY());
        assertEquals(23.39F, model.bones().get("heobo-z2").getPositionY());
    }

    @Test void midCastFollowsTheActualHandTransformAndPreservesRippleAnimation() {
        for (float height : new float[]{3, 9, 15}) {
            AnimatedGeoModel model = model();
            var hand = model.bones().get("LeftHand");
            hand.setRotationZ((float) Math.PI / 2);
            hand.setPositionY(height * 16);
            // Local X displacement becomes vertical when the parent hand rotates.
            model.bones().get("Blackhole").setPositionX(-32);
            for (String name : List.of("heobo-z", "heobo-z2")) {
                var ripple = model.bones().get(name);
                ripple.setScaleX(7);
                ripple.setScaleY(6);
                ripple.setScaleZ(0.5F);
                ripple.setRotationY(1.2F);
                ripple.setPositionX(4);
                ripple.setPositionZ(5);
            }
            PalmBlackHoleRippleAlignment.align(model, 160);
            for (String name : List.of("heobo-z", "heobo-z2")) {
                var ripple = model.bones().get(name);
                assertEquals(height + 2, (ripple.getPositionY() + ripple.getPivotY()) / 16, 1.0E-5F);
                assertEquals(7, ripple.getScaleX());
                assertEquals(6, ripple.getScaleY());
                assertEquals(0.5F, ripple.getScaleZ());
                assertEquals(1.2F, ripple.getRotationY());
                assertEquals(4, ripple.getPositionX());
                assertEquals(5, ripple.getPositionZ());
            }
        }
    }

    @Test void transitionToAlignmentIsGradualAndDoesNotOverrideTheDetachedStage() {
        AnimatedGeoModel model = model();
        model.bones().get("LeftHand").setPositionY(160);
        model.bones().get("heobo-z").setPositionY(0);
        PalmBlackHoleRippleAlignment.align(model, 110);
        assertEquals(5, model.bones().get("heobo-z").getPositionY() / 16, 1.0E-5F);
        model.bones().get("heobo-z").setPositionY(12);
        PalmBlackHoleRippleAlignment.align(model, 260);
        assertEquals(12, model.bones().get("heobo-z").getPositionY());
    }

    private static AnimatedGeoModel model() {
        GeoBone locator = bone("Blackhole", List.of());
        GeoBone hand = bone("LeftHand", List.of(locator));
        locator.setParent(hand);
        return new AnimatedGeoModel(new GeoModel(List.of(hand,
                bone("heobo-z", List.of()), bone("heobo-z2", List.of())), null));
    }

    private static GeoBone bone(String name, List<GeoBone> children) {
        return new GeoBone(children, name, new Vector3f(), new Vector3f(), null,
                false, null, false, false);
    }
}
