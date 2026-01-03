package com.github.yimeng261.maidspell.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 插件，用于条件加载 Mixin
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03
 */
public class MaidSpellMixinPlugin implements IMixinConfigPlugin {
    private static final String ISS_MOD_ID = "irons_spellbooks";
    private static final String ISS_MIXIN_PACKAGE = "com.github.yimeng261.maidspell.mixin.iss.";

    private boolean isIronsSpellbooksLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        // 检查 Iron's Spellbooks 模组是否已加载
        isIronsSpellbooksLoaded = LoadingModList.get().getModFileById(ISS_MOD_ID) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 如果是 iss 包下的 Mixin，需要检查 Iron's Spellbooks 模组是否存在
        if (mixinClassName.startsWith(ISS_MIXIN_PACKAGE)) {
            return isIronsSpellbooksLoaded;
        }

        // 其他 Mixin 正常加载
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
