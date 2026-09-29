package com.lllllwjgl3.boot;

import org.lwjgl.glfw.GLFW;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.DisplayMode;

/** Invalid window dimensions must leave a recoverable display lifecycle. */
public final class WindowCreationFailureSmoke {
    public static void main(String[] args) throws Exception {
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
        Display.setDisplayMode(new DisplayMode(0, 240));
        boolean failed = false;
        try {
            Display.class.getMethod("create").invoke(null);
        } catch (java.lang.reflect.InvocationTargetException expected) {
            failed = true;
        }
        if (!failed) throw new AssertionError("Invalid window dimensions accepted");
        if (Display.isCreated()) throw new AssertionError("Failed creation left display marked created");
        Display.destroy();
        Display.destroy();
        Display.setDisplayMode(new DisplayMode(320, 240));
        try {
            Display.class.getMethod("create").invoke(null);
            long first = Display.getHandle();
            failed = false;
            try {
                Display.class.getMethod("create").invoke(null);
            } catch (java.lang.reflect.InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IllegalStateException)) throw expected;
                failed = true;
            }
            if (!failed || Display.getHandle() != first)
                throw new AssertionError("Repeated create replaced a live window");
            Display.update();
        } finally { Display.destroy(); }
        System.out.println("PASS: invalid creation/double cleanup/recovery; duplicate create preserves live window");
    }
}
