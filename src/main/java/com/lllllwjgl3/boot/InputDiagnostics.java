package com.lllllwjgl3.boot;

import org.lwjgl.glfw.GLFW;
import org.lwjglx.opengl.Display;

/** Opt-in diagnostics for desktop shortcut/focus conflicts; never reads text callbacks. */
public final class InputDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("lllllwjgl3.inputDiagnostics");
    private static long lastWindow;
    private static int lastFocused = -1, lastCursor = -1;
    private static boolean lastFullscreen;

    private InputDiagnostics() { }

    public static void key(long window, int key, int scancode, int action, int mods) {
        if (!ENABLED || action == GLFW.GLFW_REPEAT) return;
        String name;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT: name = "LSHIFT"; break;
            case GLFW.GLFW_KEY_RIGHT_SHIFT: name = "RSHIFT"; break;
            case GLFW.GLFW_KEY_LEFT_SUPER: name = "LMETA"; break;
            case GLFW.GLFW_KEY_RIGHT_SUPER: name = "RMETA"; break;
            case GLFW.GLFW_KEY_F11: name = "F11"; break;
            case GLFW.GLFW_KEY_W:
                // W is useful for movement diagnosis only while the game captures the cursor.
                if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED) return;
                name = "W";
                break;
            default: return;
        }
        System.out.println("[LLLLLwjgl3/input] key=" + name + " scan=" + scancode
                + " action=" + action + " mods=" + mods + " " + state(window));
    }

    public static void legacyKey(int key, int state, int character, long nanos, boolean repeat) {
        if (!ENABLED) return;
        boolean interestingKey = key == 17 || key == 45 || key == 87 || key == 88
                || key == 42 || key == 54;
        boolean textEvent = key == 0 && character != 0;
        if (!interestingKey && !textEvent) return;
        System.out.println("[LLLLLwjgl3/input] legacy-key=" + key + " state=" + state
                + " char=" + character + " repeat=" + repeat);
    }

    /** Records Minecraft's interpretation of one event and its active bindings. */
    public static void minecraftEvent(Object minecraft, int eventKey, boolean state) {
        if (!ENABLED || minecraft == null) return;
        try {
            java.lang.reflect.Field settingsField = minecraft.getClass().getDeclaredField("t");
            settingsField.setAccessible(true);
            Object settings = settingsField.get(minecraft);
            System.out.println("[LLLLLwjgl3/input] minecraft-event key=" + eventKey
                    + " state=" + state + " bindings="
                    + binding(settings, "ad") + "/" + binding(settings, "as") + "/"
                    + binding(settings, "aq") + "/" + binding(settings, "an"));
        } catch (Throwable ignored) {
            // Diagnostics must never affect the game when mappings differ.
        }
    }

    private static int binding(Object settings, String fieldName) throws Exception {
        if (settings == null) return Integer.MIN_VALUE;
        java.lang.reflect.Field field = settings.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object keyBinding = field.get(settings);
        java.lang.reflect.Method getter = keyBinding.getClass().getMethod("i");
        return ((Integer) getter.invoke(keyBinding)).intValue();
    }

    public static void keyBindingEvent(Class<?> keyBindingClass, int key, boolean state) {
        if (!ENABLED || keyBindingClass == null) return;
        try {
            java.lang.reflect.Field registry = null;
            for (java.lang.reflect.Field field : keyBindingClass.getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(field.getType())) {
                    registry = field;
                    break;
                }
            }
            if (registry == null) return;
            registry.setAccessible(true);
            Object values = registry.get(null);
            StringBuilder bindings = new StringBuilder();
            if (values instanceof Iterable<?>) {
                for (Object value : (Iterable<?>) values) {
                    String description = invokeString(value, "g", "getKeyDescription");
                    if ("key.forward".equals(description) || "key.sneak".equals(description)
                            || "key.screenshot".equals(description) || "key.fullscreen".equals(description)) {
                        if (bindings.length() != 0) bindings.append(',');
                        bindings.append(description).append('=').append(invokeInt(value, "i", "getKeyCode"));
                    }
                }
            }
            System.out.println("[LLLLLwjgl3/input] keybinding-event key=" + key
                    + " state=" + state + " bindings=" + bindings);
        } catch (Throwable ignored) {
            // Diagnostics must never affect the game when mappings differ.
        }
    }

    private static String invokeString(Object target, String... names) throws Exception {
        for (String name : names) {
            try {
                return (String) target.getClass().getMethod(name).invoke(target);
            } catch (NoSuchMethodException ignored) { }
        }
        return "";
    }

    private static int invokeInt(Object target, String... names) throws Exception {
        for (String name : names) {
            try {
                return ((Integer) target.getClass().getMethod(name).invoke(target)).intValue();
            } catch (NoSuchMethodException ignored) { }
        }
        return Integer.MIN_VALUE;
    }

    public static void pollEvents() {
        GLFW.glfwPollEvents();
        if (ENABLED) sample(Display.getHandle());
    }

    public static void grab(long window, boolean grabbed) {
        if (!ENABLED) return;
        System.out.println("[LLLLLwjgl3/input] grabRequested=" + grabbed + " " + state(window));
    }

    private static void sample(long window) {
        if (window <= 0) return;
        int focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED);
        int cursor = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        boolean fullscreen = GLFW.glfwGetWindowMonitor(window) != 0;
        if (lastWindow != window || lastFocused != focused || lastCursor != cursor
                || lastFullscreen != fullscreen) {
            System.out.println("[LLLLLwjgl3/input] window-state " + state(window));
            lastWindow = window;
            lastFocused = focused;
            lastCursor = cursor;
            lastFullscreen = fullscreen;
        }
    }

    private static String state(long window) {
        return "focused=" + (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE)
                + " cursorDisabled=" + (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED)
                + " fullscreen=" + (GLFW.glfwGetWindowMonitor(window) != 0);
    }
}
