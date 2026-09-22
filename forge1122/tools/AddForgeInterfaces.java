import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/**
 * Makes net.minecraft.block.Block and net.minecraft.item.Item extend
 * Forge's IForgeRegistryEntry$Impl<X> in a COPY of the MCP-named compile
 * jars. This replicates what Forge's runtime binary patches do to vanilla
 * classes (see the 1.12.x Block.java.patch / Item.java.patch:
 * "public class Block extends IForgeRegistryEntry.Impl<Block>"); the output
 * jars are for COMPILATION ONLY and are never shipped (the real game
 * provides the real patches).
 *
 * Why the superclass matters instead of just "implements" plus stub
 * methods: the erased descriptor of Impl.setRegistryName is
 * (LResourceLocation;)LIForgeRegistryEntry;. Compiling against that shape
 * makes javac emit method refs that resolve at runtime. A hand-added stub
 * with a covariant (LResourceLocation;)LBlock; descriptor emits refs that
 * do not exist at runtime (NoSuchMethodError on mod load) -- and
 * javac-generated bridge methods in Block subclasses inherit the same
 * wrong descriptor, so fixing call sites alone is not enough. A stub
 * returning the interface type instead fails javac's own "cannot
 * implement" check. Only the real hierarchy compiles AND runs.
 */
public class AddForgeInterfaces {
    static final String IFACE = "net/minecraftforge/registries/IForgeRegistryEntry";
    static final String IMPL = "net/minecraftforge/registries/IForgeRegistryEntry$Impl";

    public static void main(String[] args) throws Exception {
        String inJar = args[0];
        String outJar = args[1];
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put("net/minecraft/block/Block", "Lnet/minecraft/block/Block;");
        targets.put("net/minecraft/item/Item", "Lnet/minecraft/item/Item;");

        try (JarFile jf = new JarFile(inJar);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(outJar))) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                byte[] data = readAll(jf.getInputStream(e));
                String name = e.getName();
                String cls = name.endsWith(".class")
                    ? name.substring(0, name.length() - 6) : null;
                if (cls != null && targets.containsKey(cls)) {
                    data = patch(data, cls, targets.get(cls));
                    System.out.println("patched " + cls);
                }
                JarEntry out = new JarEntry(name);
                out.setTime(e.getTime());
                jos.putNextEntry(out);
                jos.write(data);
                jos.closeEntry();
            }
        }
        System.out.println("wrote " + outJar);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    static byte[] patch(byte[] classBytes, String className, String typeDesc) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM6, cw) {
            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                List<String> ifaces = new ArrayList<>(Arrays.asList(interfaces));
                if (!ifaces.contains(IFACE)) {
                    ifaces.add(IFACE);
                }
                // New superclass: IForgeRegistryEntry$Impl<X>, exactly like
                // Forge's runtime patches. Generic signature mirrors it.
                String sig = "L" + IMPL + "<" + typeDesc + ">;";
                super.visit(version, access, name, sig, IMPL,
                    ifaces.toArray(new String[0]));
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if ("<init>".equals(name)) {
                    // Constructors previously called Object.<init> as their
                    // super constructor; retarget to Impl.<init> to match the
                    // new superclass, otherwise verification fails.
                    return new MethodVisitor(Opcodes.ASM6, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mname,
                                String mdesc, boolean itf) {
                            if (opcode == Opcodes.INVOKESPECIAL
                                    && "java/lang/Object".equals(owner)
                                    && "<init>".equals(mname)) {
                                owner = IMPL;
                            }
                            super.visitMethodInsn(opcode, owner, mname, mdesc, itf);
                        }
                    };
                }
                return mv;
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
