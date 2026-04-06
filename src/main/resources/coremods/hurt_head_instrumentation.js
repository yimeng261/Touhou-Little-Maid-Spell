var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

var HOOK_OWNER = 'com/github/yimeng261/maidspell/coremod/HurtHeadCoremodHooks';
var HURT_DESC = '(Lnet/minecraft/world/damagesource/DamageSource;F)Z';
var HURT_METHOD = ASMAPI.mapMethod('m_6469_');

var HURT_TARGETS = [
    'net.minecraft.world.entity.LivingEntity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Amethyst_Crab_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ancient_Ancient_Remnant_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Golem_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignited_Revenant_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Nameless_Sorcerer_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Old_Netherite_Monstrosity_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Harbinger_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.The_Leviathan_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.Koboleton_Entity',
    'com.github.L_Ender.cataclysm.entity.AnimationMonster.The_Watcher_Entity',
    'com.github.L_Ender.cataclysm.entity.Deepling.Coral_Golem_Entity',
    'com.github.L_Ender.cataclysm.entity.Deepling.Lionfish_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.AcropolisMonsters.Cindaria_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.AcropolisMonsters.Clawdian_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.AcropolisMonsters.Hippocamtus_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Draugar.Aptrgangr_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Draugar.Royal_Draugr_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Ancient_Remnant.Ancient_Remnant_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Maledictus.Maledictus_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.NewNetherite_Monstrosity.Netherite_Monstrosity_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Scylla.Scylla_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Kobolediator_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.The_Prowler_Entity',
    'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Wadjet_Entity',
    'com.github.L_Ender.cataclysm.entity.Pet.The_Baby_Leviathan_Entity'
];

function initializeCoreMod() {
    var transformers = {};

    for (var i = 0; i < HURT_TARGETS.length; i++) {
        var className = HURT_TARGETS[i];
        transformers['maidspell_hurt_head_' + i] = createHurtTransformer(className);
    }

    return transformers;
}

function createHurtTransformer(className) {
    return {
        'target': {
            'type': 'METHOD',
            'class': className,
            'methodName': HURT_METHOD,
            'methodDesc': HURT_DESC
        },
        'transformer': function(methodNode) {
            if (hasHook(methodNode, 'maidspell$enterHurtHook')) {
                return methodNode;
            }

            injectHead(methodNode);
            injectExit(methodNode);
            methodNode.maxStack = methodNode.maxStack + 4;
            return methodNode;
        }
    };
}

function hasHook(methodNode, methodName) {
    var instructions = Java.from(methodNode.instructions.toArray());
    for (var i = 0; i < instructions.length; i++) {
        var insn = instructions[i];
        if (insn.getOpcode && insn.getOpcode() === Opcodes.INVOKESTATIC && insn.owner === HOOK_OWNER && insn.name === methodName) {
            return true;
        }
    }
    return false;
}

function injectHead(methodNode) {
    var continueLabel = new LabelNode();
    var injection = new InsnList();

    injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
    injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
    injection.add(new VarInsnNode(Opcodes.FLOAD, 2));
    injection.add(new MethodInsnNode(
        Opcodes.INVOKESTATIC,
        HOOK_OWNER,
        'maidspell$enterHurtHook',
        '(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Ljava/lang/Boolean;',
        false
    ));
    injection.add(new InsnNode(Opcodes.DUP));
    injection.add(new JumpInsnNode(Opcodes.IFNULL, continueLabel));
    injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
    injection.add(new MethodInsnNode(
        Opcodes.INVOKESTATIC,
        HOOK_OWNER,
        'maidspell$exitHurtHook',
        '(Lnet/minecraft/world/entity/LivingEntity;)V',
        false
    ));
    injection.add(new MethodInsnNode(
        Opcodes.INVOKEVIRTUAL,
        'java/lang/Boolean',
        'booleanValue',
        '()Z',
        false
    ));
    injection.add(new InsnNode(Opcodes.IRETURN));
    injection.add(continueLabel);
    injection.add(new InsnNode(Opcodes.POP));

    methodNode.instructions.insert(injection);
}

function injectExit(methodNode) {
    var instructions = Java.from(methodNode.instructions.toArray());
    for (var i = 0; i < instructions.length; i++) {
        var insn = instructions[i];
        var opcode = insn.getOpcode ? insn.getOpcode() : -1;
        if (opcode === Opcodes.IRETURN || opcode === Opcodes.ATHROW) {
            var exitInjection = new InsnList();
            exitInjection.add(new VarInsnNode(Opcodes.ALOAD, 0));
            exitInjection.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                'maidspell$exitHurtHook',
                '(Lnet/minecraft/world/entity/LivingEntity;)V',
                false
            ));
            methodNode.instructions.insertBefore(insn, exitInjection);
        }
    }
}
