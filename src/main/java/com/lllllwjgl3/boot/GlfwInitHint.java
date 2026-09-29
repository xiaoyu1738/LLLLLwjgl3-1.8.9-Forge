package com.lllllwjgl3.boot;

import java.lang.reflect.InvocationTargetException;

/** Optional GLFW 3.3+ platform selector, kept out of the compatibility API. */
public final class GlfwInitHint {
    private GlfwInitHint() {
    }

    public static void apply(ClassLoader loader, String backend) {
        if ("AUTO".equals(backend)) return;

        final Class<?> glfwClass;
        try {
            // Use LaunchClassLoader explicitly. The application loader only
            // sees the vanilla LWJGL2 jars supplied by the launcher.
            glfwClass = Class.forName("org.lwjgl.glfw.GLFW", false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "LWJGL3 GLFW is missing from the coremod class path after detaching LWJGL2", e);
        }

        try {
            java.lang.reflect.Method method = glfwClass.getMethod("glfwInitHint", int.class, int.class);
            int platform = ((Integer) glfwClass.getField("GLFW_PLATFORM").get(null)).intValue();
            int value = ((Integer) glfwClass.getField(
                    "WAYLAND".equals(backend) ? "GLFW_PLATFORM_WAYLAND" : "GLFW_PLATFORM_X11").get(null)).intValue();
            method.invoke(null, Integer.valueOf(platform), Integer.valueOf(value));
        } catch (NoSuchMethodException e) {
            warnUnsupportedPlatformHint();
        } catch (NoSuchFieldException e) {
            warnUnsupportedPlatformHint();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access the GLFW platform init hint", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("GLFW rejected the requested " + backend + " backend",
                    e.getCause());
        }
    }

    private static void warnUnsupportedPlatformHint() {
        System.err.println("[LLLLLwjgl3] warning: bundled GLFW does not support explicit platform selection; "
                + "letting GLFW choose its display backend");
    }
}
