package com.lllllwjgl3.boot;

import com.google.common.collect.ImmutableBiMap;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** Exercises Forge's actual descriptor-sensitive lookup without changing its singleton. */
public class ForgeRemappingOrderTest {
    private static final String VECTOR = "Lorg/lwjgl/util/vector/Vector3f;";

    @Test
    public void registeredOrderPreservesForgeFieldAndMethodNames() throws Exception {
        FMLDeobfuscatingRemapper forge = newRemapper();
        ClassWriter fixture = new ClassWriter(0);
        fixture.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "fixture/Obfuscated", null, "java/lang/Object", null);
        fixture.visitField(Opcodes.ACC_PUBLIC, "d", VECTOR, null, null).visitEnd();
        fixture.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "a", "()" + VECTOR,
                null, null).visitEnd();
        fixture.visitEnd();
        Lwjgl3ClassTransformer lwjgl = new Lwjgl3ClassTransformer();

        // Reproduce the old order: both descriptor-sensitive lookups miss.
        byte[] broken = applyForge(forge, lwjgl.transform("fixture.Obfuscated",
                "fixture.Mapped", fixture.toByteArray()));
        ClassNode before = read(broken);
        assertEquals("d", before.fields.get(0).name);
        assertEquals("a", before.methods.get(0).name);

        int order = Lwjgl3Coremod.class.getAnnotation(IFMLLoadingPlugin.SortingIndex.class).value();
        byte[] fixed = fixture.toByteArray();
        if (order > 1000) {
            fixed = lwjgl.transform("fixture.Obfuscated", "fixture.Mapped", applyForge(forge, fixed));
        } else {
            fixed = applyForge(forge, lwjgl.transform("fixture.Obfuscated", "fixture.Mapped", fixed));
        }
        ClassNode after = read(fixed);
        assertEquals("field_178363_d", after.fields.get(0).name);
        assertEquals("func_fixture", after.methods.get(0).name);
        assertEquals("Lorg/lwjglx/util/vector/Vector3f;", after.fields.get(0).desc);
        assertEquals("()Lorg/lwjglx/util/vector/Vector3f;", after.methods.get(0).desc);
    }

    private static FMLDeobfuscatingRemapper newRemapper() throws Exception {
        Constructor<FMLDeobfuscatingRemapper> constructor = FMLDeobfuscatingRemapper.class
                .getDeclaredConstructor();
        constructor.setAccessible(true);
        FMLDeobfuscatingRemapper mapper = constructor.newInstance();
        set(mapper, "classNameBiMap", ImmutableBiMap.of("fixture/Obfuscated", "fixture/Mapped"));
        Map<String, Map<String, String>> fields = new HashMap<String, Map<String, String>>();
        fields.put("fixture/Obfuscated", Collections.singletonMap("d:" + VECTOR, "field_178363_d"));
        set(mapper, "fieldNameMaps", fields);
        Map<String, Map<String, String>> methods = new HashMap<String, Map<String, String>>();
        methods.put("fixture/Obfuscated", Collections.singletonMap("a()" + VECTOR, "func_fixture"));
        set(mapper, "methodNameMaps", methods);
        return mapper;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = FMLDeobfuscatingRemapper.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static byte[] applyForge(FMLDeobfuscatingRemapper forge, byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new RemappingClassAdapter(writer, forge), ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
