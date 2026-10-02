package com.github.yimeng261.maidspell.stagewright.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.item.bauble.springBloomReturn.SpringBloomReturnBauble;
import com.github.yimeng261.maidspell.mixin.accessor.LivingEntityHealthAccessor;
import com.github.yimeng261.maidspell.utils.MaidHealthWriteGuard;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 场景里生成生物、女仆和物品的小工具；生成的实体都登记清理。 */
public final class Actors {
    public static final String MAID = "touhou_little_maid:maid";

    private Actors() {
    }

    /** 在相对原点的方块中心生成实体；noAi 的生物不会走动或互相攻击。 */
    public static <T extends Entity> T spawn(SceneContext ctx, String type, int dx, int dy, int dz, boolean noAi) {
        return spawn(ctx, type, dx, dy, dz, noAi, (T entity) -> {
        });
    }

    /** 同 {@link #spawn(SceneContext, String, int, int, int, boolean)}，beforeAdd 在实体进入世界前运行（例如写好主人）。 */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> T spawn(SceneContext ctx, String type, int dx, int dy, int dz, boolean noAi,
                                             java.util.function.Consumer<T> beforeAdd) {
        ServerLevel level = ctx.level();
        Entity entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(type)).create(level);
        if (entity == null) {
            throw new IllegalStateException("无法创建实体 " + type);
        }
        BlockPos pos = ctx.rel(dx, dy, dz);
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        if (noAi && entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        beforeAdd.accept((T) entity);
        level.addFreshEntity(entity);
        ctx.cleanup(() -> cleanupEntity(entity));
        return (T) entity;
    }

    /** 结束场景时先释放饰品票据，再经生命周期授权移除受保护实体。 */
    public static void cleanupEntity(Entity entity) {
        if (entity instanceof EntityMaid maid) Maids.takeOffAll(maid);
        com.github.yimeng261.maidspell.utils.BossLifecycleAccess.withAuthorizedTeardown(
                entity, Entity.RemovalReason.DISCARDED, entity::discard);
    }

    /**
     * 坐着的女仆：保留 AI（饰品逻辑依赖女仆 tick）。未驯服的女仆不一定一直坐着，偶尔会走动，
     * 对位置敏感的场景用 {@link #stillMaid}。
     */
    public static EntityMaid sittingMaid(SceneContext ctx, int dx, int dy, int dz) {
        EntityMaid maid = spawn(ctx, MAID, dx, dy, dz, false);
        maid.setOrderedToSit(true);
        maid.setInSittingPose(true);
        maid.setPersistenceRequired();
        return maid;
    }

    /** 无 AI 的女仆：位置固定，用于光环范围等只依赖位置的场景。 */
    public static EntityMaid stillMaid(SceneContext ctx, int dx, int dy, int dz) {
        return spawn(ctx, MAID, dx, dy, dz, true);
    }

    public static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    /** 按 /give 语法解析带组件的物品，例如 {@code irons_spellbooks:scroll[irons_spellbooks:spell_container={...}]}。 */
    public static ItemStack parse(SceneContext ctx, String text) {
        try {
            ItemParser.ItemResult result = new ItemParser(ctx.level().registryAccess()).parse(new StringReader(text));
            ItemStack stack = new ItemStack(result.item(), 1);
            stack.applyComponents(result.components());
            return stack;
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("无法解析物品 " + text + ": " + e.getMessage(), e);
        }
    }

    /**
     * 直接设置女仆生命。本模组拦截非正常受伤造成的女仆扣血（setHealth 往下调会被忽略），
     * 这里走它自己的写入旁路，只用于布置场景初始状态。
     */
    public static void setMaidHealth(EntityMaid maid, float health) {
        MaidHealthWriteGuard.runBypassing(() ->
                maid.getEntityData().set(LivingEntityHealthAccessor.maidspell$getHealthAccessor(), health));
    }

    /** 把饰品放进女仆第一个饰品槽，返回女仆身上实际的那份物品。 */
    public static ItemStack equipBauble(EntityMaid maid, String id) {
        return Maids.putOn(maid, 0, stack(id));
    }

    /** 以 Iron's 火球术的名义通知春花-返一次施法，返回当时的游戏时间。 */
    public static long castSpringBloom(EntityMaid maid) {
        SpringBloomReturnBauble.onSpellCast(maid, "irons_spellbooks", "irons_spellbooks:firebolt", null);
        return maid.level().getGameTime();
    }

    /** 第一个饰品槽里春花-返各层的过期时间。 */
    public static List<Long> springBloomExpiries(EntityMaid maid) {
        return maid.getMaidBauble().getStackInSlot(0)
                .getOrDefault(MaidSpellDataComponents.SPRING_BLOOM_RETURN_EXPIRIES.get(), List.of());
    }

    /** 把场地原点下方一层、水平半径 radius 内换成熔岩，场景结束时恢复原方块。 */
    public static void lavaPool(SceneContext ctx, int radius) {
        ServerLevel level = ctx.level();
        Map<BlockPos, BlockState> original = new HashMap<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos pos = ctx.rel(x, -1, z);
                original.put(pos, level.getBlockState(pos));
                ctx.setBlock(x, -1, z, Blocks.LAVA);
            }
        }
        ctx.cleanup(() -> original.forEach(level::setBlockAndUpdate));
    }
}
