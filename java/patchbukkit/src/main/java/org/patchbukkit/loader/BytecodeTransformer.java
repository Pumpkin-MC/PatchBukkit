package org.patchbukkit.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class BytecodeTransformer {

    private static final Logger LOGGER = Logger.getLogger("BytecodeTransformer");

    private static Remapper createRemapper(String currentClassName) {
        return new Remapper() {
            @Override
            public String map(String internalName) {
                if (internalName == null) return null;

                // Missing dot after craftbukkit (e.g. Cloud command framework: org/bukkit/craftbukkitcommand -> org/bukkit/craftbukkit/command)
                if (internalName.startsWith("org/bukkit/craftbukkit") && !internalName.startsWith("org/bukkit/craftbukkit/")) {
                    return "org/bukkit/craftbukkit/" + internalName.substring("org/bukkit/craftbukkit".length());
                }

                // Remap legacy versioned CraftBukkit: org/bukkit/craftbukkit/v1_20_R3/... -> org/bukkit/craftbukkit/...
                if (internalName.startsWith("org/bukkit/craftbukkit/v")) {
                    String unversioned = internalName.replaceFirst("^org/bukkit/craftbukkit/v[0-9_]+R[0-9]+/", "org/bukkit/craftbukkit/");
                    return map(unversioned);
                }

                return super.map(internalName);
            }

            @Override
            public Object mapValue(Object value) {
                if ("asCraftMirror".equals(value)) {
                    // Paper 26.3 changed CraftItemStack.asBukkitCopy(ItemStack) -> asBukkitCopy(ItemInstance)
                    // and CraftItemStack.asCraftMirror(ItemStack) -> asBukkitMirror(ItemStack).
                    return "asBukkitMirror";
                }
                return super.mapValue(value);
            }
        };
    }

    private BytecodeTransformer() {}

    public static byte[] transform(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            String className = reader.getClassName();
            ClassWriter writer = new ClassWriter(reader, 0);
            Remapper remapper = createRemapper(className);
            ClassVisitor cv = new ClassRemapper(writer, remapper);

            if (className != null && className.contains("CraftBukkitReflection")) {
                cv = new ClassVisitor(Opcodes.ASM9, cv) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if ("<clinit>".equals(name)) {
                            return new MethodVisitor(Opcodes.ASM9, mv) {
                                @Override
                                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                                    if (opcode == Opcodes.PUTSTATIC && "CB_PKG_VERSION".equals(fieldName)) {
                                        // Pop whatever was calculated and push "."
                                        super.visitInsn(Opcodes.POP);
                                        super.visitLdcInsn(".");
                                    } else if (opcode == Opcodes.PUTSTATIC && "MAJOR_REVISION".equals(fieldName)) {
                                        // Cloud's parseMajorRevision("26.3") splits on "." and takes index 1 (= 3),
                                        // mistaking MC 26.3 for MC 1.3. Force MAJOR_REVISION = 26.
                                        super.visitInsn(Opcodes.POP);
                                        super.visitIntInsn(Opcodes.BIPUSH, 26);
                                    }
                                    super.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
                                }
                            };
                        }
                        return mv;
                    }
                };
            }

            if (className != null && className.endsWith("ServerManagerImpl")) {
                cv = new ClassVisitor(Opcodes.ASM9, cv) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if ("resolveVersionNoCache".equals(name)) {
                            return new MethodVisitor(Opcodes.ASM9, mv) {
                                @Override
                                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                                    if (opcode == Opcodes.GETSTATIC && "V_1_8_8".equals(fieldName) && owner.endsWith("/ServerVersion")) {
                                        // When a plugin shades an older PacketEvents that doesn't yet have V_26_3,
                                        // fall back to ServerVersion.getLatest() instead of V_1_8_8.
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "getLatest", "()L" + owner + ";", false);
                                        return;
                                    }
                                    super.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
                                }
                            };
                        }
                        return mv;
                    }
                };
            }

            if (className != null && className.contains("SpigotPacketEventsBuilder")) {
                cv = new ClassVisitor(Opcodes.ASM9, cv) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean isInterface) {
                                if (opcode == Opcodes.INVOKESPECIAL && "checkCompatibility".equals(methodName) && "()V".equals(methodDesc)) {
                                    // Equivalent to PE_IGNORE_INCOMPATIBILITY=true so plugins shading older
                                    // PacketEvents point releases do not self-disable on newer MC point releases.
                                    super.visitInsn(Opcodes.POP);
                                    return;
                                }
                                if (opcode == Opcodes.INVOKEVIRTUAL && "isNewerThan".equals(methodName) && owner.endsWith("/PEVersion")) {
                                    super.visitInsn(Opcodes.POP2);
                                    super.visitInsn(Opcodes.ICONST_0);
                                    return;
                                }
                                super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                            }
                        };
                    }
                };
            }

            reader.accept(cv, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[BytecodeTransformer] Failed to transform class bytecode, returning original", t);
            return classBytes;
        }
    }
}
