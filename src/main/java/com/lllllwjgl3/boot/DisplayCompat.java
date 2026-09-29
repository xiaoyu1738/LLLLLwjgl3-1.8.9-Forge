package com.lllllwjgl3.boot;

import java.nio.IntBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjglx.opengl.DisplayMode;

/** LWJGL2 window position API. Query GLFW so compositor moves are reflected. */
public final class DisplayCompat {
    private DisplayCompat() { }

    public static void checkNotCreated(long window) {
        if (window > 0) throw new IllegalStateException("Display is already created");
    }

    public static long createWindow(int width, int height, CharSequence title, long monitor, long share) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window dimensions must be positive");
        long window = GLFW.glfwCreateWindow(width, height, title, monitor, share);
        if (window == 0) throw new IllegalStateException("GLFW could not create the display window");
        return window;
    }

    public static boolean isCreated(long window) {
        return window > 0;
    }

    public static void setDisplayMode(long window, DisplayMode mode) {
        if (window <= 0) return;
        long monitor = GLFW.glfwGetWindowMonitor(window);
        if (monitor == 0) {
            GLFW.glfwSetWindowSize(window, mode.getWidth(), mode.getHeight());
        } else {
            GLFW.glfwSetWindowMonitor(window, monitor, 0, 0,
                    mode.getWidth(), mode.getHeight(), mode.getFrequency());
        }
    }

    public static boolean isActive(long window) {
        return window > 0 && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    public static void setResizable(long window, boolean resizable) {
        if (window > 0) GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_RESIZABLE,
                resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
    }

    public static int getX(long window, int fallback) {
        return position(window, fallback, true);
    }

    public static int getY(long window, int fallback) {
        return position(window, fallback, false);
    }

    private static int position(long window, int fallback, boolean xAxis) {
        if (window <= 0 || GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) return fallback;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(window, x, y);
            return (xAxis ? x : y).get(0);
        }
    }

    public static void setLocation(long window, int x, int y) {
        // Wayland assigns positions in the compositor and disallows this request.
        if (window > 0 && GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND)
            GLFW.glfwSetWindowPos(window, x, y);
    }
}
