package com.github.yimeng261.maidspell.mixin.ae2;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.github.yimeng261.maidspell.item.bauble.crystalCircuit.VirtualCPURegistry;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Mixin到AE2的CraftingService，用于支持虚拟CPU注册
 */
@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceMixin {
    
    @Shadow
    private Set<CraftingCPUCluster> craftingCPUClusters;
    
    /**
     * 在updateCPUClusters之后，添加我们的虚拟CPU
     */
    @Inject(method = "updateCPUClusters", at = @At("TAIL"), remap = false)
    private void onUpdateCPUClusters(CallbackInfo ci) {
        // 注意：虚拟CPU已经通过VirtualCPURegistry单独管理
        // 这里不需要做任何事情，因为getCpus方法会被下面的Mixin处理
    }
    
    /**
     * 修改getCpus方法，添加虚拟CPU到返回列表中
     */
    @Inject(method = "getCpus", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir) {
        ImmutableSet<ICraftingCPU> originalCpus = cir.getReturnValue();
        
        // 获取所有虚拟CPU
        Set<ICraftingCPU> virtualCpus = VirtualCPURegistry.getVirtualCPUs();
        
        if (!virtualCpus.isEmpty()) {
            // 合并原始CPU和虚拟CPU
            ImmutableSet.Builder<ICraftingCPU> builder = ImmutableSet.builder();
            builder.addAll(originalCpus);
            builder.addAll(virtualCpus);
            
            cir.setReturnValue(builder.build());
        }
    }
}

