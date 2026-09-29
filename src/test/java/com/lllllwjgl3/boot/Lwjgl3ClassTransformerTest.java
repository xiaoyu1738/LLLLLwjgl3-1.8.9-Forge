package com.lllllwjgl3.boot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipFile;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.InsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Lwjgl3ClassTransformerTest {
    @Test
    public void rewritesMinecraftDisplayAndInputLinkageToLwjglx() throws Exception {
        assertEquals("org/lwjglx/input/Keyboard",
                Lwjgl3ClassTransformer.remapInternalName("org/lwjgl/input/Keyboard"));
        assertEquals("org/lwjglx/opengl/Display",
                Lwjgl3ClassTransformer.remapInternalName("org/lwjgl/opengl/Display"));
        assertEquals("org/lwjgl/opengl/GL11",
                Lwjgl3ClassTransformer.remapInternalName("org/lwjgl/opengl/GL11"));
        assertEquals("org/lwjglx/openal/AL10",
                Lwjgl3ClassTransformer.remapInternalName("org/lwjgl/openal/AL10"));
    }

    @Test
    public void rewritesOwnersAndDescriptorsButLeavesNativeGlBindings() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/InputUser", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "()V", null, null);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "next", "()Z", false);
        method.visitInsn(Opcodes.POP);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glGetError", "()I", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new Lwjgl3ClassTransformer().transform("fixture.InputUser",
                "fixture.InputUser", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        boolean keyboardRelocated = false;
        boolean glKeptNative = false;
        for (org.objectweb.asm.tree.MethodNode methodNode : node.methods) {
            for (AbstractInsnNode instruction = methodNode.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invoke = (MethodInsnNode) instruction;
                keyboardRelocated |= "org/lwjglx/input/Keyboard".equals(invoke.owner);
                glKeptNative |= "org/lwjgl/opengl/GL11".equals(invoke.owner);
            }
        }
        assertTrue(keyboardRelocated);
        assertTrue(glKeptNative);
    }

    @Test
    public void leavesClassesWithoutLwjglReferencesUntouched() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Plain", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "()V", null, null);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        byte[] original = writer.toByteArray();
        byte[] transformed = new Lwjgl3ClassTransformer().transform("fixture.Plain", "fixture.Plain", original);
        assertTrue(java.util.Arrays.equals(original, transformed));
    }

    @Test
    public void transformsClassesContainingStackMapFrames() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/FramedInputUser", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "(Z)V", null, null);
        org.objectweb.asm.Label done = new org.objectweb.asm.Label();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, done);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "next", "()Z", false);
        method.visitInsn(Opcodes.POP);
        method.visitLabel(done);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new Lwjgl3ClassTransformer().transform("fixture.FramedInputUser",
                "fixture.FramedInputUser", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        boolean keyboardRelocated = false;
        for (org.objectweb.asm.tree.MethodNode methodNode : node.methods) {
            for (AbstractInsnNode instruction = methodNode.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode
                        && "org/lwjglx/input/Keyboard".equals(((MethodInsnNode) instruction).owner)) {
                    keyboardRelocated = true;
                }
            }
        }
        assertTrue(keyboardRelocated);
    }

    @Test
    public void protectsMinecraftCrashReportBeforeOpenGlContextExists() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/minecraft/client/Minecraft", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                "func_71396_d", "(Lnet/minecraft/crash/CrashReport;)Lnet/minecraft/crash/CrashReport;",
                null, null);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new Lwjgl3ClassTransformer().transform(
                "net.minecraft.client.Minecraft", "net.minecraft.client.Minecraft", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);

        MethodNode target = null;
        for (MethodNode candidate : node.methods) {
            if ("func_71396_d".equals(candidate.name)) {
                target = candidate;
                break;
            }
        }
        assertTrue(target != null);
        assertEquals(2, target.instructions.size());
        assertTrue(target.instructions.get(0) instanceof VarInsnNode);
        assertEquals(Opcodes.ALOAD, target.instructions.get(0).getOpcode());
        assertEquals(1, ((VarInsnNode) target.instructions.get(0)).var);
        assertTrue(target.instructions.get(1) instanceof InsnNode);
        assertEquals(Opcodes.ARETURN, target.instructions.get(1).getOpcode());
    }

    @Test
    public void protectsObfuscatedCrashReportSignatureToo() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/minecraft/client/Minecraft", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                "addGraphicsAndWorldToCrashReport", "(Lb;)Lb;", null, null);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new Lwjgl3ClassTransformer().transform(
                "net.minecraft.client.Minecraft", "net.minecraft.client.Minecraft", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        MethodNode target = null;
        for (MethodNode candidate : node.methods) {
            if ("addGraphicsAndWorldToCrashReport".equals(candidate.name)) {
                target = candidate;
                break;
            }
        }
        assertTrue(target != null);
        assertEquals(Opcodes.ALOAD, target.instructions.get(0).getOpcode());
        assertEquals(Opcodes.ARETURN, target.instructions.get(1).getOpcode());
    }

    @Test
    public void protectsNotchNamedMinecraftCrashReportMethod() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "ave", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "b", "(Lb;)Lb;", null, null);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new Lwjgl3ClassTransformer().transform(
                "ave", null, writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        MethodNode target = null;
        for (MethodNode candidate : node.methods) {
            if ("b".equals(candidate.name) && "(Lb;)Lb;".equals(candidate.desc)) {
                target = candidate;
                break;
            }
        }
        assertTrue(target != null);
        assertEquals(Opcodes.ALOAD, target.instructions.get(0).getOpcode());
        assertEquals(Opcodes.ARETURN, target.instructions.get(1).getOpcode());
    }

    @Test
    public void characterOnlyEventsCannotResolveToShortcutKeys() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "ave", null, "java/lang/Object", null);
        // runTick(): key == 0 ? character + 256 : key
        emitEventKeyTernary(writer, "s", true);
        // dispatchKeypresses(): key == 0 ? character : key
        emitEventKeyTernary(writer, "Z", false);
        writer.visitEnd();

        byte[] transformed = new Lwjgl3ClassTransformer().transform("ave", null, writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        for (MethodNode method : node.methods) {
            boolean readsCharacter = false;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode
                        && "getEventCharacter".equals(((MethodInsnNode) insn).name)) readsCharacter = true;
            }
            if ("s".equals(method.name)) assertTrue("runTick must keep char+256 bindings", readsCharacter);
            if ("Z".equals(method.name)) assertTrue("dispatchKeypresses must ignore text", !readsCharacter);
        }
    }

    private static void emitEventKeyTernary(ClassWriter writer, String name, boolean offset) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()I", null, null);
        org.objectweb.asm.Label useKey = new org.objectweb.asm.Label();
        org.objectweb.asm.Label done = new org.objectweb.asm.Label();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "getEventKey", "()I", false);
        method.visitJumpInsn(Opcodes.IFNE, useKey);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "getEventCharacter", "()C", false);
        if (offset) {
            method.visitIntInsn(Opcodes.SIPUSH, 256);
            method.visitInsn(Opcodes.IADD);
        }
        method.visitJumpInsn(Opcodes.GOTO, done);
        method.visitLabel(useKey);
        method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "getEventKey", "()I", false);
        method.visitLabel(done);
        method.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
    }

    @Test
    public void lwjglxOpenAlFacadeIsPresent() throws Exception {
        ZipFile jar = new ZipFile(new File("build/relocated/legacy-lwjgl3-relocated.jar"));
        try {
            assertTrue(jar.getEntry("org/lwjglx/input/Keyboard.class") != null);
            // GL is an LWJGL3 native binding, not part of the LWJGL2 facade.
            // A substring-based relocation rule used to produce the missing
            // org.lwjglx.opengl.GL class here.
            assertTrue(jar.getEntry("org/lwjglx/opengl/GL.class") == null);
            java.io.InputStream glContext = jar.getInputStream(jar.getEntry("org/lwjglx/opengl/GLContext.class"));
            try {
                assertTrue(hasMethod(readAll(glContext), "getFunctionAddress", "(Ljava/lang/String;)J"));
            } finally {
                glContext.close();
            }
        } finally {
            jar.close();
        }
        ClassNode display = new ClassNode();
        InputStream displayBytes = Lwjgl3ClassTransformerTest.class.getClassLoader()
                .getResourceAsStream("org/lwjglx/opengl/Display.class");
        assertTrue(displayBytes != null);
        new ClassReader(readAll(displayBytes)).accept(display, 0);
        for (MethodNode method : display.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    assertTrue(!"org/lwjglx/opengl/GL".equals(((MethodInsnNode) instruction).owner));
                }
            }
        }
        assertEquals("org/lwjglx/openal/AL10",
                Lwjgl3ClassTransformer.remapInternalName("org/lwjgl/openal/AL10"));
    }

    private static byte[] transform(String resource, String className) throws Exception {
        InputStream source = Lwjgl3ClassTransformerTest.class.getClassLoader()
                .getResourceAsStream(resource);
        assertTrue("missing " + resource, source != null);
        return new Lwjgl3ClassTransformer().transform(className, className, readAll(source));
    }

    private static boolean hasMethod(byte[] bytes, String name, String desc) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) return true;
        }
        return false;
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
