package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Collections;

/** 按运动中的手部骨骼层级对齐，而不是直接比较设计好的 X、Y 轨迹。 */
public final class PalmBlackHoleRippleAlignment {
    private PalmBlackHoleRippleAlignment() {
    }

    public static void align(AnimatedGeoModel model, float castAge) {
        if (model == null || castAge <= 100 || castAge >= 260) return;
        AnimatedGeoBone locator = model.bones().get("Blackhole");
        if (locator == null) return;
        var hierarchy = new ArrayList<AnimatedGeoBone>();
        for (AnimatedGeoBone bone = locator; bone != null; ) {
            hierarchy.add(bone);
            var parent = bone.geoBone().parent();
            bone = parent == null ? null : model.bones().get(parent.name());
        }
        Collections.reverse(hierarchy);
        PoseStack poses = new PoseStack();
        RenderUtils.prepMatrixForLocator(poses, hierarchy);
        float centerY = poses.last().pose().m31();
        float blend = Mth.clamp((castAge - 100) / 20, 0, 1);
        blend = blend * blend * (3 - 2 * blend);
        for (String name : new String[]{"heobo-z", "heobo-z2"}) {
            AnimatedGeoBone ripple = model.bones().get(name);
            // 两个设计好的涟漪根骨骼都直接位于模型坐标系中。
            if (ripple == null || ripple.geoBone().parent() != null) continue;
            float alignedPosition = centerY * 16 - ripple.getPivotY();
            ripple.setPositionY(Mth.lerp(blend, ripple.getPositionY(), alignedPosition));
        }
    }
}
