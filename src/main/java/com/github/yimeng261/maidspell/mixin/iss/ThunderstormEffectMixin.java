package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.effect.ThunderstormEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

/**
 * 把女仆从雷暴（{@code irons_spellbooks:thunderstorm}）的索敌结果里摘掉。
 *
 * <p>铁魔法这个法术的正体是 {@code ThunderstormEffect} 这个 BUFF：施法者身上挂着的每一刻
 * （{@code isDurationEffectTick} 每 40 tick 触发一次）都会用
 * {@code level.getEntitiesOfClass(LivingEntity.class, 20x12x20, predicate)} 捞一批目标，
 * 逐个在脚下 {@code addFreshEntity} 一根 {@code LightningStrike}。那个 predicate 只管
 * 「不是自己 / 水平距离 < 20 / isPickable / 不是观察者 / {@code !Utils.shouldHealEntity} / 有视线」，
 * <b>中立生物一律放行</b>——女仆正是中立生物，于是站在施法者旁边的女仆会被反复劈。
 *
 * <p>这里不碰 predicate（那是 ISS 自己的行为判定，改了容易连带影响别的法术），
 * 只在实体已经筛完、还没落雷之前把女仆从结果列表里删掉：
 * <ol>
 *   <li>ISS 的 {@code shouldHealEntity(caster, target)} 靠 {@code isAlliedTo}，只解决「施法者自己的女仆不被劈」，
 *       PVP 里别人的女仆照样挨劈，所以不能只靠它；</li>
 *   <li>拦在上游 {@code addFreshEntity} 上做伤害豁免也不行——落空一根雷会白占扫描，
 *       而且要自己去分辨「这根雷是雷暴召的」还是别的来源。</li>
 * </ol>
 * 按需求这里是<b>不分主人、所有女仆都不劈</b>。
 *
 * <p><b>两处 {@code remap = true} 一个都不能删，缺了都是同一个病根：注解处理器不为
 * {@code remap = false} 的选择器写 refmap 条目，Mixin 在正式包里查不到条目就退回原名原样匹配，
 * 而正式包里那些原版方法都叫 SRG 名（{@code m_xxxxx_}），于是匹配不上。</b>
 * 类上的 {@code remap = false} 只是为了别去动 ISS 自己的成员名
 * （{@code getDamageFromAmplifier}、{@code Utils.shouldHealEntity} 这种是 ISS 自己加的，没有 SRG 名字），
 * 但它<em>同时会变成 {@code @Redirect} 的默认值</em>，所以这两个 {@code true} 必须手写。踩过的两个坑：
 * <ol>
 *   <li>{@code @At} 上的 {@code remap = true} 管的是 {@code Level.getEntitiesOfClass}
 *       （正式包 {@code m_6443_}）：少了它 {@code @At} 找不到被 redirect 的调用点；</li>
 *   <li>{@code @Redirect} 上的 {@code remap = true} 管的是目标方法本身
 *       {@code MobEffect.applyEffectTick}（正式包 {@code m_6742_}）：少了它 refmap 里
 *       <b>压根没有这个方法名的映射</b>，dev 下（方法名就是原名）能跑，正式包里直接
 *       {@code Critical injection failure: @Redirect annotation on ... could not find any targets matching 'applyEffectTick(...)'}，
 *       整个 mixin 应用失败、游戏崩在 mod 加载阶段。第一次提交就是这么炸的
 *       （{@code @At} 上写了 {@code true}、{@code @Redirect} 上漏了，于是 refmap 里只有
 *       {@code getEntitiesOfClass} 那一条，方法那条根本没有）。</li>
 * </ol>
 * 同一个包里的 {@code EntityMaidWinefoxRetiredStateMixin} 就是踩过第 2 个坑才写上这条注释的。
 *
 * <p>目标方法在 1.20.1-3.16.3 的字节码里只有<b>一处</b> {@code getEntitiesOfClass} 调用：
 * <pre>
 * 8: ldc  class net/minecraft/world/entity/LivingEntity
 * 23: invokevirtual AABB.inflate:(DDD)Lnet/minecraft/world/phys/AABB;
 * 34: invokevirtual Level.getEntitiesOfClass:(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;
 * </pre>
 * 所以不用写 {@code ordinal}；真出现第二处时再补，不然写死了反而更容易被上游改动打歪。
 */
@Mixin(value = ThunderstormEffect.class, remap = false)
public abstract class ThunderstormEffectMixin {
    /**
     * 复现 ISS 原来的查询，再把女仆从结果里剔掉。
     *
     * <p>{@code getEntitiesOfClass} 返回的就是新的 {@code ArrayList}，直接 {@code removeIf} 安全。
     * 先自己调一次 predicate 再 {@code removeIf} 是有意的：省掉一次 {@code getEntitiesOfClass}
     * 的遍历分配（雷暴每 40 tick 一次、每次都可能扫到十几只），而 predicate 是纯判定，多跑一次没有副作用。
     *
     * <p>签名照抄 {@code INVOKE} 里擦除后的描述符 {@code (Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;}，
     * 参数和返回值都用裸类型：泛型只活在 {@code Signature} 属性里，Mixin 校验的是描述符，
     * 写成 {@code List<? extends Entity>} / {@code Class<T>} 这种带通配的签名反而容易在
     * 类型推导上和 {@code <T extends Entity>} 打架。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                    remap = true
            ),
            remap = true
    )
    private List maidspell$excludeMaidsFromThunderstorm(Level level, Class entityClass, AABB area, Predicate predicate) {
        List targets = level.getEntitiesOfClass(entityClass, area, predicate);
        if (!targets.isEmpty() && targets.stream().anyMatch(EntityMaid.class::isInstance)) {
            targets.removeIf(EntityMaid.class::isInstance);
        }
        return targets;
    }
}
