package com.lllllwjgl3.boot;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class Lwjgl3ApiPatcherTest {
    @Test
    public void shaderBridgesResolveAgainstRealCoreAndArbBindings() throws Exception {
        for (String suffix : new String[] {"", "ARB"}) {
            byte[] original = resource("".equals(suffix) ? "GL20" : "ARBShaderObjects");
            byte[] patched = Lwjgl3ApiPatcher.patchShaderApi(original, suffix);
            checkBridges(original, patched, 12);
            assertEquals(methods(patched), methods(Lwjgl3ApiPatcher.patchShaderApi(patched, suffix)));
            assertTrue(methods(patched).contains("glUniformMatrix4" + suffix + "(IZLjava/nio/FloatBuffer;)V"));
        }
    }

    @Test
    public void gl11BridgesResolveAgainstRealBindings() throws Exception {
        byte[] original = resource("GL11");
        byte[] patched = Lwjgl3ApiPatcher.patchGl11(original);
        checkBridges(original, patched, 19);
        assertEquals(methods(patched), methods(Lwjgl3ApiPatcher.patchGl11(patched)));
    }

    @Test
    public void existingShaderBridgeDoesNotPreventAddingOtherAliases() throws Exception {
        ClassNode node = node(resource("GL20"));
        MethodNode existing = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "glShaderSource", "(ILjava/nio/ByteBuffer;)V", null, null);
        existing.visitInsn(Opcodes.RETURN);
        existing.visitMaxs(0, 2);
        node.methods.add(existing);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        byte[] patched = Lwjgl3ApiPatcher.patchGl20(writer.toByteArray());
        assertTrue(methods(patched).contains("glUniformMatrix4(IZLjava/nio/FloatBuffer;)V"));
    }

    // Every new alias must call a real method with the same descriptor. This
    // catches misspelled suffixes before the relevant render path executes.
    private static void checkBridges(byte[] original, byte[] patched, int added) {
        Set<String> old = methods(original);
        Set<String> all = methods(patched);
        assertEquals(old.size() + added, all.size());
        for (MethodNode m : node(patched).methods) {
            if (old.contains(m.name + m.desc)) continue;
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, m.access);
            int calls = 0;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                calls++;
                MethodInsnNode call = (MethodInsnNode) insn;
                assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                assertEquals(m.desc, call.desc);
                if (call.owner.equals(node(patched).name)) {
                    assertTrue(old.contains(call.name + call.desc));
                } else {
                    assertEquals("com/lllllwjgl3/boot/Lwjgl3ApiCompat", call.owner);
                    assertTrue(call.name.startsWith("glShaderSource"));
                }
            }
            assertEquals(1, calls);
        }
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static Set<String> methods(byte[] bytes) {
        Set<String> result = new HashSet<String>();
        for (MethodNode m : node(bytes).methods) assertTrue(result.add(m.name + m.desc));
        return result;
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = Lwjgl3ApiPatcherTest.class.getResourceAsStream("/org/lwjgl/opengl/" + name + ".class")) {
            assertNotNull(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
