package com.github.yimeng261.maidspell.stagewright;

import com.github.yimeng261.maidspell.stagewright.client.BookClientScenes;
import com.github.yimeng261.maidspell.stagewright.client.EnderPocketClientScenes;
import com.github.yimeng261.maidspell.stagewright.client.ModelPackClientScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.AllyScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.AnchorScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.BaubleRuntimeScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.BookScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.ChallengeStateScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.DreamCrystalScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.EnderPocketScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.SpellEntityScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.SpellLifecycleScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.StateMigrationScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.StellarContentScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.StellarStructureScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.StructureEffectScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.FunctionScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.GuardScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.LootScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.MissingModScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.ModelPackScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.NaturalScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.NpcScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.OwnerScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.RetreatCacheScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.SharedRetreatScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.StructureScenes;
import com.github.yimeng261.maidspell.stagewright.scenes.TemplateScenes;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * 本模组的 StageWright 场景入口，按 {@link TestTopology} 分流：每个拓扑只注册自己的场景，
 * 与 src/testmod/expected-scenes-&lt;拓扑&gt;.txt 对账。
 * <p>缺模组拓扑只引用不依赖可选联动的场景类，其余场景类在那些拓扑里不会被加载。
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
                scenes.addAll(BaubleRuntimeScenes.dedicatedServer());
                scenes.addAll(DreamCrystalScenes.dedicatedServer());
                scenes.addAll(SpellLifecycleScenes.dedicatedServer());
                scenes.addAll(StateMigrationScenes.dedicatedServer());
                scenes.addAll(EnderPocketScenes.dedicatedServer());
                scenes.addAll(MissingModScenes.dedicatedServer());
                scenes.addAll(ModelPackScenes.dedicatedServer());
                scenes.addAll(GuardScenes.dedicatedServer());
                scenes.addAll(AnchorScenes.dedicatedServer());
                scenes.addAll(SpellEntityScenes.dedicatedServer());
                scenes.addAll(BookScenes.dedicatedServer());
                scenes.addAll(StructureEffectScenes.dedicatedServer());
                scenes.addAll(StellarContentScenes.dedicatedServer());
                scenes.addAll(NpcScenes.dedicatedServer());
                scenes.addAll(StellarStructureScenes.dedicatedServer());
                scenes.addAll(TemplateScenes.dedicatedServer());
                scenes.addAll(ChallengeStateScenes.dedicatedServer());
            }
            case INTEGRATED_SERVER -> {
                scenes.addAll(OwnerScenes.integratedServer());
                scenes.addAll(RetreatScenes.integratedServer());
                scenes.addAll(AllyScenes.integratedServer());
                scenes.addAll(BaubleRuntimeScenes.integratedServer());
                scenes.addAll(EnderPocketScenes.integratedServer());
                scenes.addAll(AnchorScenes.integratedServer());
                scenes.addAll(RetreatCacheScenes.integratedServer());
                scenes.addAll(EnderPocketClientScenes.integratedServer());
                scenes.addAll(ModelPackClientScenes.integratedServer());
                scenes.addAll(BookClientScenes.integratedServer());
            }
            case INTEGRATED_SERVER_SHARED -> scenes.addAll(SharedRetreatScenes.integratedServerShared());
            case DEDICATED_SERVER_WITH_CLIENT -> scenes.addAll(EnderPocketScenes.dedicatedServerWithClient());
            case DEDICATED_SERVER_MINIMAL -> scenes.addAll(MissingModScenes.minimal());
            case DEDICATED_SERVER_NO_FARMERS_DELIGHT -> scenes.addAll(MissingModScenes.noFarmersDelight());
        }
        return scenes;
    }
}
