package com.lllllwjgl3.boot;

import java.nio.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.stb.STBImage;
import org.lwjgl.opengl.GL11;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.DisplayMode;

/** Real GLFW lifecycle and STB decoding tests, explicitly opt-in. */
public final class WindowCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
        for (int cycle=0; cycle<2; cycle++) {
            if (Display.isCreated()) throw new AssertionError("Stale created flag");
            if (Display.isActive()) throw new AssertionError("Uncreated window reported focused");
            Display.class.getMethod("setLocation", int.class, int.class).invoke(null, 60, 70);
            if ((Integer)Display.class.getMethod("getX").invoke(null) != 60) throw new AssertionError("Pre-create location");
            Display.setDisplayMode(new DisplayMode(320, 240));
            Display.setTitle("LWJGL window regression");
            try {
                Display.class.getMethod("create").invoke(null);
                if (!Display.isCreated()) throw new AssertionError("Create failed");
                Display.class.getMethod("setLocation", int.class, int.class).invoke(null, 120, 140);
                GLFW.glfwPollEvents();
                IntBuffer x=BufferUtils.createIntBuffer(1), y=BufferUtils.createIntBuffer(1);
                GLFW.glfwGetWindowPos(Display.getHandle(), x, y);
                boolean matched = false;
                for (int attempt=0; attempt<50 && !matched; attempt++) {
                    GLFW.glfwPollEvents();
                    GLFW.glfwGetWindowPos(Display.getHandle(), x, y);
                    int actualX=(Integer)Display.class.getMethod("getX").invoke(null);
                    int actualY=(Integer)Display.class.getMethod("getY").invoke(null);
                    matched = actualX == x.get(0) && actualY == y.get(0);
                    if (!matched) Thread.sleep(10);
                }
                if (!matched) throw new AssertionError("Position differs from GLFW after compositor settled");
                Display.update();
                Display.setDisplayMode(new DisplayMode(480, 320));
                boolean resized = false;
                IntBuffer width = BufferUtils.createIntBuffer(1), height = BufferUtils.createIntBuffer(1);
                for (int attempt = 0; attempt < 50; attempt++) {
                    Display.update();
                    GLFW.glfwGetWindowSize(Display.getHandle(), width, height);
                    if (width.get(0) == 480 && height.get(0) == 320
                            && Display.getWidth() == 480 && Display.getHeight() == 320) {
                        resized = true;
                        break;
                    }
                    Thread.sleep(10);
                }
                if (!resized) throw new AssertionError("setDisplayMode did not resize the live window");
                for (boolean enabled : new boolean[] {true, false, true}) {
                    Display.setResizable(enabled);
                    boolean nativeResizable = GLFW.glfwGetWindowAttrib(Display.getHandle(), GLFW.GLFW_RESIZABLE) == GLFW.GLFW_TRUE;
                    if (nativeResizable != enabled) throw new AssertionError("Runtime resizable flag ignored");
                }
                GLFW.glfwHideWindow(Display.getHandle());
                for (int attempt = 0; attempt < 50 && Display.isActive(); attempt++) {
                    GLFW.glfwPollEvents();
                    Thread.sleep(10);
                }
                if (Display.isActive()) throw new AssertionError("Hidden window still active");
                GLFW.glfwShowWindow(Display.getHandle());
                GLFW.glfwPollEvents();
                boolean focusMatched = false;
                for (int attempt = 0; attempt < 50; attempt++) {
                    // Showing a window schedules a compositor focus change. Compare
                    // only once the native state is stable across the facade query.
                    GLFW.glfwPollEvents();
                    int before = GLFW.glfwGetWindowAttrib(Display.getHandle(), GLFW.GLFW_FOCUSED);
                    boolean active = Display.isActive();
                    int after = GLFW.glfwGetWindowAttrib(Display.getHandle(), GLFW.GLFW_FOCUSED);
                    if (before == after && active == (after == GLFW.GLFW_TRUE)) {
                        focusMatched = true;
                        break;
                    }
                    Thread.sleep(10);
                }
                if (!focusMatched) throw new AssertionError("Focus state differs from GLFW after settling");
                fullscreenRoundTrip();
                decodeImage();
            } finally { Display.destroy(); }
            if (Display.isCreated()) throw new AssertionError("Destroy left stale handle");
            Display.destroy();
        }
        System.out.println("PASS: create/move/resize/resizable/focus/destroy/recreate; STB RGBA PNG decoding");
    }

    private static void fullscreenRoundTrip() throws Exception {
        long handle = Display.getHandle();
        org.lwjgl.glfw.GLFWVidMode desktop = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if (desktop == null) throw new AssertionError("Missing desktop video mode");
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        try {
            DisplayMode selected = null;
            for (DisplayMode mode : Display.getAvailableDisplayModes()) {
                if (mode.getWidth() == desktop.width() && mode.getHeight() == desktop.height()
                        && mode.getFrequency() == desktop.refreshRate()) selected = mode;
            }
            if (selected == null) throw new AssertionError("Current desktop mode missing from available modes");
            Display.setDisplayMode(selected);
            Display.setFullscreen(true);
            Display.update();
            if (GLFW.glfwGetWindowMonitor(handle) == 0) throw new AssertionError("Fullscreen did not attach monitor");
            Display.setFullscreen(false);
            Display.setDisplayMode(new DisplayMode(480, 320));
            Display.update();
            if (GLFW.glfwGetWindowMonitor(handle) != 0) throw new AssertionError("Windowed mode retained monitor");
            if (Display.getHandle() != handle || !GL11.glIsTexture(texture))
                throw new AssertionError("Fullscreen transition lost GL resources");
        } finally {
            if (GLFW.glfwGetWindowMonitor(handle) != 0) Display.setFullscreen(false);
            GL11.glDeleteTextures(texture);
        }
    }

    private static void decodeImage() throws Exception {
        BufferedImage image=new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xff00ff00); image.setRGB(1,0,0xffff0000);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ImageIO.write(image,"png",out);
        byte[] png=out.toByteArray();
        ByteBuffer input=BufferUtils.createByteBuffer(png.length); input.put(png); input.flip();
        int[] w={0},h={0},channels={0};
        ByteBuffer decoded=STBImage.stbi_load_from_memory(input,w,h,channels,4);
        if(decoded==null) throw new AssertionError(STBImage.stbi_failure_reason());
        try {
            if(w[0]!=2 || h[0]!=1 || decoded.get(0)!=0 || (decoded.get(1)&255)!=255
                    || (decoded.get(4)&255)!=255) throw new AssertionError("Decoded pixels differ");
        } finally { STBImage.stbi_image_free(decoded); }
    }
}
