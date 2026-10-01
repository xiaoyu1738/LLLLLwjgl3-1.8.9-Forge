package com.lllllwjgl3.boot;

import java.lang.reflect.Method;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** Real driver checks; run explicitly with Gradle's openGlSmoke task. */
public final class OpenGlCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init failed");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
            window = GLFW.glfwCreateWindow(64, 64, "LWJGL compatibility test", 0, 0);
            if (window == 0) throw new AssertionError("No compatibility context");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Driver: " + GL11.glGetString(GL11.GL_VERSION));
            matrixAndBuffers();
            shaders(false);
            if (!GL.getCapabilities().GL_ARB_shader_objects)
                throw new AssertionError("ARB shader path not testable on this driver");
            shaders(true);
            framebuffer();
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new AssertionError("GL error: " + error);
            System.out.println("PASS: buffer state, matrices, core/ARB shader sources and uniforms, FBO readback");
        } finally {
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void matrixAndBuffers() throws Exception {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        FloatBuffer matrix = BufferUtils.createFloatBuffer(20);
        matrix.position(2); matrix.limit(18);
        for (int i = 0; i < 16; i++) matrix.put(2 + i, i % 5 == 0 ? 1f : 0f);
        matrix.put(14, 3f);
        call(GL11.class, "glLoadMatrixf", new Class<?>[] {FloatBuffer.class}, matrix);
        FloatBuffer out = BufferUtils.createFloatBuffer(20);
        out.position(2); out.limit(18);
        call(GL11.class, "glGetFloatv", new Class<?>[] {int.class, FloatBuffer.class}, GL11.GL_MODELVIEW_MATRIX, out);
        if (out.get(14) != 3f || out.position() != 2 || matrix.position() != 2)
            throw new AssertionError("Matrix/buffer state changed");
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glViewport(2, 3, 32, 31);
        call(GL11.class, "glGetIntegerv", new Class<?>[] {int.class, IntBuffer.class}, GL11.GL_VIEWPORT, viewport);
        if (viewport.get(0) != 2 || viewport.get(3) != 31) throw new AssertionError("Viewport query");
        FloatBuffer color = BufferUtils.createFloatBuffer(4).put(new float[] {1, 1, 1, 1}); color.flip();
        call(GL11.class, "glFogfv", new Class<?>[] {int.class, FloatBuffer.class}, GL11.GL_FOG_COLOR, color);
        call(GL11.class, "glLightfv", new Class<?>[] {int.class, int.class, FloatBuffer.class}, GL11.GL_LIGHT0, GL11.GL_DIFFUSE, color);
        call(GL11.class, "glLightModelfv", new Class<?>[] {int.class, FloatBuffer.class}, GL11.GL_LIGHT_MODEL_AMBIENT, color);
        call(GL11.class, "glTexEnvfv", new Class<?>[] {int.class, int.class, FloatBuffer.class}, GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, color);
    }

    private static void shaders(boolean arb) throws Exception {
        String text = "#version 120\nuniform mat4 matrix; uniform vec4 tint; void main(){gl_Position=matrix*gl_Vertex+tint;}\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer source = BufferUtils.createByteBuffer(bytes.length + 8);
        source.putInt(0xFFFFFFFF).put(bytes).putInt(0xFFFFFFFF);
        source.position(4); source.limit(4 + bytes.length);
        String suffix = arb ? "ARB" : "";
        int shader = arb ? ARBShaderObjects.glCreateShaderObjectARB(GL20.GL_VERTEX_SHADER) : GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        int program = arb ? ARBShaderObjects.glCreateProgramObjectARB() : GL20.glCreateProgram();
        try {
            if (arb) Lwjgl3ApiCompat.glShaderSourceARB(shader, source);
            else Lwjgl3ApiCompat.glShaderSource(shader, source);
            if (source.position() != 4 || source.limit() != 4 + bytes.length) throw new AssertionError("Source buffer mutated");
            String actual = arb ? ARBShaderObjects.glGetShaderSourceARB(shader) : GL20.glGetShaderSource(shader);
            if (!text.equals(actual)) throw new AssertionError("Shader source boundary mismatch");
            if (arb) {
                ARBShaderObjects.glCompileShaderARB(shader);
                if (ARBShaderObjects.glGetObjectParameteriARB(shader, GL20.GL_COMPILE_STATUS) == 0)
                    throw new AssertionError(ARBShaderObjects.glGetInfoLogARB(shader));
                ARBShaderObjects.glAttachObjectARB(program, shader);
                ARBShaderObjects.glLinkProgramARB(program);
                if (ARBShaderObjects.glGetObjectParameteriARB(program, GL20.GL_LINK_STATUS) == 0)
                    throw new AssertionError(ARBShaderObjects.glGetInfoLogARB(program));
                ARBShaderObjects.glUseProgramObjectARB(program);
            } else {
                GL20.glCompileShader(shader);
                if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(shader));
                GL20.glAttachShader(program, shader); GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(GL20.glGetProgramInfoLog(program));
                GL20.glUseProgram(program);
            }
            int location = arb ? ARBShaderObjects.glGetUniformLocationARB(program, "matrix") : GL20.glGetUniformLocation(program, "matrix");
            if (location < 0) throw new AssertionError("Matrix uniform optimized away");
            FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
            for (int i=0;i<16;i++) matrix.put(i, i%5==0 ? 1f : 0f);
            call(arb ? ARBShaderObjects.class : GL20.class, "glUniformMatrix4fv" + suffix,
                    new Class<?>[] {int.class, boolean.class, FloatBuffer.class}, location, false, matrix);
            FloatBuffer read = BufferUtils.createFloatBuffer(16);
            if (arb) ARBShaderObjects.glGetUniformfvARB(program, location, read); else GL20.glGetUniformfv(program, location, read);
            if (read.get(0) != 1 || read.get(15) != 1) throw new AssertionError("Matrix upload/readback failed");
            for (int size=1;size<=4;size++) {
                call(arb ? ARBShaderObjects.class : GL20.class, "glUniform" + size + "fv" + suffix,
                        new Class<?>[] {int.class, FloatBuffer.class}, -1, BufferUtils.createFloatBuffer(size));
                call(arb ? ARBShaderObjects.class : GL20.class, "glUniform" + size + "iv" + suffix,
                        new Class<?>[] {int.class, IntBuffer.class}, -1, BufferUtils.createIntBuffer(size));
            }
            for (int size=2;size<=3;size++) call(arb ? ARBShaderObjects.class : GL20.class, "glUniformMatrix" + size + "fv" + suffix,
                    new Class<?>[] {int.class, boolean.class, FloatBuffer.class}, -1, false, BufferUtils.createFloatBuffer(size*size));
        } finally {
            if (arb) {
                ARBShaderObjects.glUseProgramObjectARB(0);
                ARBShaderObjects.glDeleteObjectARB(program); ARBShaderObjects.glDeleteObjectARB(shader);
            } else {
                GL20.glUseProgram(0); GL20.glDeleteProgram(program); GL20.glDeleteShader(shader);
            }
        }
    }

    private static void framebuffer() {
        int fbo = GL30.glGenFramebuffers(), texture = GL11.glGenTextures();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 4, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("FBO incomplete");
            GL11.glClearColor(1, 0, 0, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            if ((pixel.get(0)&255) != 255 || pixel.get(1) != 0) throw new AssertionError("Readback mismatch");
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(fbo); GL11.glDeleteTextures(texture);
        }
    }

    private static void call(Class<?> api, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = api.getMethod(name, types);
        method.invoke(null, args);
    }
}
