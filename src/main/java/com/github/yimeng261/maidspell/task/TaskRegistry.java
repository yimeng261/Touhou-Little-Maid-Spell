package com.github.yimeng261.maidspell.task;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

/**
 * 女仆任务注册器
 * 负责将自定义任务注册到东方女仆模组中
 */
@LittleMaidExtension
public class TaskRegistry implements ILittleMaid {
    
    /**
     * 注册所有自定义任务
     */
    @Override
    public void addMaidTask(TaskManager manager) {
        // 注册法术战斗任务
        manager.add(new SpellCombatMeleeTask());
        manager.add(new SpellCombatFarTask());
    }


}