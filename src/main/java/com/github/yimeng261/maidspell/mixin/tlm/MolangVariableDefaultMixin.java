package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.storage.VariableStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让未赋值的 molang 变量取 0，对齐标准 Molang / YSM 语义。
 *
 * <p><b>这是引擎侧偏离规范，不是模型作者的错。</b>Molang 规范（以及 Bedrock、YSM）里
 * 未赋值的 {@code variable.x} 求值为 <b>0</b>，模型作者可以直接写
 * {@code v.foo == 0 ? A : B} 来表达「默认走 A」。而 TLM 这份 geckolib3 里
 * {@code VariableStorage.VariableValueHolder.value} 初值是 {@code null}：
 *
 * <pre>
 * public Object getScoped(int name) {
 *     VariableValueHolder h = scopedMap.computeIfAbsent(name, n -&gt; new VariableValueHolder());
 *     return h.value;      // 从没赋过值 -&gt; null
 * }
 * </pre>
 *
 * <p>{@code null == 0} 为假，于是**所有这类条件一律走 else 分支**，
 * 和作者的意图正好相反。
 *
 * <h2>实际踩到的坑</h2>
 * 星之魔女酒狐那份包里，作者用 {@code v.roaming.C} 做九尾／单尾的互补开关：
 *
 * <pre>
 * pre_parallel0:  TailNine  scale = "v.roaming.C == 0?1:0"
 * pre_parallel0:  Tail       scale = "v.roaming.C == 0?0:1"
 * </pre>
 *
 * 默认（C 未赋值 = 0）本该是<b>九尾显示、单尾隐藏</b>；
 * 在 TLM 里却反过来变成单尾显示、九尾隐藏 —— 那条一直在摆、
 * 我们一度以为需要在 {@code death} 里钉住的尾巴，按作者设计根本就不该出现。
 * 法阵（{@code mofazhen_}、{@code ysmGlowdamofazhen3}，靠 {@code v.roaming.B}）同理。
 *
 * <h2>为什么修在这里而不是改模型</h2>
 * 把默认分支的结果烘焙成常量写回包里，等于把作者做的切换功能删掉 ——
 * 那种改动发给作者他也不会要。作者按规范写没有任何问题，
 * 该对齐规范的是引擎。
 *
 * <p>影响范围是本存档所有模型包，但方向是<b>向规范靠拢</b>：
 * 任何依赖「未赋值 = 0」这一规范行为的包都会变得更正确，而不是更错。
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 */
@Mixin(value = VariableStorage.class, remap = false)
public class MolangVariableDefaultMixin {

    /**
     * 共用一个装好箱的 0：{@code getScoped} 是每条 molang 表达式、每根骨骼、每帧都要走的，
     * 而 {@code Double} 没有 {@code valueOf} 缓存 —— 写字面量 {@code 0.0D} 就是每次读一个新对象，
     * 且按设计一直不赋值的那些变量（{@code v.roaming.C} 之类）会永远走这条路。
     */
    @Unique
    private static final Double MAIDSPELL$ZERO = 0.0D;

    @Inject(method = "getScoped", at = @At("RETURN"), cancellable = true)
    private void maidspell$defaultUnsetToZero(int name, CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(MAIDSPELL$ZERO);
        }
    }
}
