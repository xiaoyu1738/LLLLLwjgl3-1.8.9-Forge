package com.lllllwjgl3.boot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.InsnNode;

/** Rewrites LWJGL2 linkage to the relocated org.lwjglx facade. */
public final class Lwjgl3ClassTransformer implements IClassTransformer {
    private static final String SAFE_CRASH_REPORT_PROPERTY = "lllllwjgl3.safeCrashReport";
    private static final Set<String> LEGACY_OPENGL_CLASSES = new HashSet<String>(Arrays.asList(
            "Display", "DisplayMode", "PixelFormat", "ContextCapabilities", "GLContext",
            "OpenGLException", "EventQueue", "Sync", "Util"));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || shouldSkip(name, transformedName)) return basicClass;
        boolean minecraftClass = isMinecraft(name, transformedName);
        boolean keyBindingClass = isKeyBinding(name, transformedName);
        if (!minecraftClass && !keyBindingClass && !containsLwjglReference(basicClass)) return basicClass;
        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        if (minecraftClass && isSafeCrashReportEnabled()) {
            boolean hit = neutralizeCrashReportGraphics(node);
            System.out.println(hit
                    ? "[LLLLLwjgl3] neutralized Minecraft crash-GL query"
                    : "[LLLLLwjgl3] WARN: neutralizer found NO target in Minecraft");
        }
        if (minecraftClass) {
            instrumentMinecraftInput(node);
            System.out.println(ignoreCharacterShortcutDispatch(node)
                    ? "[LLLLLwjgl3] ignored character-only shortcut dispatch"
                    : "[LLLLLwjgl3] WARN: character shortcut dispatch not found in Minecraft");
        }
        if (keyBindingClass) instrumentKeyBinding(node);
        // RemappingMethodAdapter in ASM 5 requires expanded frames. The
        // adapter rewrites frame locals/stack entries while preserving the
        // original frame semantics; it does not load application classes.
        ClassWriter writer = new ClassWriter(reader, 0);
        RemappingClassAdapter remapper = new RemappingClassAdapter(writer, new Remapper() {
            @Override
            public String map(String internalName) {
                return remapInternalName(internalName);
            }
        });
        node.accept(remapper);
        return writer.toByteArray();
    }

    /** Logs the event key beside Minecraft's live bindings when diagnostics are enabled. */
    private static void instrumentMinecraftInput(ClassNode node) {
        for (MethodNode method : node.methods) {
            boolean instrumented = false;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!("avb".equals(call.owner) || call.owner.endsWith("/KeyBinding"))
                        || !"a".equals(call.name)
                        || !"(IZ)V".equals(call.desc)) continue;
                InsnList trace = new InsnList();
                trace.add(new VarInsnNode(Opcodes.ALOAD, 0));
                trace.add(new VarInsnNode(Opcodes.ILOAD, 1));
                trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard",
                        "getEventKeyState", "()Z", false));
                trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "com/lllllwjgl3/boot/InputDiagnostics", "minecraftEvent",
                        "(Ljava/lang/Object;IZ)V", false));
                method.instructions.insertBefore(insn, trace);
                instrumented = true;
                break;
            }
            if (instrumented) return;
        }
    }

    /**
     * Minecraft.dispatchKeypresses() resolves a key=0 text event to its raw
     * character, so committed 'W'/'X' (87/88) alias KEY_F11/KEY_F12 and toggle
     * fullscreen or take a screenshot. With XIM, GLFW reports the key press and
     * the committed text in separate polls, so every Shift+letter reaches this
     * path. The keyboard loop in runTick() adds 256 to the character and is
     * left untouched; only the unoffset ternary is patched to yield key 0,
     * which the method already ignores.
     */
    static boolean ignoreCharacterShortcutDispatch(ClassNode node) {
        boolean hit = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!isKeyboardCall(insn, "getEventCharacter", "()C")) continue;
                AbstractInsnNode branch = previousCode(method.instructions, insn);
                AbstractInsnNode key = previousCode(method.instructions, branch);
                AbstractInsnNode next = nextCode(insn);
                if (branch == null || branch.getOpcode() != Opcodes.IFNE
                        || !isKeyboardCall(key, "getEventKey", "()I")
                        || next == null || next.getOpcode() != Opcodes.GOTO) continue;
                method.instructions.set(insn, new InsnNode(Opcodes.ICONST_0));
                hit = true;
            }
        }
        return hit;
    }

    private static boolean isKeyboardCall(AbstractInsnNode insn, String name, String desc) {
        if (!(insn instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) insn;
        return ("org/lwjgl/input/Keyboard".equals(call.owner) || "org/lwjglx/input/Keyboard".equals(call.owner))
                && name.equals(call.name) && desc.equals(call.desc);
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode node) {
        AbstractInsnNode current = node == null ? null : node.getNext();
        while (current != null && current.getOpcode() < 0) current = current.getNext();
        return current;
    }

    private static AbstractInsnNode previousCode(InsnList instructions, AbstractInsnNode node) {
        AbstractInsnNode current = node == null ? null : node.getPrevious();
        while (current != null && current.getOpcode() < 0) current = current.getPrevious();
        return current;
    }

    private static boolean isKeyBinding(String name, String transformedName) {
        return "avb".equals(name) || "avb".equals(transformedName)
                || "net.minecraft.client.settings.KeyBinding".equals(name)
                || "net.minecraft.client.settings.KeyBinding".equals(transformedName);
    }

    /** Logs the registry values that Minecraft uses for the four affected actions. */
    private static void instrumentKeyBinding(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals("a") || !method.desc.equals("(IZ)V")
                    || (method.access & Opcodes.ACC_STATIC) == 0) continue;
            InsnList trace = new InsnList();
            trace.add(new LdcInsnNode(Type.getObjectType(node.name)));
            trace.add(new VarInsnNode(Opcodes.ILOAD, 0));
            trace.add(new VarInsnNode(Opcodes.ILOAD, 1));
            trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "com/lllllwjgl3/boot/InputDiagnostics", "keyBindingEvent",
                    "(Ljava/lang/Class;IZ)V", false));
            method.instructions.insert(trace);
            return;
        }
    }

    private static boolean isSafeCrashReportEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(SAFE_CRASH_REPORT_PROPERTY, "true"));
    }

    private static boolean containsLwjglReference(byte[] bytes) {
        final byte[] needle = new byte[] {
                'o', 'r', 'g', '/', 'l', 'w', 'j', 'g', 'l'
        };
        outer:
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static boolean isMinecraft(String name, String transformedName) {
        return "net.minecraft.client.Minecraft".equals(name)
                || "net.minecraft.client.Minecraft".equals(transformedName)
                // FML can invoke us before the deobfuscation transformer has
                // populated transformedName. In the 1.8.9 client Minecraft
                // is the Notch class `ave`.
                || "ave".equals(name)
                || "ave".equals(transformedName);
    }

    /**
     * CrashReport generation can run before GLFW has created a current OpenGL
     * context. LWJGL3's native checks abort the JVM when glGetString is called
     * in that state, hiding the original startup exception. Keep the report
     * object and skip only Minecraft's graphics/world enrichment for now.
     */
    private static boolean neutralizeCrashReportGraphics(ClassNode node) {
        boolean hit = false;
        for (MethodNode method : node.methods) {
            if (!("func_71396_d".equals(method.name)
                    || "addGraphicsToCrashReport".equals(method.name)
                    || "addGraphicsAndWorldToCrashReport".equals(method.name)
                    // Notch names before FML's deobfuscation: ave.b(b).
                    || "b".equals(method.name))) continue;
            // FML normally deobfuscates the descriptor, but a few launch
            // chains invoke this transformer while the class still uses the
            // obfuscated CrashReport type (b). Match both forms.
            if (!("(Lnet/minecraft/crash/CrashReport;)Lnet/minecraft/crash/CrashReport;".equals(method.desc)
                    || "(Lb;)Lb;".equals(method.desc))) continue;

            InsnList replacement = new InsnList();
            replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
            replacement.add(new InsnNode(Opcodes.ARETURN));
            method.instructions = replacement;
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.maxStack = 1;
            method.maxLocals = 2;
            hit = true;
        }
        return hit;
    }

    private static boolean shouldSkip(String name, String transformedName) {
        String value = name != null ? name : transformedName;
        if (value == null) return true;
        return value.startsWith("com.lllllwjgl3.")
                || value.startsWith("org.lwjglx.")
                || value.startsWith("org.lwjgl.");
    }

    /** Map only the LWJGL2 surface; GL11..GL45 remain native LWJGL3 bindings. */
    static String remapInternalName(String internalName) {
        if (internalName == null) return null;
        if (internalName.startsWith("org/lwjgl/input/")) {
            return "org/lwjglx/input/" + internalName.substring("org/lwjgl/input/".length());
        }
        if (internalName.startsWith("org/lwjgl/util/")) {
            return "org/lwjglx/util/" + internalName.substring("org/lwjgl/util/".length());
        }
        if (internalName.startsWith("org/lwjgl/openal/")) {
            return "org/lwjglx/openal/" + internalName.substring("org/lwjgl/openal/".length());
        }
        if ("org/lwjgl/LWJGLException".equals(internalName)
                || "org/lwjgl/LWJGLUtil".equals(internalName)
                || "org/lwjgl/Sys".equals(internalName)) {
            return "org/lwjglx/" + internalName.substring("org/lwjgl/".length());
        }
        if (internalName.startsWith("org/lwjgl/opengl/")) {
            String simple = internalName.substring("org/lwjgl/opengl/".length());
            int nested = simple.indexOf('$');
            String base = nested < 0 ? simple : simple.substring(0, nested);
            if (LEGACY_OPENGL_CLASSES.contains(base)) {
                return "org/lwjglx/opengl/" + simple;
            }
        }
        return internalName;
    }
}
