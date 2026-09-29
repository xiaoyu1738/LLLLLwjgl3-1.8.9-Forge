package com.lllllwjgl3.boot;

import java.lang.reflect.Field;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.Test;
import org.junit.Assume;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BootstrapClassLoaderTest {
    @Test
    @SuppressWarnings("unchecked")
    public void detachesOrgLwjglFromLaunchWrapperParentDelegation() throws Exception {
        LaunchClassLoader loader = new LaunchClassLoader(new URL[0]);
        Field field = LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
        field.setAccessible(true);
        Set<String> exceptions = (Set<String>) field.get(loader);
        assertTrue(exceptions.contains("org.lwjgl."));

        Lwjgl3Coremod.detachVanillaLwjgl(loader);

        assertFalse(exceptions.contains("org.lwjgl."));
        assertFalse(exceptions.contains("com.lllllwjgl3.boot."));
    }

    @Test
    public void missingRequiredGlfwClassIsFatalAndNamesTheClasspathProblem() {
        ClassLoader missingGlfw = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };

        try {
            GlfwInitHint.apply(missingGlfw, "X11");
            fail("missing GLFW must fail bootstrap");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("LWJGL3 GLFW is missing"));
        }
    }

    @Test
    public void autoBackendDoesNotLoadGlfwEarly() {
        GlfwInitHint.apply(null, "AUTO");
    }

    @Test
    public void coremodJarWinsWhenVanillaLwjglWasOriginallyFirst() throws Exception {
        Assume.assumeTrue("1.8".equals(System.getProperty("java.specification.version")));

        File replacement = new File("build/libs/LLLLLwjgl3-1.8.9-Forge-1.0.0.jar");
        assertTrue(replacement.isFile());
        File vanilla = File.createTempFile("fake-lwjgl2-", ".jar");
        vanilla.deleteOnExit();
        writeDuplicateGl11(vanilla);

        URL replacementUrl = replacement.toURI().toURL();
        LaunchClassLoader loader = new LaunchClassLoader(new URL[] {
                vanilla.toURI().toURL(), replacementUrl
        });
        assertTrue(loader.getResource("com/lllllwjgl3/boot/Lwjgl3Coremod.class") != null);

        Lwjgl3Classpath.prioritizeSource(loader, replacementUrl);
        Lwjgl3Coremod.detachVanillaLwjgl(loader);

        Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11", false, loader);
        URL actual = gl11.getProtectionDomain().getCodeSource().getLocation();
        assertTrue(Lwjgl3Classpath.sameLocation(replacementUrl, actual));
    }

    private static void writeDuplicateGl11(File destination) throws Exception {
        InputStream input = BootstrapClassLoaderTest.class.getClassLoader()
                .getResourceAsStream("org/lwjgl/opengl/GL11.class");
        assertTrue(input != null);
        try {
            JarOutputStream output = new JarOutputStream(new FileOutputStream(destination));
            try {
                output.putNextEntry(new JarEntry("org/lwjgl/opengl/GL11.class"));
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.closeEntry();
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }
}
