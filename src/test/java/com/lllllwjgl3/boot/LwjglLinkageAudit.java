package com.lllllwjgl3.boot;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Static member-linkage audit. Does not claim to validate rendering semantics. */
public final class LwjglLinkageAudit {
    private final Map<String, ClassNode> runtime = new HashMap<String, ClassNode>();
    private final Map<String, Set<String>> missing = new TreeMap<String, Set<String>>();
    private int checked;
    private final Remapper names = new Remapper() {
        @Override public String map(String name) { return Lwjgl3ClassTransformer.remapInternalName(name); }
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("runtime.jar consumer.jar [...]");
        LwjglLinkageAudit audit = new LwjglLinkageAudit();
        for (ClassNode node : readJar(new File(args[0]))) audit.runtime.put(node.name, node);
        for (int i=1; i<args.length; i++) audit.scan(new File(args[i]));
        for (Map.Entry<String, Set<String>> entry : audit.missing.entrySet())
            System.out.println(entry.getKey() + " <- " + entry.getValue());
        System.out.println("Checked references: " + audit.checked + "; missing members: " + audit.missing.size());
        if (!audit.missing.isEmpty()) throw new IllegalStateException("Unresolved LWJGL member references");
    }

    private void scan(File file) throws IOException {
        for (ClassNode node : readJar(file)) {
            // The runtime replaces embedded LWJGL bindings; audit consumer code.
            if (node.name.startsWith("org/lwjgl/") || node.name.startsWith("org/lwjgl3/")
                    || node.name.startsWith("org/lwjglx/")) continue;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) insn;
                        check(call.owner, call.name, call.desc, false, node.name);
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) insn;
                        check(field.owner, field.name, field.desc, true, node.name);
                    }
                }
            }
        }
    }

    private void check(String owner, String name, String descriptor, boolean field, String caller) {
        if (!owner.startsWith("org/lwjgl/")) return;
        checked++;
        String mappedOwner = names.mapType(owner);
        String mappedDescriptor = field ? names.mapDesc(descriptor) : names.mapMethodDesc(descriptor);
        if (exists(mappedOwner, name, mappedDescriptor, field, new HashSet<String>())) return;
        String key = mappedOwner + "." + name + mappedDescriptor;
        Set<String> callers = missing.get(key);
        if (callers == null) { callers = new TreeSet<String>(); missing.put(key, callers); }
        callers.add(caller);
    }

    private boolean exists(String owner, String name, String descriptor, boolean field, Set<String> visited) {
        if (!visited.add(owner)) return false;
        ClassNode node = runtime.get(owner);
        if (node == null) return false;
        if (field) {
            for (FieldNode member : node.fields) if (member.name.equals(name) && member.desc.equals(descriptor)) return true;
        } else {
            for (MethodNode member : node.methods) if (member.name.equals(name) && member.desc.equals(descriptor)) return true;
        }
        if (node.superName != null && exists(node.superName, name, descriptor, field, visited)) return true;
        for (String iface : node.interfaces) if (exists(iface, name, descriptor, field, visited)) return true;
        return false;
    }

    private static List<ClassNode> readJar(File file) throws IOException {
        List<ClassNode> result = new ArrayList<ClassNode>();
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    result.add(node);
                }
            }
        }
        return result;
    }
}
