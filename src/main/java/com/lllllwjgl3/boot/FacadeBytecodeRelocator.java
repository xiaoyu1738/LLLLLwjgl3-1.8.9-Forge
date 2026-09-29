package com.lllllwjgl3.boot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Patches stale descriptors left by Jar Jar in the Kotlin facade. */
public final class FacadeBytecodeRelocator {
    private static final Set<String> LEGACY_OPENGL_CLASSES = new HashSet<String>(Arrays.asList(
            "Display", "DisplayMode", "PixelFormat", "ContextCapabilities", "GLContext",
            "OpenGLException", "EventQueue", "Sync", "Util"));

    private FacadeBytecodeRelocator() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        File input = new File(args[0]);
        File output = new File(args[1]);
        JarFile jar = new JarFile(input);
        try {
            JarOutputStream out = new JarOutputStream(new FileOutputStream(output));
            try {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    byte[] data = read(jar, entry);
                    if (name.endsWith(".class")) {
                        ClassReader reader = new ClassReader(data);
                        ClassNode node = new ClassNode();
                        reader.accept(node, ClassReader.EXPAND_FRAMES);
                        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                            @Override protected String getCommonSuperClass(String type1, String type2) {
                                return "java/lang/Object";
                            }
                        };
                        RemappingClassAdapter adapter = new RemappingClassAdapter(writer, new Remapper() {
                            @Override public String map(String value) {
                                return mapFacadeName(value);
                            }
                        });
                        node.accept(adapter);
                        data = writer.toByteArray();
                        if ("org/lwjgl/opengl/GLContext".equals(reader.getClassName())
                                || "org/lwjglx/opengl/GLContext".equals(reader.getClassName())) {
                            data = injectFunctionAddressBridge(data);
                        }
                    }
                    JarEntry copy = new JarEntry(name);
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

    private static byte[] injectFunctionAddressBridge(byte[] input) {
        ClassReader reader = new ClassReader(input);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        for (MethodNode method : node.methods) {
            if ("getFunctionAddress".equals(method.name)
                    && "(Ljava/lang/String;)J".equals(method.desc)) return input;
        }
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getFunctionAddress", "(Ljava/lang/String;)J", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL",
                "getFunctionProvider", "()Lorg/lwjgl/system/FunctionProvider;", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/lwjgl/system/FunctionProvider",
                "getFunctionAddress", "(Ljava/lang/CharSequence;)J", true);
        method.visitInsn(Opcodes.LRETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String mapFacadeName(String value) {
        if (value == null) return null;
        if (value.startsWith("org/lwjgl/input/")) return "org/lwjglx/input/" + value.substring(16);
        if (value.startsWith("org/lwjgl/util/")) return "org/lwjglx/util/" + value.substring(15);
        if ("org/lwjgl/LWJGLException".equals(value) || "org/lwjgl/LWJGLUtil".equals(value)
                || "org/lwjgl/Sys".equals(value) || "org/lwjgl/BufferUtils".equals(value)) {
            return "org/lwjglx/" + value.substring(10);
        }
        if (value.startsWith("org/lwjgl/opengl/")) {
            String simple = value.substring(17);
            String base = simple.indexOf('$') < 0 ? simple : simple.substring(0, simple.indexOf('$'));
            if (LEGACY_OPENGL_CLASSES.contains(base)) {
                return "org/lwjglx/opengl/" + simple;
            }
        }
        return value;
    }

    private static byte[] read(JarFile jar, JarEntry entry) throws IOException {
        java.io.InputStream input = jar.getInputStream(entry);
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
