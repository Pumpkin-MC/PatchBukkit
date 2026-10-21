package org.patchbukkit.protocgen;

import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.compiler.PluginProtos.*;

import java.io.IOException;
import java.util.*;

public class FfiGenerator {

    private static final Map<String, String> typeMap = new HashMap<>();

    public static void main(String[] args) throws IOException {
        CodeGeneratorRequest request = CodeGeneratorRequest.parseFrom(System.in);
        CodeGeneratorResponse.Builder response = CodeGeneratorResponse.newBuilder();
        response.setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE);

        // Build type map from all proto files
        for (FileDescriptorProto file : request.getProtoFileList()) {
            buildTypeMap(file);
        }

        // Generate FFI classes for services
        for (FileDescriptorProto file : request.getProtoFileList()) {
            if (!request.getFileToGenerateList().contains(file.getName())) {
                continue;
            }

            String javaPackage = getJavaPackage(file);

            for (ServiceDescriptorProto service : file.getServiceList()) {
                String className = service.getName() + "Ffi";
                String content = generateFfiClass(service, javaPackage);
                String fileName = javaPackage.replace('.', '/') + "/" + className + ".java";

                response.addFile(CodeGeneratorResponse.File.newBuilder()
                    .setName(fileName)
                    .setContent(content)
                    .build());
            }
        }

        response.build().writeTo(System.out);
    }

    private static void buildTypeMap(FileDescriptorProto file) {
        String protoPackage = file.getPackage();
        String javaPackage = getJavaPackage(file);
        boolean multipleFiles = file.getOptions().getJavaMultipleFiles();

        String javaPrefix = multipleFiles
            ? javaPackage + "."
            : javaPackage + "." + getJavaOuterClassName(file) + ".";

        for (DescriptorProto message : file.getMessageTypeList()) {
            mapType(protoPackage, message.getName(), javaPrefix);
            buildNestedTypeMap(message, "." + protoPackage + "." + message.getName(),
                              javaPrefix + message.getName());
        }

        for (EnumDescriptorProto enumType : file.getEnumTypeList()) {
            mapType(protoPackage, enumType.getName(), javaPrefix);
        }
    }

    private static void mapType(String protoPackage, String name, String javaPrefix) {
        typeMap.put("." + protoPackage + "." + name, javaPrefix + name);
    }

    private static void buildNestedTypeMap(DescriptorProto parent, String protoPrefix, String javaPrefix) {
        for (DescriptorProto nested : parent.getNestedTypeList()) {
            typeMap.put(protoPrefix + "." + nested.getName(), javaPrefix + "." + nested.getName());
            buildNestedTypeMap(nested, protoPrefix + "." + nested.getName(),
                              javaPrefix + "." + nested.getName());
        }

        for (EnumDescriptorProto enumType : parent.getEnumTypeList()) {
            typeMap.put(protoPrefix + "." + enumType.getName(), javaPrefix + "." + enumType.getName());
        }
    }

    private static String getJavaPackage(FileDescriptorProto file) {
        return file.getOptions().hasJavaPackage()
            ? file.getOptions().getJavaPackage()
            : file.getPackage();
    }

    private static String getJavaOuterClassName(FileDescriptorProto file) {
        if (file.getOptions().hasJavaOuterClassname()) {
            return file.getOptions().getJavaOuterClassname();
        }
        String name = file.getName();
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return toPascalCase(name.replace(".proto", ""));
    }

    private static String generateFfiClass(ServiceDescriptorProto service, String javaPackage) {
        StringBuilder sb = new StringBuilder();
        String className = service.getName() + "Ffi";
        List<MethodDescriptorProto> methods = service.getMethodList();

        // Package and imports
        sb.append("package ").append(javaPackage).append(";\n\n");
        sb.append("import java.lang.foreign.*;\n");
        sb.append("import java.lang.invoke.MethodHandle;\n");
        sb.append("import com.google.protobuf.InvalidProtocolBufferException;\n\n");

        // Class
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    private static final Linker LINKER = Linker.nativeLinker();\n");
        sb.append("    private static final int SCRATCH_CAPACITY = 65536;\n");
        sb.append("    private static final ThreadLocal<ScratchBuffer> SCRATCH = ThreadLocal.withInitial(ScratchBuffer::new);\n");
        sb.append("    private static MethodHandle freeNative;\n");

        for (MethodDescriptorProto method : methods) {
            sb.append("    private static MethodHandle ").append(toHandleName(method)).append(";\n");
        }
        sb.append("\n");
        sb.append("    private static final class ScratchBuffer {\n");
        sb.append("        final Arena arena = Arena.ofAuto();\n");
        sb.append("        final MemorySegment inputSegment = arena.allocate(SCRATCH_CAPACITY, 8);\n");
        sb.append("        final MemorySegment outputLenSegment = arena.allocate(ValueLayout.JAVA_LONG);\n");
        sb.append("    }\n\n");

        // Init methods
        generateInitMethod(sb, methods);
        generateInitFreeMethod(sb);

        // Service methods
        for (MethodDescriptorProto method : methods) {
            generateServiceMethod(sb, method);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static void generateInitMethod(StringBuilder sb, List<MethodDescriptorProto> methods) {
        sb.append("    public static void init(");

        StringJoiner params = new StringJoiner(", ");
        for (MethodDescriptorProto method : methods) {
            params.add("long " + toCamelCase(method.getName()) + "Addr");
        }
        sb.append(params).append(") {\n");
        sb.append("        try {\n");

        for (MethodDescriptorProto method : methods) {
            String handleName = toHandleName(method);
            String addrName = toCamelCase(method.getName()) + "Addr";

            sb.append("            ").append(handleName).append(" = LINKER.downcallHandle(\n");
            sb.append("                MemorySegment.ofAddress(").append(addrName).append("),\n");
            sb.append("                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ");
            sb.append("ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));\n");
        }
        sb.append("        } catch (Throwable t) {\n");
        sb.append("            System.err.println(\"[PatchBukkit FFI] Failed init: \" + t.getMessage());\n");
        sb.append("            t.printStackTrace();\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    private static void generateInitFreeMethod(StringBuilder sb) {
        sb.append("    public static void initFree(long freeAddr) {\n");
        sb.append("        try {\n");
        sb.append("            freeNative = LINKER.downcallHandle(\n");
        sb.append("                MemorySegment.ofAddress(freeAddr),\n");
        sb.append("                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));\n");
        sb.append("        } catch (Throwable t) {\n");
        sb.append("            System.err.println(\"[PatchBukkit FFI] Failed initFree: \" + t.getMessage());\n");
        sb.append("            t.printStackTrace();\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    private static void generateServiceMethod(StringBuilder sb, MethodDescriptorProto method) {
        String methodName = toCamelCase(method.getName());
        String handleName = toHandleName(method);
        String inputType = resolveJavaType(method.getInputType());
        String outputType = resolveJavaType(method.getOutputType());

        sb.append("    public static ").append(outputType).append(" ").append(methodName);
        sb.append("(").append(inputType).append(" request) {\n");
        sb.append("        MethodHandle handle = ").append(handleName).append(";\n");
        sb.append("        if (handle == null) return null;\n");
        sb.append("        try {\n");
        sb.append("            int serializedSize = request.getSerializedSize();\n");
        sb.append("            ScratchBuffer scratch = SCRATCH.get();\n");
        sb.append("            MemorySegment outputLenSegment = scratch.outputLenSegment;\n");
        sb.append("            MemorySegment resultPtr;\n\n");
        sb.append("            if (serializedSize == 0) {\n");
        sb.append("                resultPtr = (MemorySegment) handle.invokeExact(scratch.inputSegment, 0L, outputLenSegment);\n");
        sb.append("            } else if (serializedSize <= SCRATCH_CAPACITY) {\n");
        sb.append("                byte[] inputBytes = request.toByteArray();\n");
        sb.append("                MemorySegment.copy(inputBytes, 0, scratch.inputSegment, ValueLayout.JAVA_BYTE, 0, inputBytes.length);\n");
        sb.append("                resultPtr = (MemorySegment) handle.invokeExact(scratch.inputSegment, (long) inputBytes.length, outputLenSegment);\n");
        sb.append("            } else {\n");
        sb.append("                try (Arena arena = Arena.ofConfined()) {\n");
        sb.append("                    byte[] inputBytes = request.toByteArray();\n");
        sb.append("                    MemorySegment inputSegment = arena.allocate(inputBytes.length);\n");
        sb.append("                    inputSegment.copyFrom(MemorySegment.ofArray(inputBytes));\n");
        sb.append("                    resultPtr = (MemorySegment) handle.invokeExact(inputSegment, (long) inputBytes.length, outputLenSegment);\n");
        sb.append("                }\n");
        sb.append("            }\n\n");
        sb.append("            if (resultPtr.equals(MemorySegment.NULL)) return null;\n\n");
        sb.append("            long outputLen = outputLenSegment.get(ValueLayout.JAVA_LONG, 0);\n");
        sb.append("            if (outputLen == 0) {\n");
        sb.append("                return ").append(outputType).append(".getDefaultInstance();\n");
        sb.append("            }\n");
        sb.append("            byte[] outputBytes = resultPtr.reinterpret(outputLen).toArray(ValueLayout.JAVA_BYTE);\n");
        sb.append("            return ").append(outputType).append(".parseFrom(outputBytes);\n");
        sb.append("        } catch (InvalidProtocolBufferException e) {\n");
        sb.append("            throw new RuntimeException(\"Failed to parse response\", e);\n");
        sb.append("        } catch (Throwable t) {\n");
        sb.append("            throw new RuntimeException(\"FFI call failed\", t);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    private static String resolveJavaType(String protoType) {
        if (typeMap.containsKey(protoType)) {
            return typeMap.get(protoType);
        }
        if (protoType.startsWith(".google.protobuf.")) {
            return "com.google.protobuf." + protoType.substring(".google.protobuf.".length());
        }
        return protoType.startsWith(".") ? protoType.substring(1) : protoType;
    }

    private static String toHandleName(MethodDescriptorProto method) {
        return toCamelCase(method.getName()) + "Native";
    }

    private static String toCamelCase(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String toPascalCase(String name) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
