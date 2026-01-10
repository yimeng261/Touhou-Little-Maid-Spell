package com.github.yimeng261.maidspell.api.entity;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 用于判断女仆是否佩戴有锚定核心
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-11-15 01:10
 */
public interface AnchoredEntityMaid {
    boolean maidSpell$isAnchored();

    void maidSpell$setAnchored(boolean anchored);

    static boolean isMaidAnchored(EntityMaid maid) {
        AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
        return anchoredEntityMaid.maidSpell$isAnchored();
    }
}
