package com.poit.doc.scanner.provider;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ClassMetaProvider backed by ASM bytecode analysis.
 * Reads .class files from classpath, extracts fields, annotations, signatures.
 */
public final class AsmClassMetaProvider implements ClassMetaProvider {

    private final ClassLoader classLoader;

    public AsmClassMetaProvider(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Optional<ClassMeta> find(String fqn) {
        String resourcePath = fqn.replace('.', '/') + ".class";
        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return Optional.empty();
            }
            ClassReader cr = new ClassReader(is);
            ClassNode cn = new ClassNode(Opcodes.ASM9);
            cr.accept(cn, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

            return Optional.of(toClassMeta(cn, fqn));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private ClassMeta toClassMeta(ClassNode cn, String fqn) {
        String simpleName = cn.name.contains("/")
                ? cn.name.substring(cn.name.lastIndexOf('/') + 1)
                : cn.name;
        boolean isEnum = (cn.access & Opcodes.ACC_ENUM) != 0;
        boolean isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0;

        List<FieldMeta> fields = new ArrayList<>();
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                // Skip synthetic/enum constant fields
                if ((fn.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
                if (isEnum && (fn.access & Opcodes.ACC_ENUM) != 0) continue;
                // Skip static fields unless they're meaningful
                if ((fn.access & Opcodes.ACC_STATIC) != 0 && !isEnum) continue;
                fields.add(toFieldMeta(fn));
            }
        }

        List<AnnotationMeta> annotations = toAnnotations(cn.visibleAnnotations, cn.invisibleAnnotations);

        List<TypeMeta> interfaces = new ArrayList<>();
        if (cn.interfaces != null) {
            for (String iface : cn.interfaces) {
                interfaces.add(new TypeMeta(
                        iface.replace('/', '.'),
                        iface.substring(iface.lastIndexOf('/') + 1),
                        false, false, false, false, null, null));
            }
        }

        TypeMeta superclass = null;
        if (cn.superName != null && !"java/lang/Object".equals(cn.superName)) {
            superclass = new TypeMeta(
                    cn.superName.replace('/', '.'),
                    cn.superName.substring(cn.superName.lastIndexOf('/') + 1),
                    false, false, false, false, null, null);
        }

        // Parse generic signature if available
        List<String> enumConstants = isEnum ? resolveEnumConstants(cn) : null;

        // Parse generic type arguments from signature
        if (cn.signature != null) {
            parseClassSignature(cn.signature, interfaces);
        }

        return new ClassMeta(
                fqn, simpleName, isEnum, isInterface,
                fields, annotations, interfaces, superclass,
                "", null); // No JavaDoc from bytecode
    }

    private FieldMeta toFieldMeta(FieldNode fn) {
        TypeMeta type = parseFieldType(fn);
        List<AnnotationMeta> annotations = toAnnotations(fn.visibleAnnotations, fn.invisibleAnnotations);
        return new FieldMeta(fn.name, type, annotations, "");
    }

    private TypeMeta parseFieldType(FieldNode fn) {
        if (fn.signature != null) {
            return parseFieldSignature(fn.signature);
        }
        return descToType(fn.desc);
    }

    TypeMeta parseFieldSignature(String signature) {
        SignatureInfo info = new SignatureInfo();
        new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
            @Override
            public void visitClassType(String name) {
                info.fqn = name.replace('/', '.');
                info.simpleName = name.substring(name.lastIndexOf('/') + 1);
            }

            @Override
            public SignatureVisitor visitTypeArgument(char wildcard) {
                return new SignatureVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitClassType(String name) {
                        info.typeArgs.add(new TypeMeta(
                                name.replace('/', '.'),
                                name.substring(name.lastIndexOf('/') + 1),
                                false, false,
                                isCollectionType(name.replace('/', '.')),
                                isMapType(name.replace('/', '.')),
                                null, null));
                    }
                };
            }

            @Override
            public SignatureVisitor visitArrayType() {
                info.isArray = true;
                return this;
            }
        });
        boolean isCollection = isCollectionType(info.fqn);
        boolean isMap = isMapType(info.fqn);
        return new TypeMeta(info.fqn, info.simpleName, false, info.isArray,
                isCollection, isMap, info.typeArgs, null);
    }

    TypeMeta descToType(String desc) {
        switch (desc) {
            case "Z": return primitive("boolean", "boolean");
            case "B": return primitive("byte", "byte");
            case "C": return primitive("char", "char");
            case "S": return primitive("short", "short");
            case "I": return primitive("int", "int");
            case "J": return primitive("long", "long");
            case "F": return primitive("float", "float");
            case "D": return primitive("double", "double");
            case "V": return primitive("void", "void");
            case "Ljava/lang/String;":
                return new TypeMeta("java.lang.String", "String", false, false, false, false, null, null);
        }
        if (desc.startsWith("[")) {
            int dims = 0;
            while (desc.charAt(dims) == '[') dims++;
            String baseDesc = desc.substring(dims);
            TypeMeta base = descToType(baseDesc);
            return new TypeMeta(base.getFqn(), base.getSimpleName(), base.isPrimitive(),
                    true, base.isCollection(), base.isMap(), base.getTypeArguments(), null);
        }
        if (desc.startsWith("L") && desc.endsWith(";")) {
            String internal = desc.substring(1, desc.length() - 1);
            String fqn = internal.replace('/', '.');
            String simpleName = internal.substring(internal.lastIndexOf('/') + 1);
            // Strip inner class marker
            if (simpleName.contains("$")) {
                simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1);
            }
            boolean isCollection = isCollectionType(fqn);
            boolean isMap = isMapType(fqn);
            return new TypeMeta(fqn, simpleName, false, false, isCollection, isMap, null, null);
        }
        return new TypeMeta(desc, desc, false, false, false, false, null, null);
    }

    private TypeMeta primitive(String fqn, String simpleName) {
        return new TypeMeta(fqn, simpleName, true, false, false, false, null, null);
    }

    private List<AnnotationMeta> toAnnotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationMeta> result = new ArrayList<>();
        if (visible != null) {
            for (AnnotationNode an : visible) {
                result.add(toAnnotationMeta(an));
            }
        }
        if (invisible != null) {
            for (AnnotationNode an : invisible) {
                result.add(toAnnotationMeta(an));
            }
        }
        return result;
    }

    private AnnotationMeta toAnnotationMeta(AnnotationNode an) {
        String fqn = an.desc.substring(1, an.desc.length() - 1).replace('/', '.');
        Map<String, Object> values = new HashMap<>();
        if (an.values != null) {
            for (int i = 0; i < an.values.size() - 1; i += 2) {
                String key = (String) an.values.get(i);
                Object val = an.values.get(i + 1);
                values.put(key, resolveAnnotationValue(val));
            }
        }
        return new AnnotationMeta(fqn, values);
    }

    private Object resolveAnnotationValue(Object val) {
        if (val == null) return null;
        if (val instanceof String[]) {
            String[] pair = (String[]) val;
            // ASM annotation value format: ["type_descriptor", value] or just value
            if (pair.length >= 2) {
                return pair[1];
            }
            return pair[0];
        }
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(resolveAnnotationValue(item));
            }
            return result;
        }
        if (val instanceof org.objectweb.asm.Type) {
            return ((org.objectweb.asm.Type) val).getClassName();
        }
        if (val instanceof AnnotationNode) {
            return toAnnotationMeta((AnnotationNode) val);
        }
        return val;
    }

    private void parseClassSignature(String signature, List<TypeMeta> interfaces) {
        // Parse generic superclass/interfaces from class signature
        new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
        });
    }

    private List<String> resolveEnumConstants(ClassNode cn) {
        List<String> constants = new ArrayList<>();
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                if ((fn.access & Opcodes.ACC_ENUM) != 0 && (fn.access & Opcodes.ACC_STATIC) != 0
                        && (fn.access & Opcodes.ACC_FINAL) != 0) {
                    constants.add(fn.name);
                }
            }
        }
        return constants.isEmpty() ? null : constants;
    }

    boolean isCollectionType(String fqn) {
        if (fqn == null) return false;
        return fqn.equals("java.util.List")
                || fqn.equals("java.util.Set")
                || fqn.equals("java.util.Collection")
                || fqn.equals("java.util.ArrayList")
                || fqn.equals("java.util.HashSet")
                || fqn.equals("java.util.LinkedList")
                || fqn.equals("java.util.TreeSet");
    }

    boolean isMapType(String fqn) {
        if (fqn == null) return false;
        return fqn.equals("java.util.Map")
                || fqn.equals("java.util.HashMap")
                || fqn.equals("java.util.LinkedHashMap")
                || fqn.equals("java.util.TreeMap")
                || fqn.equals("java.util.concurrent.ConcurrentMap");
    }

    static class SignatureInfo {
        String fqn;
        String simpleName;
        boolean isArray;
        List<TypeMeta> typeArgs = new ArrayList<>();
    }
}
