package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps the public health view aligned with this entity's authoritative health. */
public final class AuthoritativeHealthReturnTransformer implements ClassFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthoritativeHealthReturnTransformer.class);
    private static final String TARGET = MagicalWinefoxBossEntity.class.getName().replace('.', '/');
    private static final String RAW_HEALTH_METHOD = "maidspell$authoritativeHealth";
    private static final Set<String> HEALTH_METHOD_NAMES = Set.of("getHealth", "m_21223_");
    private static boolean installed;

    private boolean transformed;

    private AuthoritativeHealthReturnTransformer() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        Instrumentation instrumentation;
        try {
            instrumentation = ByteBuddyAgent.install();
        } catch (Throwable exception) {
            LOGGER.warn("Could not install the authoritative health return guard", exception);
            return;
        }
        if (!instrumentation.isRetransformClassesSupported()
                || !instrumentation.isModifiableClass(MagicalWinefoxBossEntity.class)) {
            LOGGER.warn("The JVM does not support retransformation of the boss health view");
            return;
        }

        AuthoritativeHealthReturnTransformer transformer = new AuthoritativeHealthReturnTransformer();
        try {
            instrumentation.addTransformer(transformer, true);
            instrumentation.retransformClasses(MagicalWinefoxBossEntity.class);
            if (!transformer.transformed) {
                throw new IllegalStateException("Boss getHealth method was not transformed");
            }
            installed = true;
            LOGGER.info("Installed authoritative health return guard");
        } catch (Throwable exception) {
            instrumentation.removeTransformer(transformer);
            LOGGER.error("Could not transform the boss health view", exception);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!TARGET.equals(className)) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            boolean[] found = {false};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!"()F".equals(descriptor) || !HEALTH_METHOD_NAMES.contains(name)) {
                        return method;
                    }
                    found[0] = true;
                    return new MethodVisitor(Opcodes.ASM9, method) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.FRETURN) {
                                super.visitInsn(Opcodes.POP);
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET,
                                        RAW_HEALTH_METHOD, "()F", false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            }, 0);
            if (!found[0]) {
                LOGGER.error("Boss getHealth method was not found during retransformation");
                return null;
            }
            this.transformed = true;
            return writer.toByteArray();
        } catch (Throwable exception) {
            LOGGER.error("Failed to transform the boss health return", exception);
            return null;
        }
    }
}
