package com.github.yimeng261.maidspell.compat.geckolib;

import com.github.yimeng261.maidspell.compat.geckolib.client.GeckoLibCompatClient;
import com.github.yimeng261.maidspell.compat.geckolib.entity.GeckoLibCompatSpear;
import com.github.yimeng261.maidspell.compat.geckolib.registry.GeckoLibCompatEntities;
import com.github.yimeng261.maidspell.compat.geckolib.registry.GeckoLibCompatItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * 依赖 GeckoLib 几何体模型的内容与 GeckoLib 之间的隔离层。
 *
 * <p>GeckoLib 是可选依赖：星影投枪的物品类在类声明上就继承了 GeckoLib 的 {@code GeoItem}，
 * 实体类继承了 {@code GeoEntity}。没装 GeckoLib 时，只要这两个类被加载（物品/实体注册时就会加载）
 * 就会抛 {@code NoClassDefFoundError}，整个注册阶段跟着崩。所以它们的**注册**与**客户端渲染**
 * 都收在这一个包里，只在 GeckoLib 存在时挂上：没装 GeckoLib 时投枪这个物品与实体干脆不存在，
 * 女仆、法术、结构等其余内容照常工作。
 *
 * <p>与 {@code IronsSpellbooksCompat} 同一套路：真正碰到 GeckoLib 类型的类只在这个包里，
 * 调用方先问 {@link #isLoaded()}，条件不成立时那些类根本不会被加载。
 */
public final class GeckoLibCompat {

    public static final String MOD_ID = "geckolib";

    /** 类初始化时查一次即可：模组加载状态在游戏生命周期里不会变。 */
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private GeckoLibCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /** 注册投枪的物品与实体，从通用的模组构造里调进来。 */
    public static void init(IEventBus eventBus) {
        if (!isLoaded()) {
            return;
        }
        GeckoLibCompatItems.register(eventBus);
        GeckoLibCompatEntities.register(eventBus);
    }

    /** 酒狐的投枪攻击仅在 GeckoLib 存在时加载投枪实体类。 */
    public static void spawnBossSpear(LivingEntity boss, LivingEntity target) {
        if (!isLoaded()) {
            return;
        }
        GeckoLibCompatSpear.spawn(boss, target);
    }

    /** 注册投枪的实体渲染器，从通用的实体渲染器注册事件里调进来。 */
    public static void initClient(EntityRenderersEvent.RegisterRenderers event) {
        if (!isLoaded()) {
            return;
        }
        GeckoLibCompatClient.onRegisterEntityRenderers(event);
    }

    /** 注册投枪的物品属性覆盖，从通用的 {@code ClientSetup} 里调进来。 */
    public static void initClientSetup() {
        if (!isLoaded()) {
            return;
        }
        GeckoLibCompatClient.onClientSetup();
    }
}
