package org.lwjglx.opengl;

import com.lllllwjgl3.boot.DisplayCompat;
import com.lllllwjgl3.boot.InputDiagnostics;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import org.lwjglx.BufferUtils;
import org.lwjglx.LWJGLException;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

public final class Display {
    public static final Display INSTANCE = new Display();

    private static String title = "";
    private static long handle = -1L;
    private static boolean resizable;
    private static DisplayMode displayMode = new DisplayMode(640, 480, 24, 60);
    private static int width;
    private static int height;
    private static int xPos;
    private static int yPos;
    private static boolean windowResized;
    private static GLFWWindowSizeCallback sizeCallback;
    private static ByteBuffer[] cachedIcons;

    private Display() { }

    public static String getTitle() {
        return title;
    }

    public static void setTitle(String value) {
        title = value == null ? "" : value;
        if (isCreated()) GLFW.glfwSetWindowTitle(handle, title);
    }

    public static long getHandle() {
        return handle;
    }

    public static void setHandle(long value) {
        handle = value;
    }

    public static DisplayMode getDisplayMode() {
        return displayMode;
    }

    public static void setDisplayMode(DisplayMode mode) {
        if (mode == null) throw new NullPointerException("mode");
        displayMode = mode;
        width = mode.getWidth();
        height = mode.getHeight();
        DisplayCompat.setDisplayMode(handle, mode);
    }

    public static int getWidth() {
        return width;
    }

    public static void setWidth(int value) {
        width = value;
    }

    public static int getHeight() {
        return height;
    }

    public static void setHeight(int value) {
        height = value;
    }

    public static int getXPos() {
        return xPos;
    }

    public static void setXPos(int value) {
        xPos = value;
    }

    public static int getYPos() {
        return yPos;
    }

    public static void setYPos(int value) {
        yPos = value;
    }

    public static DisplayMode getDesktopDisplayMode() {
        DisplayMode[] modes = getAvailableDisplayModes();
        DisplayMode result = null;
        for (DisplayMode mode : modes) {
            if (result == null || mode.getWidth() * mode.getHeight() > result.getWidth() * result.getHeight()) {
                result = mode;
            }
        }
        return result;
    }

    public static int setIcon(ByteBuffer[] icons) {
        if (icons == null) throw new NullPointerException("icons");
        if (cachedIcons == null || !sameIcons(cachedIcons, icons)) {
            cachedIcons = new ByteBuffer[icons.length];
            for (int i = 0; i < icons.length; i++) cachedIcons[i] = cloneByteBuffer(icons[i]);
        }
        if (!isCreated()) return 0;
        GLFW.glfwSetWindowIcon(handle, iconsToGlfwBuffer(cachedIcons));
        return 1;
    }

    private static boolean sameIcons(ByteBuffer[] left, ByteBuffer[] right) {
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) return false;
        }
        return true;
    }

    private static ByteBuffer cloneByteBuffer(ByteBuffer original) {
        ByteBuffer clone = BufferUtils.createByteBuffer(original.capacity());
        int position = original.position();
        clone.put(original);
        ((Buffer) original).position(position);
        ((Buffer) clone).flip();
        return clone;
    }

    private static GLFWImage.Buffer iconsToGlfwBuffer(ByteBuffer[] icons) {
        GLFWImage.Buffer buffer = GLFWImage.malloc(icons.length);
        for (ByteBuffer icon : icons) {
            int pixels = icon.limit() / 4;
            int dimension = (int) Math.sqrt(pixels);
            buffer.put(GLFWImage.malloc().set(dimension, dimension, icon));
        }
        buffer.flip();
        return buffer;
    }

    public static void update() {
        windowResized = false;
        InputDiagnostics.pollEvents();
        if (Mouse.isCreated()) Mouse.poll();
        if (Keyboard.isCreated()) Keyboard.poll();
        GLFW.glfwSwapBuffers(handle);
    }

    public static void create(PixelFormat pixelFormat) throws LWJGLException {
        DisplayCompat.checkNotCreated(handle);
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        handle = DisplayCompat.createWindow(displayMode.getWidth(), displayMode.getHeight(), title,
                MemoryUtil.NULL, MemoryUtil.NULL);
        width = displayMode.getWidth();
        height = displayMode.getHeight();
        GLFW.glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        sizeCallback = GLFWWindowSizeCallback.create(Display::resizeCallback);
        GLFW.glfwSetWindowSizeCallback(handle, sizeCallback);
        Mouse.create();
        Keyboard.create();
        GLFW.glfwShowWindow(handle);
        if (cachedIcons != null) setIcon(cachedIcons);
    }

    public static void create() throws LWJGLException {
        create(new PixelFormat());
    }

    public static void setFullscreen(boolean fullscreen) {
        if (!isCreated()) return;
        if (fullscreen) {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, width, height, displayMode.getFrequency());
        } else {
            GLFW.glfwSetWindowMonitor(handle, MemoryUtil.NULL, xPos, yPos, width, height, GLFW.GLFW_DONT_CARE);
        }
    }

    public static DisplayMode[] getAvailableDisplayModes() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) return new DisplayMode[0];
        org.lwjgl.glfw.GLFWVidMode.Buffer modes = GLFW.glfwGetVideoModes(monitor);
        if (modes == null) return new DisplayMode[0];
        java.util.LinkedHashSet<DisplayMode> result = new java.util.LinkedHashSet<DisplayMode>();
        for (int i = 0; i < modes.limit(); i++) {
            org.lwjgl.glfw.GLFWVidMode mode = modes.get(i);
            result.add(new DisplayMode(mode.width(), mode.height(),
                    mode.redBits() + mode.greenBits() + mode.blueBits(), mode.refreshRate()));
        }
        return result.toArray(new DisplayMode[result.size()]);
    }

    private static void resizeCallback(long window, int newWidth, int newHeight) {
        if (window == handle) {
            windowResized = true;
            width = newWidth;
            height = newHeight;
        }
    }

    public void destroyWindow() {
        if (!isCreated()) return;
        if (sizeCallback != null) {
            sizeCallback.free();
            sizeCallback = null;
        }
        Mouse.destroy();
        Keyboard.destroy();
        GLFW.glfwDestroyWindow(handle);
        handle = -1L;
        GL.setCapabilities(null);
    }

    public static void destroy() {
        INSTANCE.destroyWindow();
        GLFW.glfwTerminate();
        GLFWErrorCallback callback = GLFW.glfwSetErrorCallback(null);
        if (callback != null) callback.free();
    }

    public static boolean isCreated() {
        return DisplayCompat.isCreated(handle);
    }

    public static boolean isCloseRequested() {
        return isCreated() && GLFW.glfwWindowShouldClose(handle);
    }

    public static boolean isActive() {
        return DisplayCompat.isActive(handle);
    }

    public static void setResizable(boolean value) {
        resizable = value;
        DisplayCompat.setResizable(handle, value);
    }

    public static void sync(int fps) {
        Sync.sync(fps);
    }

    public static void setVSyncEnabled(boolean enabled) {
        GLFW.glfwSwapInterval(enabled ? 1 : 0);
    }

    public static boolean wasResized() {
        return windowResized;
    }

    public static int getX() {
        xPos = DisplayCompat.getX(handle, xPos);
        return xPos;
    }

    public static int getY() {
        yPos = DisplayCompat.getY(handle, yPos);
        return yPos;
    }

    public static void setLocation(int x, int y) {
        xPos = x;
        yPos = y;
        DisplayCompat.setLocation(handle, x, y);
    }
}
