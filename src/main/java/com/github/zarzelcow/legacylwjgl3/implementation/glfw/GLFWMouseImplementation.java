package com.github.zarzelcow.legacylwjgl3.implementation.glfw;

import org.lwjgl.glfw.*;
import com.github.zarzelcow.legacylwjgl3.implementation.input.MouseImplementation;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.EventQueue;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * @author Zarzelcow
 * @created 28/09/2022 - 8:58 PM
 */
public class GLFWMouseImplementation implements MouseImplementation {
    private GLFWMouseButtonCallback buttonCallback;
    private GLFWCursorPosCallback posCallback;
    private GLFWScrollCallback scrollCallback;
    private GLFWCursorEnterCallback cursorEnterCallback;
    private long windowHandle;
    private boolean grabbed;
    private boolean isInsideWindow;

    private final EventQueue event_queue = new EventQueue(Mouse.EVENT_SIZE);

    private final ByteBuffer tmp_event = ByteBuffer.allocate(Mouse.EVENT_SIZE);

    private int last_x;
    private int last_y;
    private int accum_dx;
    private int accum_dy;
    private int accum_dz;
    private double scroll_remainder;
    private byte[] button_states = new byte[this.getButtonCount()];

    @Override
    public void createMouse() {
        this.windowHandle = Display.getHandle();
        Arrays.fill(this.button_states, (byte) 0);
        this.reset();

        if (GLFW.glfwRawMouseMotionSupported() && !Mouse.getPrivilegedBoolean("org.lwjglx.input.Mouse.disableRawInput"))
            GLFW.glfwSetInputMode(this.windowHandle, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);

        this.buttonCallback = GLFWMouseButtonCallback.create((window, button, action, mods) -> {
            byte state = action == GLFW.GLFW_PRESS ? (byte)1 : (byte)0;
            putMouseEvent((byte) button, state, 0, System.nanoTime());
            if (button < button_states.length)
                button_states[button] = state;
        });
        this.posCallback = GLFWCursorPosCallback.create((window, xpos, ypos) -> {
            int x = (int) xpos;
            int y = Display.getHeight() - 1 - (int) ypos; // I don't know why but this un-inverts the y motion of mouse inputs
            int dx = x - last_x;
            int dy = y - last_y;
            if (dx != 0 || dy != 0) {
                accum_dx += dx;
                accum_dy += dy;
                last_x = x;
                last_y = y;
                long nanos = System.nanoTime();
                if (grabbed) {
                    putMouseEventWithCoords((byte)-1, (byte)0, dx, dy, 0, nanos);
                } else {
                    putMouseEventWithCoords((byte)-1, (byte)0, x, y, 0, nanos);
                }
            }
        });
        this.scrollCallback = GLFWScrollCallback.create((window, xoffset, yoffset) -> {
            double scaled = scroll_remainder + yoffset * 120.0;
            int delta = (int) scaled;
            scroll_remainder = scaled - delta;
            if (delta != 0) {
                accum_dz += delta;
                putMouseEvent((byte)-1, (byte)0, delta, System.nanoTime());
            }
        });
        this.cursorEnterCallback = GLFWCursorEnterCallback.create((window, entered) -> this.isInsideWindow = entered);

        GLFW.glfwSetMouseButtonCallback(this.windowHandle, this.buttonCallback);
        GLFW.glfwSetCursorPosCallback(this.windowHandle, this.posCallback);
        GLFW.glfwSetScrollCallback(this.windowHandle, this.scrollCallback);
        GLFW.glfwSetCursorEnterCallback(this.windowHandle, this.cursorEnterCallback);

        this.last_x = Math.max(0, Display.getWidth() / 2);
        this.last_y = Math.max(0, Display.getHeight() / 2);
        setNativeCursorPosition(this.last_x, this.last_y);
    }

    private void putMouseEvent(byte button, byte state, int dz, long nanos) {
        if (grabbed)
            putMouseEventWithCoords(button, state, 0, 0, dz, nanos);
        else
            putMouseEventWithCoords(button, state, last_x, last_y, dz, nanos);
    }

    private void putMouseEventWithCoords(byte button, byte state, int coord1, int coord2, int dz, long nanos) {
        tmp_event.clear();
        tmp_event.put(button).put(state).putInt(coord1).putInt(coord2).putInt(dz).putLong(nanos);
        tmp_event.flip();
        event_queue.putEvent(tmp_event);
    }

    @Override
    public void destroyMouse() {
        GLFW.glfwSetMouseButtonCallback(this.windowHandle, null);
        GLFW.glfwSetCursorPosCallback(this.windowHandle, null);
        GLFW.glfwSetScrollCallback(this.windowHandle, null);
        GLFW.glfwSetCursorEnterCallback(this.windowHandle, null);
        this.buttonCallback.free();
        this.posCallback.free();
        this.scrollCallback.free();
        this.cursorEnterCallback.free();
        this.windowHandle = 0L;
        this.grabbed = false;
        this.isInsideWindow = false;
        this.scroll_remainder = 0.0;
        Arrays.fill(this.button_states, (byte) 0);
        this.reset();
    }

    private void reset() {
        this.event_queue.clearEvents();
        accum_dx = accum_dy = accum_dz = 0;
        scroll_remainder = 0.0;
    }
    @Override
    public void pollMouse(IntBuffer coord_buffer, ByteBuffer buttons_buffer) {
        if (grabbed) {
            coord_buffer.put(0, accum_dx);
            coord_buffer.put(1, accum_dy);
        } else {
            coord_buffer.put(0, last_x);
            coord_buffer.put(1, last_y);
        }
        coord_buffer.put(2, accum_dz);
        accum_dx = accum_dy = accum_dz = 0;
        for (int i = 0; i < button_states.length; i++)
            buttons_buffer.put(i, button_states[i]);
    }

    @Override
    public void readMouse(ByteBuffer readBuffer) {
        event_queue.copyEvents(readBuffer);
    }

    @Override
    public void setCursorPosition(int x, int y) {
        this.last_x = x;
        this.last_y = y;
        this.accum_dx = this.accum_dy = 0;
        setNativeCursorPosition(x, y);
    }

    @Override
    public void grabMouse(boolean grab) {
        int restore_x = this.last_x;
        int restore_y = this.last_y;
        this.grabbed = grab;
        GLFW.glfwSetInputMode(this.windowHandle, GLFW.GLFW_CURSOR, grab ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        if (!grab) setCursorPosition(restore_x, restore_y);
        this.reset();
    }

    private void setNativeCursorPosition(int x, int y) {
        int native_y = Math.max(0, Display.getHeight() - 1 - y);
        GLFW.glfwSetCursorPos(this.windowHandle, x, native_y);
    }

    @Override
    public boolean hasWheel() {
        return true;
    }

    @Override
    public int getButtonCount() {
        return GLFW.GLFW_MOUSE_BUTTON_LAST + 1;
    }

    @Override
    public boolean isInsideWindow() {
        return this.isInsideWindow;
    }
}
