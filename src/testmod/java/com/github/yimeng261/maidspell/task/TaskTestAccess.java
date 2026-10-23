package com.github.yimeng261.maidspell.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/** 给场景测试用的同包访问口：坐下或骑乘时是否允许施法的判定是受保护的静态方法。 */
public final class TaskTestAccess {
    private TaskTestAccess() {
    }

    public static boolean ridePoseSpellcastingEnabled(EntityMaid maid) {
        return SpellCombatMeleeTask.isRidePoseSpellcastingEnabled(maid);
    }
}
