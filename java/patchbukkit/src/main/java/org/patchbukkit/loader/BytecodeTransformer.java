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
        final boolean isCraftBukkitReflection = currentClassName != null && currentClassName.contains("CraftBukkitReflection");

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

                // When headless Paper is booted, real CraftServer, CraftPlayer, CraftScheduler, CraftBlock,
                // CraftWorld, and CraftEntity are present and backed by NMS bridges. Only remap in fallback mode.
                if (!org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    // Keep CraftServer as is inside CraftBukkitReflection to prevent breaking its reflection logic
                    if (isCraftBukkitReflection && internalName.equals("org/bukkit/craftbukkit/CraftServer")) {
                        return internalName;
                    }

                    // Remap CraftServer -> PatchBukkitServer for seamless native casts and method calls
                    if (internalName.equals("org/bukkit/craftbukkit/CraftServer")) {
                        return "org/patchbukkit/PatchBukkitServer";
                    }

                    // Remap CraftPlayer -> PatchBukkitPlayer for seamless native casts and getHandle()
                    if (internalName.equals("org/bukkit/craftbukkit/entity/CraftPlayer")) {
                        return "org/patchbukkit/entity/PatchBukkitPlayer";
                    }

                    // Remap CraftScheduler -> BukkitScheduler
                    if (internalName.equals("org/bukkit/craftbukkit/scheduler/CraftScheduler")) {
                        return "org/bukkit/scheduler/BukkitScheduler";
                    }

                    // Remap CraftBlock -> PatchBukkitBlock for seamless native casts and method calls
                    if (internalName.equals("org/bukkit/craftbukkit/block/CraftBlock")) {
                        return "org/patchbukkit/world/PatchBukkitBlock";
                    }

                    // Remap CraftWorld -> org/patchbukkit/CraftWorld
                    if (internalName.equals("org/bukkit/craftbukkit/CraftWorld")) {
                        return "org/patchbukkit/CraftWorld";
                    }

                    // Remap CraftEntity -> org/patchbukkit/entity/CraftEntity
                    if (internalName.equals("org/bukkit/craftbukkit/entity/CraftEntity")) {
                        return "org/patchbukkit/entity/CraftEntity";
                    }
                }

                return super.map(internalName);
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
                                    }
                                    super.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
                                }
                            };
                        }
                        return mv;
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
