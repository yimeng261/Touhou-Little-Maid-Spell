package com.github.yimeng261.maidspell.stagewright;

import com.github.yimeng261.maidspell.stagewright.scenes.FunctionScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.LootScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.NaturalScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.OwnerScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.SharedRetreatScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.StructureScenes;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * 本模组的 StageWright 场景入口，按 {@link TestTopology} 分流：每个拓扑只注册自己的场景，
 * 与 src/testmod/expected-scenes-&lt;拓扑&gt;.txt 对账。
 */
public final class MaidSpellScenes implements SceneProvider {
    @Override
    public List<Scene> scenes() {
        List<Scene> scenes = new ArrayList<>();
        switch (TestTopology.current()) {
            case DEDICATED_SERVER -> {
                scenes.addAll(LootScenes.dedicatedServer());
                scenes.addAll(StructureScenes.dedicatedServer());
                scenes.addAll(NaturalScenes.dedicatedServer());
                scenes.addAll(FunctionScenes.dedicatedServer());
            }
            case INTEGRATED_SERVER -> {
                scenes.addAll(OwnerScenes.integratedServer());
                scenes.addAll(RetreatScenes.integratedServer());
            }
            case INTEGRATED_SERVER_SHARED -> scenes.addAll(SharedRetreatScenes.integratedServerShared());
        }
        return scenes;
    }
}
