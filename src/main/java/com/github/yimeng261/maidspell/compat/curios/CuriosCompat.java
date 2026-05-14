package com.github.yimeng261.maidspell.compat.curios;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Curios 模组桥接类
 * 所有对 {@code top.theillusivec4.curios.api.CuriosApi} 的引用都集中在此类，
 * 调用方需要先用 {@link #isLoaded()} 守卫，确保 curios 缺失时不会触发链接错误。
 */
public final class CuriosCompat {
    public static final String MOD_ID = "curios";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * 在 curios 加载时注册 curios 相关的事件订阅。
     */
    public static void init() {
        if (!LOADED) {
            return;
        }
        NeoForge.EVENT_BUS.register(CuriosEventHandler.class);
    }

    /**
     * 遍历女仆所有 curios 槽位中匹配 filter 的 ItemStack，对每个匹配的物品执行 action。
     * curios 未加载时直接返回。
     */
    public static void forEachMatchingCurio(LivingEntity entity, Predicate<ItemStack> filter, Consumer<ItemStack> action) {
        if (!LOADED) {
            return;
        }
        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                handler.findCurios(filter).forEach(slotResult -> {
                    ItemStack stack = slotResult.stack();
                    if (!stack.isEmpty()) {
                        action.accept(stack);
                    }
                })
        );
    }

    /**
     * 为实体的所有 curios 槽位类型各注册一个 transient slot modifier。
     */
    public static void addTransientSlotForAllTypes(LivingEntity entity, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        if (!LOADED) {
            return;
        }
        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                handler.getCurios().keySet().forEach(slotType -> {
                    handler.removeSlotModifier(slotType, id);
                    handler.addTransientSlotModifier(slotType, id, amount, operation);
                })
        );
    }

    /**
     * 移除实体所有 curios 槽位类型上指定 id 的 slot modifier。
     */
    public static void removeSlotModifierForAllTypes(LivingEntity entity, ResourceLocation id) {
        if (!LOADED) {
            return;
        }
        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                handler.getCurios().keySet().forEach(slotType ->
                        handler.removeSlotModifier(slotType, id))
        );
    }
}
