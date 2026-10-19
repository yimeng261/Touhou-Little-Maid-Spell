package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the bundled, unrelocated Byte Buddy agent outside Forge's module layer.
 *
 * <p>The agent must keep its original package and transformation behavior, but
 * another mod may already provide the {@code net.bytebuddy.agent} module. The
 * nested jar is therefore a plain MaidSpell resource rather than a Jar-in-Jar
 * mod dependency. A child-first loader attaches it without adding a second
 * module to ModLauncher's layer.</p>
 */
final class ByteBuddyAgentInstaller {
    private static final String AGENT_RESOURCE =
            "/META-INF/maidspell/byte-buddy-agent-1.15.11.jar";
    private static final String INSTALLER = "net.bytebuddy.agent.Installer";
    private static final Logger LOGGER = LoggerFactory.getLogger(ByteBuddyAgentInstaller.class);

    private ByteBuddyAgentInstaller() {
    }

    static Instrumentation install() throws ReflectiveOperationException {
        try (InputStream input = ByteBuddyAgentInstaller.class.getResourceAsStream(AGENT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled Byte Buddy agent resource");
            }
            Path agentJar = Files.createTempFile("maidspell-bytebuddy-", ".jar");
            try {
                Files.copy(input, agentJar, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Instrumentation fresh = installFreshAgent(agentJar);
                    LOGGER.info("Attached a fresh Byte Buddy agent");
                    return fresh;
                } catch (Throwable exception) {
                    LOGGER.warn("Could not attach a fresh Byte Buddy agent; "
                            + "falling back to the existing instrumentation", exception);
                }
                return installViaByteBuddy(agentJar);
            } finally {
                try {
                    Files.deleteIfExists(agentJar);
                } catch (java.io.IOException ignored) {
                    // Windows keeps the loaded agent jar open until JVM exit.
                    agentJar.toFile().deleteOnExit();
                }
            }
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            throw exception instanceof ReflectiveOperationException reflective
                    ? reflective : new ReflectiveOperationException(exception);
        }
    }

    private static Instrumentation installViaByteBuddy(Path agentJar) throws ReflectiveOperationException {
        try {
            URL[] urls = {agentJar.toUri().toURL()};
            try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                Class<?> agent = Class.forName("net.bytebuddy.agent.ByteBuddyAgent", true, loader);
                Object value = agent.getMethod("install").invoke(null);
                if (value instanceof Instrumentation instrumentation) {
                    return instrumentation;
                }
                throw new IllegalStateException("Byte Buddy agent install returned no Instrumentation");
            }
        } catch (java.io.IOException exception) {
            throw new ReflectiveOperationException(exception);
        }
    }

    /**
     * Loads a new agent environment instead of reusing the instrumentation
     * installed by another mod. The new environment is appended after all
     * agents that were loaded earlier, so its transformers run last.
     */
    private static Instrumentation installFreshAgent(Path agentJar) throws ReflectiveOperationException {
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        Class<?> installer = null;
        Field instrumentationField = null;
        Object previous = null;
        try {
            installer = Class.forName(INSTALLER, true, systemLoader);
            instrumentationField = installer.getDeclaredField("instrumentation");
            instrumentationField.setAccessible(true);
            previous = instrumentationField.get(null);
            instrumentationField.set(null, null);
        } catch (ClassNotFoundException ignored) {
            // The agent jar will provide the Installer class when it is loaded.
        } catch (IllegalAccessException exception) {
            throw new ReflectiveOperationException(exception);
        }
        try {
            allowSelfAttach();
            Class<?> virtualMachine = Class.forName("com.sun.tools.attach.VirtualMachine",
                    true, systemLoader);
            Object vm = virtualMachine.getMethod("attach", String.class)
                    .invoke(null, Long.toString(ProcessHandle.current().pid()));
            try {
                virtualMachine.getMethod("loadAgent", String.class)
                        .invoke(vm, agentJar.toAbsolutePath().toString());
            } finally {
                try {
                    virtualMachine.getMethod("detach").invoke(vm);
                } catch (ReflectiveOperationException ignored) {
                    // The attachment is already complete.
                }
            }
            if (instrumentationField == null) {
                installer = Class.forName(INSTALLER, true, systemLoader);
                instrumentationField = installer.getDeclaredField("instrumentation");
                instrumentationField.setAccessible(true);
            }
            Object value = instrumentationField.get(null);
            if (value instanceof Instrumentation instrumentation) {
                return instrumentation;
            }
            throw new IllegalStateException("Fresh Byte Buddy agent returned no Instrumentation");
        } catch (Throwable exception) {
            if (instrumentationField != null) {
                try {
                    instrumentationField.set(null, previous);
                } catch (IllegalAccessException ignored) {
                    // Preserve the original failure.
                }
            }
            throw exception instanceof ReflectiveOperationException reflective
                    ? reflective : new ReflectiveOperationException(exception);
        }
    }

    private static void allowSelfAttach() {
        try {
            Class<?> hotspotVm = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
            Field allowSelfAttach = hotspotVm.getDeclaredField("ALLOW_ATTACH_SELF");
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Object base = unsafeType.getMethod("staticFieldBase", Field.class).invoke(unsafe, allowSelfAttach);
            long offset = (long) unsafeType.getMethod("staticFieldOffset", Field.class)
                    .invoke(unsafe, allowSelfAttach);
            unsafeType.getMethod("putBoolean", Object.class, long.class, boolean.class)
                    .invoke(unsafe, base, offset, true);
        } catch (Throwable ignored) {
            // Byte Buddy's attachment provider may still support self-attachment.
        }
    }
}
