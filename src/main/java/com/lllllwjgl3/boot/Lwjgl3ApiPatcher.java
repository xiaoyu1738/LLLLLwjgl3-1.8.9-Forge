package com.lllllwjgl3.boot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.*;

/** Adds the small set of LWJGL2 descriptors still required by Minecraft. */
public final class Lwjgl3ApiPatcher {
    private static final String GL20 = "org/lwjgl/opengl/GL20";
    private static final String SHADER_SOURCE = "(ILjava/nio/ByteBuffer;)V";

    private Lwjgl3ApiPatcher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        patchJar(new File(args[0]), new File(args[1]));
    }

    private static void patchJar(File input, File output) throws IOException {
        JarFile jar = new JarFile(input);
        try {
            JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
            try {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    byte[] data = read(jar, entry);
                    if ((GL20 + ".class").equals(entry.getName())) data = patchGl20(data);
                    if ("org/lwjgl/opengl/ARBShaderObjects.class".equals(entry.getName())) data = patchShaderApi(data, "ARB");
                    if ("org/lwjgl/opengl/GL11.class".equals(entry.getName())) data = patchGl11(data);
                    if ("org/lwjglx/opengl/Display.class".equals(entry.getName())) data = patchDisplay(data);
                    if ("com/github/zarzelcow/legacylwjgl3/implementation/glfw/GLFWMouseImplementation.class".equals(entry.getName()))
                        data = patchMouseScroll(data);
                    if ("com/github/zarzelcow/legacylwjgl3/implementation/glfw/GLFWKeyboardImplementation.class".equals(entry.getName()))
                        data = patchKeyboard(data);
                    JarEntry copy = new JarEntry(entry.getName());
                    out.putNextEntry(copy);
                    out.write(data);
                    out.closeEntry();
                }
            } finally {
                out.close();
            }
        } finally {
            jar.close();
        }
    }

    static byte[] patchGl20(byte[] input) {
        return patchShaderApi(input, "");
    }

    static byte[] patchShaderApi(byte[] input, String suffix) {
        ClassReader reader = new ClassReader(input);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        for (int size = 1; size <= 4; size++) {
            addAlias(node, "glUniform" + size + suffix, "glUniform" + size + "fv" + suffix,
                    "(ILjava/nio/FloatBuffer;)V");
            addAlias(node, "glUniform" + size + suffix, "glUniform" + size + "iv" + suffix,
                    "(ILjava/nio/IntBuffer;)V");
        }
        for (int size = 2; size <= 4; size++) {
            addAlias(node, "glUniformMatrix" + size + suffix, "glUniformMatrix" + size + "fv" + suffix,
                    "(IZLjava/nio/FloatBuffer;)V");
        }
        String shaderSource = "glShaderSource" + suffix;
        boolean hasSource = false;
        for (MethodNode method : node.methods) {
            if (shaderSource.equals(method.name) && SHADER_SOURCE.equals(method.desc)) hasSource = true;
        }
        if (!hasSource) {
            MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    shaderSource, SHADER_SOURCE, null, null);
            bridge.visitCode();
            bridge.visitVarInsn(Opcodes.ILOAD, 0);
            bridge.visitVarInsn(Opcodes.ALOAD, 1);
            bridge.visitMethodInsn(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/Lwjgl3ApiCompat",
                    shaderSource, SHADER_SOURCE, false);
            bridge.visitInsn(Opcodes.RETURN);
            bridge.visitMaxs(2, 2);
            bridge.visitEnd();
            node.methods.add(bridge);
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] patchGl11(byte[] input) {
        ClassReader reader = new ClassReader(input);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        // LWJGL3 uses the native vector suffix on these buffer overloads.
        addAlias(node, "glGetFloat", "glGetFloatv", "(ILjava/nio/FloatBuffer;)V");
        addAlias(node, "glGetInteger", "glGetIntegerv", "(ILjava/nio/IntBuffer;)V");
        addAlias(node, "glGetDouble", "glGetDoublev", "(ILjava/nio/DoubleBuffer;)V");
        addAlias(node, "glGetBoolean", "glGetBooleanv", "(ILjava/nio/ByteBuffer;)V");
        addAlias(node, "glMultMatrix", "glMultMatrixf", "(Ljava/nio/FloatBuffer;)V");
        addAlias(node, "glMultMatrix", "glMultMatrixd", "(Ljava/nio/DoubleBuffer;)V");
        addAlias(node, "glLoadMatrix", "glLoadMatrixf", "(Ljava/nio/FloatBuffer;)V");
        addAlias(node, "glLoadMatrix", "glLoadMatrixd", "(Ljava/nio/DoubleBuffer;)V");
        addAlias(node, "glFog", "glFogfv", "(ILjava/nio/FloatBuffer;)V");
        addAlias(node, "glFog", "glFogiv", "(ILjava/nio/IntBuffer;)V");
        addAlias(node, "glLight", "glLightfv", "(IILjava/nio/FloatBuffer;)V");
        addAlias(node, "glLight", "glLightiv", "(IILjava/nio/IntBuffer;)V");
        addAlias(node, "glLightModel", "glLightModelfv", "(ILjava/nio/FloatBuffer;)V");
        addAlias(node, "glLightModel", "glLightModeliv", "(ILjava/nio/IntBuffer;)V");
        addAlias(node, "glTexEnv", "glTexEnvfv", "(IILjava/nio/FloatBuffer;)V");
        addAlias(node, "glTexEnv", "glTexEnviv", "(IILjava/nio/IntBuffer;)V");
        addAlias(node, "glTexGen", "glTexGenfv", "(IILjava/nio/FloatBuffer;)V");
        addAlias(node, "glTexGen", "glTexGeniv", "(IILjava/nio/IntBuffer;)V");
        addAlias(node, "glTexGen", "glTexGendv", "(IILjava/nio/DoubleBuffer;)V");
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] patchKeyboard(byte[] input) {
        ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, ClassReader.EXPAND_FRAMES);
        for (FieldNode field : node.fields) if ("lwjgl2TextEvents".equals(field.name)) return input;
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "lwjgl2PendingKey", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "lwjgl2PendingState", "B", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "lwjgl2PendingChar", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "lwjgl2PendingNanos", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "lwjgl2PendingRepeat", "Z", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "lwjgl2PendingValid", "Z", null, null));
        boolean patched = false;
        for (MethodNode method : node.methods) {
            if ("putKeyboardEvent".equals(method.name) && "(IBIJZ)V".equals(method.desc)) {
                InsnList trace = new InsnList();
                trace.add(new VarInsnNode(Opcodes.ILOAD, 1));
                trace.add(new VarInsnNode(Opcodes.ILOAD, 2));
                trace.add(new VarInsnNode(Opcodes.ILOAD, 3));
                trace.add(new VarInsnNode(Opcodes.LLOAD, 4));
                trace.add(new VarInsnNode(Opcodes.ILOAD, 6));
                trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/InputDiagnostics",
                        "legacyKey", "(IIIJZ)V", false));
                method.instructions.insert(trace);
            }
            if ("lambda$createKeyboard$0".equals(method.name) && "(JIIII)V".equals(method.desc)) {
                InsnList trace = new InsnList();
                trace.add(new VarInsnNode(Opcodes.LLOAD, 1));
                for (int slot = 3; slot <= 6; slot++) trace.add(new VarInsnNode(Opcodes.ILOAD, slot));
                trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/InputDiagnostics",
                        "key", "(JIIII)V", false));
                method.instructions.insert(trace);
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) insn;
                        if ("putKeyboardEvent".equals(call.name) && "(IBIJZ)V".equals(call.desc)) {
                            call.name = "lwjgl2QueueKeyEvent";
                            break;
                        }
                    }
                }
            }
            if ("createKeyboard".equals(method.name)) {
                InsnList reset = new InsnList();
                reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reset.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "key_down_buffer", "[B"));
                reset.add(new InsnNode(Opcodes.ICONST_0));
                reset.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "fill", "([BB)V", false));
                reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reset.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "event_queue", "Lorg/lwjglx/opengl/EventQueue;"));
                reset.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "org/lwjglx/opengl/EventQueue", "clearEvents", "()V", false));
                reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reset.add(new InsnNode(Opcodes.ICONST_0));
                reset.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "lwjgl2PendingValid", "Z"));
                method.instructions.insert(reset);
            }
            if ("pollKeyboard".equals(method.name) && "(Ljava/nio/ByteBuffer;)V".equals(method.desc)) {
                InsnList flush = new InsnList();
                flush.add(new VarInsnNode(Opcodes.ALOAD, 0));
                flush.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name,
                        "lwjgl2FlushPending", "()V", false));
                method.instructions.insert(flush);
            }
            if ("destroyKeyboard".equals(method.name)) {
                // Remove native callback pointers before freeing their trampolines.
                InsnList detach = new InsnList();
                for (String kind : new String[] {"Key", "Char"}) {
                    detach.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    detach.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "windowHandle", "J"));
                    detach.add(new InsnNode(Opcodes.ACONST_NULL));
                    detach.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/glfw/GLFW",
                            "glfwSet" + kind + "Callback", "(JLorg/lwjgl/glfw/GLFW" + kind
                            + "CallbackI;)Lorg/lwjgl/glfw/GLFW" + kind + "Callback;", false));
                    detach.add(new InsnNode(Opcodes.POP));
                }
                method.instructions.insert(detach);
            }
            if (!"lambda$createKeyboard$1".equals(method.name) || !"(JI)V".equals(method.desc)) continue;
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 3);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "lwjgl2QueueCharacter", "(I)V", false);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(2, 4);
            method.visitEnd();
            patched = true;
        }
        if (!patched) throw new IllegalStateException("GLFW text callback changed; update compatibility patch");
        addKeyboardQueueMethods(node);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "lwjgl2TextEvents", "Z", null, 1));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void addKeyboardQueueMethods(ClassNode node) {
        MethodNode flush = new MethodNode(Opcodes.ACC_PRIVATE, "lwjgl2FlushPending", "()V", null, null);
        flush.visitCode();
        org.objectweb.asm.Label done = new org.objectweb.asm.Label();
        flush.visitVarInsn(Opcodes.ALOAD, 0);
        flush.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingValid", "Z");
        flush.visitJumpInsn(Opcodes.IFEQ, done);
        flush.visitVarInsn(Opcodes.ALOAD, 0);
        flush.visitVarInsn(Opcodes.ALOAD, 0); flush.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingKey", "I");
        flush.visitVarInsn(Opcodes.ALOAD, 0); flush.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingState", "B");
        flush.visitVarInsn(Opcodes.ALOAD, 0); flush.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingChar", "I");
        flush.visitVarInsn(Opcodes.ALOAD, 0); flush.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingNanos", "J");
        flush.visitVarInsn(Opcodes.ALOAD, 0); flush.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingRepeat", "Z");
        flush.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "putKeyboardEvent", "(IBIJZ)V", false);
        flush.visitVarInsn(Opcodes.ALOAD, 0); flush.visitInsn(Opcodes.ICONST_0);
        flush.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingValid", "Z");
        flush.visitLabel(done); flush.visitInsn(Opcodes.RETURN); flush.visitMaxs(8, 1); flush.visitEnd();
        node.methods.add(flush);

        MethodNode queueKey = new MethodNode(Opcodes.ACC_PRIVATE, "lwjgl2QueueKeyEvent", "(IBIJZ)V", null, null);
        queueKey.visitCode();
        org.objectweb.asm.Label store = new org.objectweb.asm.Label();
        org.objectweb.asm.Label direct = new org.objectweb.asm.Label();
        queueKey.visitVarInsn(Opcodes.ALOAD, 0);
        queueKey.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingValid", "Z");
        queueKey.visitJumpInsn(Opcodes.IFEQ, direct);
        queueKey.visitVarInsn(Opcodes.ALOAD, 0);
        queueKey.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "lwjgl2FlushPending", "()V", false);
        queueKey.visitLabel(direct);
        queueKey.visitVarInsn(Opcodes.ILOAD, 2);
        queueKey.visitJumpInsn(Opcodes.IFNE, store);
        queueKey.visitVarInsn(Opcodes.ALOAD, 0); queueKey.visitVarInsn(Opcodes.ILOAD, 1);
        queueKey.visitVarInsn(Opcodes.ILOAD, 2); queueKey.visitVarInsn(Opcodes.ILOAD, 3);
        queueKey.visitVarInsn(Opcodes.LLOAD, 4); queueKey.visitVarInsn(Opcodes.ILOAD, 6);
        queueKey.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "putKeyboardEvent", "(IBIJZ)V", false);
        queueKey.visitInsn(Opcodes.RETURN);
        queueKey.visitLabel(store);
        queueKey.visitVarInsn(Opcodes.ALOAD, 0); queueKey.visitVarInsn(Opcodes.ILOAD, 1);
        queueKey.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingKey", "I");
        queueKey.visitVarInsn(Opcodes.ALOAD, 0); queueKey.visitVarInsn(Opcodes.ILOAD, 2);
        queueKey.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingState", "B");
        queueKey.visitVarInsn(Opcodes.ALOAD, 0); queueKey.visitVarInsn(Opcodes.ILOAD, 3);
        queueKey.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingChar", "I");
        queueKey.visitVarInsn(Opcodes.ALOAD, 0); queueKey.visitVarInsn(Opcodes.LLOAD, 4);
        queueKey.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingNanos", "J");
        queueKey.visitVarInsn(Opcodes.ALOAD, 0); queueKey.visitVarInsn(Opcodes.ILOAD, 6);
        queueKey.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingRepeat", "Z");
        queueKey.visitVarInsn(Opcodes.ALOAD, 0); queueKey.visitInsn(Opcodes.ICONST_1);
        queueKey.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingValid", "Z");
        queueKey.visitInsn(Opcodes.RETURN); queueKey.visitMaxs(8, 7); queueKey.visitEnd();
        node.methods.add(queueKey);

        MethodNode character = new MethodNode(Opcodes.ACC_PRIVATE, "lwjgl2QueueCharacter", "(I)V", null, null);
        character.visitCode();
        org.objectweb.asm.Label standalone = new org.objectweb.asm.Label();
        org.objectweb.asm.Label codePoint = new org.objectweb.asm.Label();
        org.objectweb.asm.Label supplementary = new org.objectweb.asm.Label();
        org.objectweb.asm.Label characterDone = new org.objectweb.asm.Label();
        character.visitVarInsn(Opcodes.ALOAD, 0);
        character.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingValid", "Z");
        character.visitJumpInsn(Opcodes.IFEQ, standalone);
        character.visitVarInsn(Opcodes.ALOAD, 0);
        character.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingState", "B");
        character.visitJumpInsn(Opcodes.IFEQ, standalone);
        character.visitVarInsn(Opcodes.ALOAD, 0);
        character.visitFieldInsn(Opcodes.GETFIELD, node.name, "lwjgl2PendingChar", "I");
        character.visitJumpInsn(Opcodes.IFNE, standalone);
        character.visitVarInsn(Opcodes.ILOAD, 1);
        character.visitLdcInsn(0xFFFF);
        character.visitJumpInsn(Opcodes.IF_ICMPGT, supplementary);
        character.visitVarInsn(Opcodes.ALOAD, 0); character.visitVarInsn(Opcodes.ILOAD, 1);
        character.visitFieldInsn(Opcodes.PUTFIELD, node.name, "lwjgl2PendingChar", "I");
        character.visitVarInsn(Opcodes.ALOAD, 0);
        character.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "lwjgl2FlushPending", "()V", false);
        character.visitInsn(Opcodes.RETURN);
        character.visitLabel(standalone);
        character.visitVarInsn(Opcodes.ALOAD, 0);
        character.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "lwjgl2FlushPending", "()V", false);
        character.visitVarInsn(Opcodes.ILOAD, 1); character.visitLdcInsn(0xFFFF);
        character.visitJumpInsn(Opcodes.IF_ICMPGT, supplementary);
        character.visitVarInsn(Opcodes.ALOAD, 0); character.visitInsn(Opcodes.ICONST_0);
        character.visitInsn(Opcodes.ICONST_1); character.visitVarInsn(Opcodes.ILOAD, 1);
        character.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        character.visitInsn(Opcodes.ICONST_0);
        character.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "putKeyboardEvent", "(IBIJZ)V", false);
        character.visitInsn(Opcodes.RETURN);
        character.visitLabel(supplementary);
        character.visitVarInsn(Opcodes.ILOAD, 1);
        character.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "toChars", "(I)[C", false);
        character.visitVarInsn(Opcodes.ASTORE, 2);
        character.visitInsn(Opcodes.ICONST_0); character.visitVarInsn(Opcodes.ISTORE, 3);
        character.visitLabel(codePoint);
        character.visitVarInsn(Opcodes.ILOAD, 3); character.visitVarInsn(Opcodes.ALOAD, 2);
        character.visitInsn(Opcodes.ARRAYLENGTH); character.visitJumpInsn(Opcodes.IF_ICMPGE, characterDone);
        character.visitVarInsn(Opcodes.ALOAD, 0); character.visitInsn(Opcodes.ICONST_0);
        character.visitInsn(Opcodes.ICONST_1); character.visitVarInsn(Opcodes.ALOAD, 2);
        character.visitVarInsn(Opcodes.ILOAD, 3); character.visitInsn(Opcodes.CALOAD);
        character.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        character.visitInsn(Opcodes.ICONST_0);
        character.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "putKeyboardEvent", "(IBIJZ)V", false);
        character.visitIincInsn(3, 1); character.visitJumpInsn(Opcodes.GOTO, codePoint);
        character.visitLabel(characterDone); character.visitInsn(Opcodes.RETURN);
        character.visitMaxs(7, 4); character.visitEnd();
        node.methods.add(character);
    }

    static byte[] patchMouseScroll(byte[] input) {
        ClassReader reader = new ClassReader(input);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        final String remainder = "lwjgl2ScrollRemainder";
        for (FieldNode field : node.fields) if (remainder.equals(field.name)) return input;
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, remainder, "D", null, null));
        boolean patched = false;
        for (MethodNode method : node.methods) {
            if ("grabMouse".equals(method.name) && "(Z)V".equals(method.desc)) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() != Opcodes.RETURN) continue;
                    InsnList trace = new InsnList();
                    trace.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    trace.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "windowHandle", "J"));
                    trace.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/InputDiagnostics",
                            "grab", "(JZ)V", false));
                    method.instructions.insertBefore(insn, trace);
                }
            }
            if ("createMouse".equals(method.name) || "destroyMouse".equals(method.name)) {
                InsnList reset = new InsnList();
                reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reset.add(new InsnNode(Opcodes.DCONST_0));
                reset.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, remainder, "D"));
                reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reset.add(new InsnNode(Opcodes.ICONST_0));
                reset.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "accum_dz", "I"));
                reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reset.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "button_states", "[B"));
                reset.add(new InsnNode(Opcodes.ICONST_0));
                reset.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "fill", "([BB)V", false));
                reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reset.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, "reset", "()V", false));
                if ("destroyMouse".equals(method.name)) {
                    for (String kind : new String[] {"MouseButton", "CursorPos", "Scroll", "CursorEnter"}) {
                        reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        reset.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "windowHandle", "J"));
                        reset.add(new InsnNode(Opcodes.ACONST_NULL));
                        reset.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/glfw/GLFW",
                                "glfwSet" + kind + "Callback", "(JLorg/lwjgl/glfw/GLFW" + kind
                                + "CallbackI;)Lorg/lwjgl/glfw/GLFW" + kind + "Callback;", false));
                        reset.add(new InsnNode(Opcodes.POP));
                    }
                }
                method.instructions.insert(reset);
            }
            if (!"lambda$createMouse$2".equals(method.name) || !"(JDD)V".equals(method.desc)) continue;
            // GLFW offsets are wheel detents; LWJGL2 uses 120 units per detent.
            // Carry sub-unit fractions between callbacks instead of losing touchpad input.
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitInsn(Opcodes.DUP);
            method.visitFieldInsn(Opcodes.GETFIELD, node.name, remainder, "D");
            method.visitVarInsn(Opcodes.DLOAD, 5);
            method.visitLdcInsn(120.0);
            method.visitInsn(Opcodes.DMUL);
            method.visitInsn(Opcodes.DADD);
            method.visitFieldInsn(Opcodes.PUTFIELD, node.name, remainder, "D");
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitFieldInsn(Opcodes.GETFIELD, node.name, remainder, "D");
            method.visitInsn(Opcodes.D2I);
            method.visitVarInsn(Opcodes.ISTORE, 7);
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitInsn(Opcodes.DUP);
            method.visitFieldInsn(Opcodes.GETFIELD, node.name, remainder, "D");
            method.visitVarInsn(Opcodes.ILOAD, 7);
            method.visitInsn(Opcodes.I2D);
            method.visitInsn(Opcodes.DSUB);
            method.visitFieldInsn(Opcodes.PUTFIELD, node.name, remainder, "D");
            org.objectweb.asm.Label done = new org.objectweb.asm.Label();
            method.visitVarInsn(Opcodes.ILOAD, 7);
            method.visitJumpInsn(Opcodes.IFEQ, done);
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitInsn(Opcodes.DUP);
            method.visitFieldInsn(Opcodes.GETFIELD, node.name, "accum_dz", "I");
            method.visitVarInsn(Opcodes.ILOAD, 7);
            method.visitInsn(Opcodes.IADD);
            method.visitFieldInsn(Opcodes.PUTFIELD, node.name, "accum_dz", "I");
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitInsn(Opcodes.ICONST_M1);
            method.visitInsn(Opcodes.ICONST_0);
            method.visitVarInsn(Opcodes.ILOAD, 7);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, "putMouseEvent", "(BBIJ)V", false);
            method.visitLabel(done);
            method.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(7, 8);
            method.visitEnd();
            patched = true;
        }
        if (!patched) throw new IllegalStateException("GLFW scroll callback changed; update compatibility patch");
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] patchDisplay(byte[] input) {
        ClassReader reader = new ClassReader(input);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        String owner = node.name;
        MethodNode create = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "create", "()V", null, new String[] {"org/lwjglx/LWJGLException"});
        create.visitCode();
        create.visitTypeInsn(Opcodes.NEW, "org/lwjglx/opengl/PixelFormat");
        create.visitInsn(Opcodes.DUP);
        create.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/lwjglx/opengl/PixelFormat", "<init>", "()V", false);
        create.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "create", "(Lorg/lwjglx/opengl/PixelFormat;)V", false);
        create.visitInsn(Opcodes.RETURN);
        create.visitMaxs(2, 0);
        addIfMissing(node, create);
        for (String axis : new String[] {"X", "Y"}) {
            MethodNode get = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get" + axis, "()I", null, null);
            get.visitCode();
            get.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "getHandle", "()J", false);
            get.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "get" + axis + "Pos", "()I", false);
            get.visitMethodInsn(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/DisplayCompat",
                    "get" + axis, "(JI)I", false);
            get.visitInsn(Opcodes.IRETURN);
            get.visitMaxs(3, 0);
            addIfMissing(node, get);
        }
        MethodNode location = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "setLocation", "(II)V", null, null);
        location.visitCode();
        location.visitVarInsn(Opcodes.ILOAD, 0);
        location.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "setXPos", "(I)V", false);
        location.visitVarInsn(Opcodes.ILOAD, 1);
        location.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "setYPos", "(I)V", false);
        location.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "getHandle", "()J", false);
        location.visitVarInsn(Opcodes.ILOAD, 0);
        location.visitVarInsn(Opcodes.ILOAD, 1);
        location.visitMethodInsn(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/DisplayCompat", "setLocation", "(JII)V", false);
        location.visitInsn(Opcodes.RETURN);
        location.visitMaxs(4, 2);
        addIfMissing(node, location);
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if ("org/lwjgl/glfw/GLFW".equals(call.owner) && "glfwPollEvents".equals(call.name)) {
                    call.owner = "com/lllllwjgl3/boot/InputDiagnostics";
                    call.name = "pollEvents";
                }
            }
            if ("create".equals(method.name) && "(Lorg/lwjglx/opengl/PixelFormat;)V".equals(method.desc)) {
                boolean guarded = false;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if ("com/lllllwjgl3/boot/DisplayCompat".equals(call.owner) && "checkNotCreated".equals(call.name)) guarded = true;
                    if ("org/lwjgl/glfw/GLFW".equals(call.owner) && "glfwCreateWindow".equals(call.name)) {
                        call.owner = "com/lllllwjgl3/boot/DisplayCompat";
                        call.name = "createWindow";
                    }
                }
                if (!guarded) {
                    InsnList guard = new InsnList();
                    guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "getHandle", "()J", false));
                    guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/DisplayCompat", "checkNotCreated", "(J)V", false));
                    method.instructions.insert(guard);
                }
            }
            String argument = null;
            String field = null;
            if ("setDisplayMode".equals(method.name) && "(Lorg/lwjglx/opengl/DisplayMode;)V".equals(method.desc)) {
                argument = "Lorg/lwjglx/opengl/DisplayMode;";
                field = "displayMode";
            } else if ("setResizable".equals(method.name) && "(Z)V".equals(method.desc)) {
                argument = "Z";
                field = "resizable";
            } else if (("isActive".equals(method.name) || "isCreated".equals(method.name)) && "()Z".equals(method.desc)) {
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();
                method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "getHandle", "()J", false);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/DisplayCompat", method.name, "(J)Z", false);
                method.visitInsn(Opcodes.IRETURN);
                method.maxStack = 2;
                continue;
            }
            if (argument == null) continue;
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            int load = "Z".equals(argument) ? Opcodes.ILOAD : Opcodes.ALOAD;
            if (load == Opcodes.ALOAD) {
                method.visitVarInsn(load, 0);
                method.visitLdcInsn("mode");
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "requireNonNull",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
                method.visitInsn(Opcodes.POP);
            }
            method.visitVarInsn(load, 0);
            method.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, argument);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "getHandle", "()J", false);
            method.visitVarInsn(load, 0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/lllllwjgl3/boot/DisplayCompat",
                    method.name, "(J" + argument + ")V", false);
            method.visitInsn(Opcodes.RETURN);
            method.maxStack = 3;
            method.maxLocals = 1;
        }
        // The upstream facade leaves a stale handle after destruction, so
        // isCreated() stays true and a second destroy touches freed callbacks.
        for (MethodNode method : node.methods) {
            if (!"destroyWindow".equals(method.name) || !"()V".equals(method.desc)) continue;
            boolean patched = false;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTSTATIC
                        && "handle".equals(((FieldInsnNode) insn).name)) patched = true;
            }
            if (patched) continue;
            LabelNode alive = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "isCreated", "()Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFNE, alive));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(alive);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.RETURN) continue;
                InsnList reset = new InsnList();
                reset.add(new LdcInsnNode(-1L));
                reset.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, "handle", "J"));
                reset.add(new InsnNode(Opcodes.ACONST_NULL));
                reset.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, "sizeCallback", "Lorg/lwjgl/glfw/GLFWWindowSizeCallback;"));
                reset.add(new InsnNode(Opcodes.ACONST_NULL));
                reset.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL", "setCapabilities", "(Lorg/lwjgl/opengl/GLCapabilities;)V", false));
                method.instructions.insertBefore(insn, reset);
            }
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void addIfMissing(ClassNode node, MethodNode bridge) {
        for (MethodNode method : node.methods) {
            if (bridge.name.equals(method.name) && bridge.desc.equals(method.desc)) return;
        }
        node.methods.add(bridge);
    }

    private static void addAlias(ClassNode node, String legacy, String target, String descriptor) {
        boolean found = false;
        for (MethodNode method : node.methods) {
            if (!descriptor.equals(method.desc)) continue;
            if (legacy.equals(method.name)) return;
            if (target.equals(method.name)) found = true;
        }
        if (!found) throw new IllegalStateException("Missing bridge target: " + target + descriptor);
        MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                legacy, descriptor, null, null);
        bridge.visitCode();
        int slot = 0;
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            bridge.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot);
            slot += argument.getSize();
        }
        bridge.visitMethodInsn(Opcodes.INVOKESTATIC, node.name, target, descriptor, false);
        bridge.visitInsn(Opcodes.RETURN);
        bridge.visitMaxs(slot, slot);
        bridge.visitEnd();
        node.methods.add(bridge);
    }

    private static byte[] read(JarFile jar, JarEntry entry) throws IOException {
        InputStream input = jar.getInputStream(entry);
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
