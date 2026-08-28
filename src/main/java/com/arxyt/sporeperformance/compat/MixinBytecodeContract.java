package com.arxyt.sporeperformance.compat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Startup-only contract check for direct Mixin transformations.  A class
 * existing and containing a method with the right name is not enough: a
 * Redirect can still target an invocation that another mod removed.  Before
 * Mixin applies our class, prove that every overwrite target and every
 * instruction-targeted injection exists in the installed bytecode.
 */
public final class MixinBytecodeContract {
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String INJECTION_PREFIX = "Lorg/spongepowered/asm/mixin/injection/";

    public static Result verify(String targetBinaryName, String mixinBinaryName) {
        ClassNode target = read(targetBinaryName);
        ClassNode mixin = read(mixinBinaryName);
        if (target == null || mixin == null) return Result.failure("class bytes unavailable");

        for (MethodNode mixinMethod : mixin.methods) {
            if (hasAnnotation(mixinMethod, OVERWRITE) && !hasMethod(target, mixinMethod.name, mixinMethod.desc)) {
                return Result.failure("overwrite target missing: " + mixinMethod.name + mixinMethod.desc);
            }
            for (AnnotationNode injection : injectionAnnotations(mixinMethod)) {
                // Mixin itself treats require=0 as optional.  Such hooks are
                // deliberately used for members injected by AI Fix, which do
                // not exist in the untransformed class bytes available here.
                if (optional(injection)) continue;
                List<String> selectedMethods = strings(value(injection, "method"));
                if (!selectedMethods.isEmpty() && !hasAnySelectedMethod(target, selectedMethods)) {
                    return Result.failure("injection method missing: " + selectedMethods);
                }
                for (AnnotationNode at : annotations(value(injection, "at"))) {
                    String member = string(value(at, "target"));
                    if (!member.isEmpty() && !hasTargetInstruction(target, selectedMethods, member)) {
                        return Result.failure("injection point missing: " + member + " in " + selectedMethods);
                    }
                }
            }
        }
        return Result.success();
    }

    private static ClassNode read(String binaryName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) return null;
        try (InputStream input = loader.getResourceAsStream(binaryName.replace('.', '/') + ".class")) {
            if (input == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean hasMethod(ClassNode target, String name, String descriptor) {
        return target.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }

    private static boolean hasAnySelectedMethod(ClassNode target, List<String> selectors) {
        return target.methods.stream().anyMatch(method -> selectors.stream().anyMatch(selector -> matches(method, selector)));
    }

    private static boolean hasTargetInstruction(ClassNode target, List<String> selectors, String encodedMember) {
        Member member = Member.parse(encodedMember);
        if (member == null) return false;
        for (MethodNode method : target.methods) {
            if (!selectors.isEmpty() && selectors.stream().noneMatch(selector -> matches(method, selector))) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode invoke
                        && member.method
                        && invoke.owner.equals(member.owner)
                        && invoke.name.equals(member.name)
                        && invoke.desc.equals(member.descriptor)) return true;
                if (instruction instanceof FieldInsnNode field
                        && !member.method
                        && field.owner.equals(member.owner)
                        && field.name.equals(member.name)
                        && field.desc.equals(member.descriptor)) return true;
            }
        }
        return false;
    }

    private static boolean matches(MethodNode method, String selector) {
        int descriptorStart = selector.indexOf('(');
        if (descriptorStart < 0) return method.name.equals(selector);
        return method.name.equals(selector.substring(0, descriptorStart)) && method.desc.equals(selector.substring(descriptorStart));
    }

    private static boolean hasAnnotation(MethodNode method, String descriptor) {
        return annotations(method.visibleAnnotations).stream().anyMatch(annotation -> annotation.desc.equals(descriptor))
                || annotations(method.invisibleAnnotations).stream().anyMatch(annotation -> annotation.desc.equals(descriptor));
    }

    private static List<AnnotationNode> injectionAnnotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        for (AnnotationNode annotation : annotations(method.visibleAnnotations)) {
            if (annotation.desc.startsWith(INJECTION_PREFIX)) result.add(annotation);
        }
        for (AnnotationNode annotation : annotations(method.invisibleAnnotations)) {
            if (annotation.desc.startsWith(INJECTION_PREFIX)) result.add(annotation);
        }
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
        }
        return null;
    }

    private static boolean optional(AnnotationNode annotation) {
        Object require = value(annotation, "require");
        return require instanceof Integer count && count == 0;
    }

    private static List<String> strings(Object value) {
        if (value instanceof String string) return List.of(string);
        if (!(value instanceof List<?> values)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object entry : values) if (entry instanceof String string) result.add(string);
        return result;
    }

    private static String string(Object value) { return value instanceof String string ? string : ""; }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode> annotations(Object value) {
        if (value instanceof AnnotationNode annotation) return List.of(annotation);
        if (!(value instanceof List<?> values)) return Collections.emptyList();
        List<AnnotationNode> result = new ArrayList<>();
        for (Object entry : values) if (entry instanceof AnnotationNode annotation) result.add(annotation);
        return result;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> annotations) {
        return annotations == null ? Collections.emptyList() : annotations;
    }

    private record Member(String owner, String name, String descriptor, boolean method) {
        private static Member parse(String encoded) {
            if (!encoded.startsWith("L")) return null;
            int ownerEnd = encoded.indexOf(';');
            if (ownerEnd <= 1 || ownerEnd + 1 >= encoded.length()) return null;
            String owner = encoded.substring(1, ownerEnd);
            String rest = encoded.substring(ownerEnd + 1);
            int methodDescriptor = rest.indexOf('(');
            if (methodDescriptor >= 1) {
                String name = rest.substring(0, methodDescriptor);
                String descriptor = rest.substring(methodDescriptor);
                try {
                    Type.getMethodType(descriptor);
                    return new Member(owner, name, descriptor, true);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            int fieldDescriptor = rest.indexOf(':');
            if (fieldDescriptor >= 1) {
                String name = rest.substring(0, fieldDescriptor);
                String descriptor = rest.substring(fieldDescriptor + 1);
                try {
                    Type.getType(descriptor);
                    return new Member(owner, name, descriptor, false);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    public record Result(boolean compatible, String detail) {
        private static Result success() { return new Result(true, "ok"); }
        private static Result failure(String detail) { return new Result(false, detail); }
    }

    private MixinBytecodeContract() {}
}
