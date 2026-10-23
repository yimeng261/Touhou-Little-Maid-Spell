package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.accessor.StructureTemplateAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin {
    /** 本模组命名空间的模板加载后打上标记，放置实体时据此补上乘客。 */
    @Inject(method = "tryLoad", at = @At("RETURN"))
    private void maidSpell$markOwnTemplate(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        if (MaidSpellMod.MOD_ID.equals(id.getNamespace())) {
            cir.getReturnValue().ifPresent(StructureTemplateAccessor::markOwn);
        }
    }
}
