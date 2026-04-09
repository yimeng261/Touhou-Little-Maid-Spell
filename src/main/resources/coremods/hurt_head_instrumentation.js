var ASMAPI = Java.type('net.neoforged.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

var HOOK_OWNER = 'com/github/yimeng261/maidspell/coremod/HurtHeadCoremodHooks';
var LIVING_ENTITY_INTERNAL_NAME = 'net/minecraft/world/entity/LivingEntity';
var HURT_DESC = '(Lnet/minecraft/world/damagesource/DamageSource;F)Z';
var HURT_METHOD = ASMAPI.mapMethod('hurt');

var TARGET_GROUPS = [
    {
        name: 'base',
        required: true,
        classes: [
            'net.minecraft.world.entity.LivingEntity'
        ]
    },
    {
        name: 'cataclysm',
        required: false,
        classes: [
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Amethyst_Crab_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Golem_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignited_Revenant_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Harbinger_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.The_Leviathan_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.Koboleton_Entity',
            'com.github.L_Ender.cataclysm.entity.AnimationMonster.The_Watcher_Entity',
            'com.github.L_Ender.cataclysm.entity.Deepling.AbstractDeepling',
            'com.github.L_Ender.cataclysm.entity.Deepling.Coral_Golem_Entity',
            'com.github.L_Ender.cataclysm.entity.Deepling.Lionfish_Entity',
            'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.AcropolisMonsters.Cindaria_Entity',
            'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.AcropolisMonsters.Clawdian_Entity',
            'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.AcropolisMonsters.Hippocamtus_Entity',
            'com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Coralssus_Entity',
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
        ]
    },
    {
        name: 'cataclysm_spellbooks',
        required: false,
        classes: [
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedAmethystCrab',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedAptrgangr',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedDraugur',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedEliteDraugur',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedIgnitedBerserker',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedIgnitedRevenant',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedKoboldiator',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedKoboleton',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedPhantomRemnant',
            'net.acetheeldritchking.cataclysm_spellbooks.entity.mobs.SummonedRoyalDraugur'
        ]
    },
    {
        name: 'irons_spellbooks',
        required: false,
        classes: [
            'io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.NeutralWizard',
            'io.redspace.ironsspellbooks.entity.mobs.dead_king_boss.DeadKingBoss',
            'io.redspace.ironsspellbooks.entity.mobs.keeper.KeeperEntity',
            'io.redspace.ironsspellbooks.entity.mobs.wizards.alchemist.ApothecaristEntity',
            'io.redspace.ironsspellbooks.entity.mobs.wizards.cursed_armor_stand.CursedArmorStandEntity',
            'io.redspace.ironsspellbooks.entity.mobs.wizards.fire_boss.FireBossEntity',
            'io.redspace.ironsspellbooks.entity.mobs.SummonedPolarBear',
            'io.redspace.ironsspellbooks.entity.mobs.SummonedSkeleton',
            'io.redspace.ironsspellbooks.entity.mobs.SummonedVex',
            'io.redspace.ironsspellbooks.entity.mobs.SummonedZombie'
        ]
    },
    {
        name: 'magic_from_the_east',
        required: false,
        classes: [
            'net.warphan.iss_magicfromtheeast.entity.mobs.jade_executioner.JadeExecutionerEntity',
            'net.warphan.iss_magicfromtheeast.entity.mobs.jiangshi.JiangshiEntity',
            'net.warphan.iss_magicfromtheeast.entity.mobs.jiangshi.SummonedJiangshiEntity',
            'net.warphan.iss_magicfromtheeast.entity.mobs.kitsune.SummonedKitsune',
            'net.warphan.iss_magicfromtheeast.entity.mobs.spirit_ashigaru.SpiritAshigaruEntity',
            'net.warphan.iss_magicfromtheeast.entity.mobs.spirit_samurai.SpiritSamuraiEntity'
        ]
    },
    {
        name: 'firesenderexpansion',
        required: false,
        classes: [
            'net.fireofpower.firesenderexpansion.entities.mobs.void_wyrm.VoidWyrm'
        ]
    },
    {
        name: 'discerning_the_eldritch',
        required: false,
        classes: [
            'net.acetheeldritchking.discerning_the_eldritch.entity.mobs.bosses.ascended_one.AscendedOneBoss',
            'net.acetheeldritchking.discerning_the_eldritch.entity.mobs.bosses.ascended_one.AscendedOneCultistEntity',
            'net.acetheeldritchking.discerning_the_eldritch.entity.mobs.eldritch_caster.TheApostleEntity',
            'net.acetheeldritchking.discerning_the_eldritch.entity.mobs.gaoler.GaolerEntity',
            'net.acetheeldritchking.discerning_the_eldritch.entity.mobs.sightless_maw.SightlessMawEntity',
            'net.acetheeldritchking.discerning_the_eldritch.entity.mobs.untold_behemoth.UntoldBehemothEntity'
        ]
    },
    {
        name: 'gtbcs_geomancy_plus',
        required: false,
        classes: [
            'com.gametechbc.gtbcs_geomancy_plus.entity.summons.SummonedNaga'
        ]
    },
    {
        name: 'hazen_n_stuff',
        required: false,
        classes: [
            'net.hazen.hazennstuff.Entity.Mobs.Wizards.Evil.ReignOfTyros.Aptos.AptosEntity'
        ]
    },
    {
        name: 'aeromancy_additions',
        required: false,
        classes: [
            'com.snackpirate.aeromancy.spells.summon_breeze.SummonedBreeze'
        ]
    }
];

function initializeCoreMod() {
    var transformers = {};
    var diagnostics = createDiagnostics();

    for (var groupIndex = 0; groupIndex < TARGET_GROUPS.length; groupIndex++) {
        var group = TARGET_GROUPS[groupIndex];
        ensureGroupDiagnostics(diagnostics, group.name);
        for (var classIndex = 0; classIndex < group.classes.length; classIndex++) {
            var className = group.classes[classIndex];
            diagnostics.declaredTargets++;
            diagnostics.groups[group.name].declared++;
            transformers['maidspell_hurt_head_' + group.name + '_' + classIndex] = createHurtTransformer(className, group.name, diagnostics);
            diagnostics.registeredTargets++;
            diagnostics.groups[group.name].registered++;
        }
    }

    logSummary(diagnostics);
    return transformers;
}

function createDiagnostics() {
    return {
        declaredTargets: 0,
        registeredTargets: 0,
        missingClasses: [],
        missingMethods: [],
        groups: {}
    };
}

function ensureGroupDiagnostics(diagnostics, groupName) {
    if (!diagnostics.groups[groupName]) {
        diagnostics.groups[groupName] = {
            declared: 0,
            registered: 0,
            missingClasses: 0,
            missingMethods: 0
        };
    }
}

function createHurtTransformer(className, groupName, diagnostics) {
    return {
        'target': {
            'type': 'METHOD',
            'class': className,
            'methodName': HURT_METHOD,
            'methodDesc': HURT_DESC
        },
        'transformer': function (methodNode) {
            try {
                if (methodNode == null) {
                    logWarn('Skipping hurt instrumentation for ' + className + ' [' + groupName + '] because method node is null');
                    return methodNode;
                }
                if (methodNode.name !== HURT_METHOD || methodNode.desc !== HURT_DESC) {
                    logDebug('Skipping transformer for ' + className + ' [' + groupName + '] due to method mismatch: ' + methodNode.name + methodNode.desc);
                    return methodNode;
                }
                if (hasHook(methodNode, 'maidspell$enterHurtHook')) {
                    logDebug('Skipped already instrumented hurt method for ' + className);
                    return methodNode;
                }

                injectHead(methodNode);
                injectExit(methodNode);
                methodNode.maxStack = methodNode.maxStack + 4;
                logDebug('Instrumented hurt method for ' + className + ' [' + groupName + ']');
                return methodNode;
            } catch (error) {
                logError('Failed to instrument hurt for ' + className + ' [' + groupName + ']: ' + stringifyError(error));
                return methodNode;
            }
        }
    };
}

function recordMissingClass(diagnostics, group, className) {
    diagnostics.missingClasses.push(className);
    diagnostics.groups[group.name].missingClasses++;
    if (group.required) {
        logWarn('Required hurt hook target class missing: ' + className);
    } else {
        logDebug('Optional hurt hook target class missing: ' + className + ' [' + group.name + ']');
    }
}

function recordMissingMethod(diagnostics, group, className) {
    diagnostics.missingMethods.push(className);
    diagnostics.groups[group.name].missingMethods++;
    if (group.required) {
        logWarn('Required hurt hook target missing declared hurt method: ' + className + ' ' + HURT_DESC);
    } else {
        logDebug('Optional hurt hook target missing declared hurt method: ' + className + ' [' + group.name + ']');
    }
}

function logSummary(diagnostics) {
    logInfo('Hurt hook transformer summary: declaredTargets=' + diagnostics.declaredTargets
        + ', registeredTransformers=' + diagnostics.registeredTargets
        + ' (registration count only; does not prove runtime class/method presence)');

    var groupNames = Object.keys(diagnostics.groups);
    for (var i = 0; i < groupNames.length; i++) {
        var groupName = groupNames[i];
        var groupDiagnostics = diagnostics.groups[groupName];
        logDebug('Group ' + groupName + ': declaredTargets=' + groupDiagnostics.declared
            + ', registeredTransformers=' + groupDiagnostics.registered);
    }
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

function stringifyError(error) {
    if (error == null) {
        return 'unknown error';
    }
    if (error.message) {
        return String(error.message);
    }
    return String(error);
}

function logDebug(message) {
    logToHook('maidspell$coremodDebug', message);
}

function logInfo(message) {
    logToHook('maidspell$coremodInfo', message);
}

function logWarn(message) {
    logToHook('maidspell$coremodWarn', message);
}

function logError(message) {
    logToHook('maidspell$coremodError', message);
}

function logToHook(methodName, message) {
    try {
        var level = 'DEBUG';
        if (methodName === 'maidspell$coremodInfo') {
            level = 'INFO';
        } else if (methodName === 'maidspell$coremodWarn') {
            level = 'WARN';
        } else if (methodName === 'maidspell$coremodError') {
            level = 'ERROR';
        }
        ASMAPI.log(level, '[MaidSpell/Coremod] {}', String(message));
    } catch (ignored) {
    }
}
