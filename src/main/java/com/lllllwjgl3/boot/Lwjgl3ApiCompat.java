package com.lllllwjgl3.boot;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Small bridges for LWJGL2 method descriptors still used by Minecraft. */
public final class Lwjgl3ApiCompat {
    private Lwjgl3ApiCompat() { }

    /** LWJGL2 passes all remaining bytes with an explicit length, without decoding. */
    public static void glShaderSource(int shader, ByteBuffer source) {
        requireDirect(source);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glShaderSource(shader, stack.pointers(MemoryUtil.memAddress(source)),
                    stack.ints(source.remaining()));
        }
    }

    public static void glShaderSourceARB(int shader, ByteBuffer source) {
        requireDirect(source);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ARBShaderObjects.glShaderSourceARB(shader, stack.pointers(MemoryUtil.memAddress(source)),
                    stack.ints(source.remaining()));
        }
    }

    private static void requireDirect(ByteBuffer source) {
        if (source == null) throw new NullPointerException("source");
        if (!source.isDirect()) throw new IllegalArgumentException("Shader source must be a direct buffer");
    }
}
