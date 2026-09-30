package com.github.yimeng261.maidspell.stagewright.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * 客户端的车万女仆模型表：1.5.3 自带的两个酒狐模型与星之魔女酒狐都在，已移除的 winefox_saint_black 不在；
 * 旧存档里仍用这个模型 ID 的女仆出现在玩家面前时客户端照常渲染、不掉线。
 */
public final class ModelPackClientScenes {
    private static final String REMOVED_MODEL = "geckolib:winefox_saint_black";

    private ModelPackClientScenes() {
    }

    public static List<Scene> integratedServer() {
        return List.of(
                Players.hostScene("client.model_pack.clientModelList", 5, ModelPackClientScenes::modelList),
                Players.hostScene("client.model_pack.removedModelIdStillRenders", 80, ModelPackClientScenes::removedModel));
    }

    private static void modelList(SceneContext ctx, ServerPlayer player) {
        ClientSide.call(ctx, () -> Set.copyOf(CustomPackLoader.MAID_MODELS.getModelIdSet()), models -> {
            for (String model : List.of("geckolib:winefox_elf", "geckolib:winefox_saint", "touhou_little_maid_spell:stellar_witch")) {
                ctx.check(models.contains(model)).as("客户端模型表含 " + model).isTrue();
            }
            ctx.check(models.contains(REMOVED_MODEL)).as("客户端模型表含 " + REMOVED_MODEL).isFalse();
        });
    }

    /** 把女仆放到玩家视线正前方，渲染 3 秒后客户端仍在世界里、能看到这只女仆。 */
    private static void removedModel(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        maid.setModelId(REMOVED_MODEL);
        Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(3));
        maid.moveTo(front.x, player.getY(), front.z, player.getYRot() + 180, 0);
        Checks.after(ctx, 60, () -> ClientSide.call(ctx, () -> {
            Minecraft mc = Minecraft.getInstance();
            Entity seen = mc.level == null ? null : mc.level.getEntity(maid.getId());
            return mc.getConnection() != null && seen instanceof EntityMaid;
        }, fine -> ctx.check(fine).as("客户端仍连着并看得到这只女仆").isTrue()));
    }
}
