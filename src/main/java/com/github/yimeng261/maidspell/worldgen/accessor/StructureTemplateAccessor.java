package com.github.yimeng261.maidspell.worldgen.accessor;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 给 StructureTemplate 记上是否为本模组命名空间的模板（由模板管理器加载时标记）。
 * 原版放置模板实体不读 Passengers，本模组模板放置时据此补上坐在座位上的乘客，别的模组的模板保持原版行为。
 */
public interface StructureTemplateAccessor {
    void maidspell$setOwnTemplate(boolean own);

    boolean maidspell$isOwnTemplate();

    static void markOwn(StructureTemplate template) {
        ((StructureTemplateAccessor) template).maidspell$setOwnTemplate(true);
    }
}
