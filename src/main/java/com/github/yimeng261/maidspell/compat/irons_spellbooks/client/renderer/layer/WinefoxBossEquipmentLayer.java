package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.layer;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowLongswordItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarShadowStaffItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.item.StarWitchHatItem;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.MagicalWinefoxBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * 把万法酒狐实际穿戴的星之魔女装备渲染到模型上原本内置装备所在的骨骼。
 *
 * <p>装备模型是从 boss 模型里整体平移出来的，方块坐标与 boss 只差一个位移，
 * 因此两边同名骨骼的轴心之差就是需要补回去的位移 —— 在运行时直接算，
 * 以后在 Blockbench 里怎么挪都不用回来改常量。
 */
public class WinefoxBossEquipmentLayer extends GeoRenderLayer<MagicalWinefoxBossEntity> {

    /** 帽子只认头部槽空不空，不认里面具体是什么物品。 */
    private final Mount hat = new Mount("witchcap", "witchcap",
            StarWitchHatItem.MODEL, StarWitchHatItem.TEXTURE);
    /**
     * 长剑挂在主手挂点 {@code MStarShadowSword} 上。
     *
     * <p>{@code ambient_parts} 原来会把这根骨骼缩到 0，靠 {@code hold_mainhand:*} 再放回 1 ——
     * 那是武器网格还内置在 boss 模型里时的显隐开关。武器拆成独立物品模型之后，
     * 显隐已经由本层按主手物品决定，那两条 scale 轨道就从 {@code ambient_parts} 里删掉了。
     */
    private final Mount longsword = new Mount("MStarShadowSword", "StarShadowSword",
            StarShadowLongswordItem.MODEL, StarShadowLongswordItem.TEXTURE);
    /**
     * 法杖挂在 {@code StarShadowStaff} 上 —— 它现在是 {@code MStarShadowSword} 的子骨骼。
     *
     * <p>原本 {@code StaffLocator} 挂在 {@code DownBody} 下、背在身后，且被
     * {@code ambient_parts} / {@code hold_mainhand:bow} / {@code defeat} 常年缩到 0，
     * 所以挂在上面的法杖永远看不见。现在整条骨骼链平移进了右手，轴心和
     * {@code MStarShadowSword} 重合，{@code StarShadowStaff} 自带的 {@code [-90,-2.5,90]}
     * 就是竖握姿势；挂在主手挂点下面也让它继承挥砍、抽杖、倒地那些武器动画。
     */
    private final Mount staff = new Mount("StarShadowStaff", "handle2",
            StarShadowStaffItem.MODEL, StarShadowStaffItem.TEXTURE);

    public WinefoxBossEquipmentLayer(GeoRenderer<MagicalWinefoxBossEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, MagicalWinefoxBossEntity animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        Mount mount = mountFor(animatable, bone.getName());
        if (mount == null) {
            return;
        }

        BakedGeoModel bossModel = getDefaultBakedModel(animatable);
        BakedGeoModel equipment = mount.bakedModel();
        neutralizeRootBones(equipment);
        mount.syncFromBoss(bossModel, equipment);
        Vec3 offset = mount.offset(bossModel, equipment);
        RenderType equipmentRenderType = RenderType.entityCutoutNoCull(mount.texture);

        poseStack.pushPose();
        poseStack.translate(offset.x / 16.0D, offset.y / 16.0D, offset.z / 16.0D);

        // 这里**不能**用 GeoRenderer.reRender：它会走 GeoEntityRenderer.actuallyRender，
        // 而那里面的 applyRotations() 没有被 isReRender 挡住（只有动画状态那段被挡了）。
        // 结果是实体朝向的 mulPose(YP, 180 - bodyYaw) 被叠加第二次：本体转一次、装备再转一次，
        // yaw=0 时正好多 180°，帽子的蝴蝶结就跑到脑后去了。
        // 同样被重复施加的还有 translate(0, 0.01f, 0) 与死亡翻滚的 ZP 旋转。
        // 我们此刻的 poseStack 已经在目标骨骼上了，直接渲染装备的骨骼树即可。
        VertexConsumer equipmentBuffer = bufferSource.getBuffer(equipmentRenderType);
        for (GeoBone root : equipment.topLevelBones()) {
            getRenderer().renderRecursively(poseStack, animatable, root, equipmentRenderType, bufferSource,
                    equipmentBuffer, true, partialTick, packedLight, packedOverlay,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
        poseStack.popPose();

        // 必须把批次切回 boss 自己的 RenderType，否则 boss 剩下的骨骼会被画成装备的那一批。
        //
        // BufferSource.getBuffer 对两个不同的 entityCutoutNoCull(纹理) 返回的是**同一个**
        // BufferBuilder（只有 fixedBuffers 里的类型才有独立 builder）。换类型时它会把上一批
        // endBatch 掉，再用新格式 begin()——可是 boss 那边还攥着同一个 builder 的旧引用继续写。
        // 结果就是帽子之后渲染的一切（身体、法阵）都带着帽子的纹理和渲染类型画出去：
        // 法阵变纯色、boss 整体变色，都是这一条。
        bufferSource.getBuffer(renderType);

        // 画完立刻把借来的骨骼放回原位，见 Mount#restore。
        mount.restore(equipment);
    }

    /**
     * 把装备模型的顶层骨骼恢复成静止、可见的状态。
     *
     * <p>{@link BakedGeoModel} 是 {@code GeckoLibCache} 全局共享的——同一份法帽模型，
     * 玩家戴着它时 {@code GeoArmorRenderer} 会把 {@code armorHead} 的旋转改成跟随玩家头部，
     * 还会用 {@code setAllVisible(false)} 按装备槽位开关这几根骨骼。这些写入都留在共享对象上，
     * 轮到本层渲染时读到的就是别处最后一次写进去的值：帽子朝向会飘，极端情况下整顶帽子不见。
     *
     * <p>只清顶层骨骼就够了：{@code GeoArmorRenderer} 只碰
     * {@code armorHead} / {@code armorBody} / 四肢这几根，装备本体挂在它们下面，
     * 更深的骨骼交给 {@link Mount#syncFromBoss}。
     */
    private static void neutralizeRootBones(BakedGeoModel equipment) {
        for (GeoBone bone : equipment.topLevelBones()) {
            bone.setRotX(0.0F);
            bone.setRotY(0.0F);
            bone.setRotZ(0.0F);
            bone.setPosX(0.0F);
            bone.setPosY(0.0F);
            bone.setPosZ(0.0F);
            bone.setScaleX(1.0F);
            bone.setScaleY(1.0F);
            bone.setScaleZ(1.0F);
            bone.setHidden(false);
            bone.setChildrenHidden(false);
        }
    }

    /**
     * 当前骨骼上该挂哪件装备；没有对应装备就返回 null。
     *
     * <p><b>头部只画巫师帽这一顶，戴什么都画它，空着则不画。</b>
     * 星之魔女法帽是酒狐外观的一部分而不是可替换装备，所以不认头部槽里的具体物品；
     * 但头部槽空着时她就是没戴帽子，那就什么都不画。
     *
     * <p>这条判断就是头部渲染的**全部** —— 本 boss 的渲染器只挂了本层与
     * {@link WinefoxBossHeldItemLayer} 两层，而 {@code GeoEntityRenderer} 自己不带护甲层，
     * 所以头部槽换成别的帽子并不会多画一顶出来。
     *
     * <p>双手则相反：只有拿着自制武器时才由本层画，
     * 其余物品交给 {@link WinefoxBossHeldItemLayer} 走原版渲染。
     */
    @Nullable
    private Mount mountFor(MagicalWinefoxBossEntity boss, String boneName) {
        if (hat.bossBone.equals(boneName)) {
            return boss.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? null : hat;
        }
        ItemStack mainHand = boss.getMainHandItem();
        if (longsword.bossBone.equals(boneName) && isItem(mainHand, StarShadowLongswordItem.class)) {
            return longsword;
        }
        if (staff.bossBone.equals(boneName) && isItem(mainHand, StarShadowStaffItem.class)) {
            return staff;
        }
        return null;
    }

    /**
     * 这件物品是否由本层按自己的骨骼渲染。
     *
     * <p>{@link WinefoxBossHeldItemLayer} 靠它避让，否则同一把武器会被画两遍：
     * 一遍在武器挂点上，一遍在手持挂点上。
     */
    public static boolean isCustomEquipment(ItemStack stack) {
        return isItem(stack, StarShadowLongswordItem.class) || isItem(stack, StarShadowStaffItem.class);
    }

    private static boolean isItem(ItemStack stack, Class<? extends Item> type) {
        return !stack.isEmpty() && type.isInstance(stack.getItem());
    }

    /**
     * 一件装备的挂载方式：在 boss 的哪根骨骼上渲染（{@code bossBone}），
     * 以及用哪根两边同名的骨骼算轴心差（{@code anchorBone}）。
     */
    private static final class Mount {

        private final String bossBone;
        private final String anchorBone;
        private final ResourceLocation modelResource;
        private final ResourceLocation texture;
        private final StarEquipmentGeoModel<MagicalWinefoxBossEntity> model;

        private BakedGeoModel cachedFor;
        private Vec3 cachedOffset = Vec3.ZERO;

        private BakedGeoModel cachedLinksFor;
        private Map<GeoBone, GeoBone> cachedLinks = Map.of();

        private Mount(String bossBone, String anchorBone, ResourceLocation modelResource, ResourceLocation texture) {
            this.bossBone = bossBone;
            this.anchorBone = anchorBone;
            this.modelResource = modelResource;
            this.texture = texture;
            this.model = new StarEquipmentGeoModel<>(modelResource, texture);
        }

        private BakedGeoModel bakedModel() {
            return this.model.getBakedModel(this.modelResource);
        }

        /** 资源重载后 baked 模型是新对象，靠这个判断缓存是否过期。 */
        private Vec3 offset(BakedGeoModel bossModel, BakedGeoModel equipment) {
            if (this.cachedFor != bossModel) {
                this.cachedOffset = resolveOffset(bossModel, equipment);
                this.cachedFor = bossModel;
            }
            return this.cachedOffset;
        }

        private Vec3 resolveOffset(BakedGeoModel bossModel, BakedGeoModel equipment) {
            GeoBone bossAnchor = bossModel.getBone(this.anchorBone).orElse(null);
            GeoBone ownAnchor = equipment.getBone(this.anchorBone).orElse(null);
            if (bossAnchor == null || ownAnchor == null) {
                MaidSpellMod.LOGGER.warn("Winefox boss equipment {} is missing anchor bone '{}' (boss={}, equipment={})",
                        this.modelResource, this.anchorBone, bossAnchor != null, ownAnchor != null);
                return Vec3.ZERO;
            }
            return new Vec3(bossAnchor.getPivotX() - ownAnchor.getPivotX(),
                    bossAnchor.getPivotY() - ownAnchor.getPivotY(),
                    bossAnchor.getPivotZ() - ownAnchor.getPivotZ());
        }

        /**
         * 把 boss 骨骼上这一帧算好的形变搬到装备模型的同名骨骼上。
         *
         * <p>装备模型在这条渲染路径上是不跑动画的：我们只是借 {@code renderRecursively}
         * 画它的骨骼树。可 boss 的动画偏偏会去动装备内部的骨骼 —— 二阶段的
         * {@code phase_two_idle}、{@code sword_attack_01/02} 都在写 {@code StarShadowSword}。
         * 这些轨道写进的是 boss 模型自己那份骨骼，而 boss 模型里对应的立方体早就搬到
         * 装备模型里去了（{@code cubes: 0}），不搬过来形变就只是空转。
         *
         * <p>成立的前提是两边同名骨骼的静止旋转一致、轴心之差是常量（法杖与法帽完全满足；
         * 长剑的 {@code ysmGlow}/{@code p1}/{@code p2} 轴心有 2~6 的出入，但这三根在二阶段
         * 没有任何动画写入，同步是空操作）。
         *
         * <p>两个必须跳过的地方：顶层骨骼刚被 {@link WinefoxBossEquipmentLayer#neutralizeRootBones}
         * 归位，而 {@code bossBone} 那根（法帽的 {@code witchcap}）此刻已经体现在 poseStack 上了，
         * 再抄一遍就是叠加两次。
         */
        private void syncFromBoss(BakedGeoModel bossModel, BakedGeoModel equipment) {
            if (this.cachedLinksFor != bossModel) {
                this.cachedLinks = resolveLinks(bossModel, equipment);
                this.cachedLinksFor = bossModel;
            }
            for (Map.Entry<GeoBone, GeoBone> link : this.cachedLinks.entrySet()) {
                GeoBone own = link.getKey();
                GeoBone source = link.getValue();
                own.setRotX(source.getRotX());
                own.setRotY(source.getRotY());
                own.setRotZ(source.getRotZ());
                own.setPosX(source.getPosX());
                own.setPosY(source.getPosY());
                own.setPosZ(source.getPosZ());
                own.setScaleX(source.getScaleX());
                own.setScaleY(source.getScaleY());
                own.setScaleZ(source.getScaleZ());
            }
        }

        /**
         * 把 {@link #syncFromBoss} 借用的骨骼恢复成静止姿态。
         *
         * <p><b>这一步不是可选的。</b>{@link BakedGeoModel} 是 {@code GeckoLibCache}
         * 按资源路径全局共享的 —— 玩家手里那把星影长剑、地上的掉落物、展示框里的，
         * 与酒狐身上这把是**同一个对象**。酒狐的 {@code sword_form} 会把
         * {@code handle} 拉长 1.84 倍、{@code style1} 归零、{@code p1}/{@code p2} 位移 −25，
         * 不还原的话这些形变就留在共享对象上，玩家手里的剑跟着一起变形。
         *
         * <p>{@code GeoItemRenderer} 那条路径不会替我们清：它只对**自己动画写过**的骨骼
         * 做归位（{@code AnimationProcessor} 靠 {@code hasRotationChanged()} 一类的标记判断），
         * 而这些骨骼是被我们从外面直接 {@code setScaleX} 写的，没有任何标记。
         *
         * <p>恢复到 {@code getInitialSnapshot()} 而不是硬编码的 0/1：那是 GeckoLib 烘焙时
         * 从 geo 文件读出来的静止姿态，骨骼自带的旋转（例如法杖的 {@code [-90,-2.5,90]}）
         * 也在里面，写死 0 会把它们抹平。
         */
        private void restore(BakedGeoModel equipment) {
            for (GeoBone own : this.cachedLinks.keySet()) {
                BoneSnapshot rest = own.getInitialSnapshot();
                own.setRotX(rest.getRotX());
                own.setRotY(rest.getRotY());
                own.setRotZ(rest.getRotZ());
                own.setPosX(rest.getOffsetX());
                own.setPosY(rest.getOffsetY());
                own.setPosZ(rest.getOffsetZ());
                own.setScaleX(rest.getScaleX());
                own.setScaleY(rest.getScaleY());
                own.setScaleZ(rest.getScaleZ());
            }
        }

        private Map<GeoBone, GeoBone> resolveLinks(BakedGeoModel bossModel, BakedGeoModel equipment) {
            Map<GeoBone, GeoBone> links = new HashMap<>();
            for (GeoBone root : equipment.topLevelBones()) {
                for (GeoBone child : root.getChildBones()) {
                    collectLinks(bossModel, child, links);
                }
            }
            return links;
        }

        private void collectLinks(BakedGeoModel bossModel, GeoBone own, Map<GeoBone, GeoBone> links) {
            if (!this.bossBone.equals(own.getName())) {
                bossModel.getBone(own.getName()).ifPresent(source -> links.put(own, source));
            }
            for (GeoBone child : own.getChildBones()) {
                collectLinks(bossModel, child, links);
            }
        }
    }
}
