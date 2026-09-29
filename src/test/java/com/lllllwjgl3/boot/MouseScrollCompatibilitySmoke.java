package com.lllllwjgl3.boot;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.DisplayMode;

/** Invokes the actual installed GLFW callback, then consumes the public LWJGL2 API. */
public final class MouseScrollCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
        Display.setDisplayMode(new DisplayMode(320, 240));
        Display.setTitle("Mouse wheel regression");
        try {
            Display.class.getMethod("create").invoke(null);
            Mouse.poll(); Mouse.getDWheel(); while (Mouse.next()) { }
            GLFWScrollCallback callback = GLFW.glfwSetScrollCallback(Display.getHandle(), null);
            if (callback == null) throw new AssertionError("Scroll callback not installed");
            GLFW.glfwSetScrollCallback(Display.getHandle(), callback);
            check(callback, new double[] {1}, 120, 1);
            check(callback, new double[] {-1}, -120, 1);
            check(callback, new double[] {0.25, 0.25}, 60, 2);
            check(callback, new double[] {1.0/256, 1.0/256, 1.0/256}, 1, 1);
            check(callback, new double[] {-1.0/256, -1.0/256, -1.0/256}, -1, 1);
            check(callback, new double[] {1, -1}, 0, 2);
            callback.invoke(Display.getHandle(), 1, 0);
            check(callback, new double[0], 0, 0);
            Mouse.setGrabbed(true);
            check(callback, new double[] {1}, 120, 1);
            Mouse.setGrabbed(false);
            check(callback, new double[] {-1}, -120, 1);
            if (args.length != 0) glideMenu(callback, args[0]);
            callback.invoke(Display.getHandle(), 0, 1);
            org.lwjgl.glfw.GLFWMouseButtonCallback buttons = GLFW.glfwSetMouseButtonCallback(Display.getHandle(), null);
            GLFW.glfwSetMouseButtonCallback(Display.getHandle(), buttons);
            buttons.invoke(Display.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
            Mouse.destroy();
            // Query only: never invoke an old callback after it has been freed.
            if (GLFW.glfwSetScrollCallback(Display.getHandle(), null) != null)
                throw new AssertionError("Destroyed mouse left native scroll callback pointing to freed memory");
            Mouse.create();
            Mouse.poll();
            if (Mouse.isButtonDown(0) || Mouse.getDWheel() != 0)
                throw new AssertionError("Mouse recreation retained held button or wheel state");
            System.out.println("PASS: wheel +/-120; fractional accumulation; queue/poll independence; grab/ungrab; horizontal ignored");
        } finally { Display.destroy(); }
    }

    private static void glideMenu(GLFWScrollCallback callback, String path) throws Exception {
        try (final java.util.jar.JarFile jar = new java.util.jar.JarFile(path)) {
            ClassLoader loader = new ClassLoader(MouseScrollCompatibilitySmoke.class.getClassLoader()) {
                @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                    java.util.jar.JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
                    if (entry == null) throw new ClassNotFoundException(name);
                    try (java.io.InputStream in = jar.getInputStream(entry)) {
                        org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(in);
                        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
                        reader.accept(new org.objectweb.asm.commons.RemappingClassAdapter(writer,
                                new org.objectweb.asm.commons.Remapper() {
                                    @Override public String map(String value) {
                                        return Lwjgl3ClassTransformer.remapInternalName(value);
                                    }
                                }), org.objectweb.asm.ClassReader.EXPAND_FRAMES);
                        byte[] bytes = writer.toByteArray();
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
                }
            };
            Class<?> type = loader.loadClass("me.eldodebug.soar.utils.mouse.Scroll");
            Object scroll = type.newInstance();
            type.getMethod("setMaxScroll", float.class).invoke(scroll, 1000f);
            java.lang.reflect.Field position = type.getDeclaredField("rawScroll");
            position.setAccessible(true);
            callback.invoke(Display.getHandle(), 0, -1);
            Mouse.poll();
            type.getMethod("onScroll").invoke(scroll);
            if (position.getFloat(scroll) != -30f) throw new AssertionError("Glide menu did not move down 30");
            callback.invoke(Display.getHandle(), 0, 1);
            Mouse.poll();
            type.getMethod("onScroll").invoke(scroll);
            if (position.getFloat(scroll) != 0f) throw new AssertionError("Glide menu did not move back up");
            type.getMethod("onScroll").invoke(scroll);
            if (position.getFloat(scroll) != 0f) throw new AssertionError("Glide repeated consumed scroll");
            System.out.println("PASS: actual Glide Scroll.onScroll moved 0 -> -30 -> 0");
        }
    }

    private static void check(GLFWScrollCallback callback, double[] offsets, int expected, int events) {
        for (double y : offsets) callback.invoke(Display.getHandle(), 0, y);
        Mouse.poll();
        int count=0, sum=0;
        while (Mouse.next()) {
            if (Mouse.getEventDWheel()==0) continue; // Window pointer moves are independent events.
            if (Mouse.getEventButton()!=-1) throw new AssertionError("Wheel reported as button");
            count++; sum+=Mouse.getEventDWheel();
        }
        int polled=Mouse.getDWheel();
        if (sum!=expected || polled!=expected || count!=events)
            throw new AssertionError("Expected "+expected+"/"+events+"; queue="+sum+" poll="+polled+" events="+count);
        if (Mouse.getDWheel()!=0) throw new AssertionError("Polling did not consume wheel");
        Mouse.poll();
        if (Mouse.getDWheel()!=0 || Mouse.next()) throw new AssertionError("Wheel repeated on next poll");
        if (expected==120 && polled/4!=30) throw new AssertionError("Glide integer scroll step is zero");
    }
}
