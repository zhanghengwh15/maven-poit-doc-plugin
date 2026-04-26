package com.poit.doc.scanner.provider;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ClassMetaProvider backed by QDox source parsing.
 */
public final class QDoxClassMetaProvider implements ClassMetaProvider {

    private final JavaProjectBuilder builder;

    public QDoxClassMetaProvider(JavaProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    public Optional<ClassMeta> find(String fqn) {
        JavaClass javaClass = builder.getClassByName(fqn);
        if (javaClass == null) {
            return Optional.empty();
        }
        return Optional.of(toClassMeta(javaClass));
    }

    private ClassMeta toClassMeta(JavaClass javaClass) {
        String fqn = javaClass.getCanonicalName();
        String simpleName = javaClass.getName();
        boolean isEnum = javaClass.isEnum();
        boolean isInterface = javaClass.isInterface();

        List<FieldMeta> fields = new ArrayList<>();
        for (JavaField field : javaClass.getFields()) {
            if (field.isEnumConstant()) continue;
            fields.add(toFieldMeta(field));
        }

        List<AnnotationMeta> annotations = toAnnotations(javaClass.getAnnotations());

        List<TypeMeta> interfaces = new ArrayList<>();
        for (JavaType iface : javaClass.getImplements()) {
            interfaces.add(toTypeMeta(iface));
        }

        TypeMeta superclass = null;
        JavaType superType = javaClass.getSuperJavaClass();
        if (superType != null && !"java.lang.Object".equals(superType.getCanonicalName())) {
            superclass = toTypeMeta(superType);
        }

        String classComment = extractComment(javaClass.getComment());

        return new ClassMeta(fqn, simpleName, isEnum, isInterface,
                fields, annotations, interfaces, superclass, classComment, new HashMap<>());
    }

    private FieldMeta toFieldMeta(JavaField field) {
        String name = field.getName();
        TypeMeta type = toTypeMeta(field.getType());
        List<AnnotationMeta> annotations = toAnnotations(field.getAnnotations());
        String comment = extractComment(field.getComment());
        return new FieldMeta(name, type, annotations, comment);
    }

    private TypeMeta toTypeMeta(JavaType javaType) {
        if (javaType == null) {
            return new TypeMeta(null, "void", false, false, false, false, null, null);
        }

        String fqn = javaType.getCanonicalName();
        String simpleName = javaType.getValue();
        boolean isPrimitive = false;
        boolean isArray = false;
        List<TypeMeta> typeArgs = new ArrayList<>();

        if (javaType instanceof JavaClass jc) {
            isPrimitive = jc.isPrimitive();
            isArray = jc.isArray();
            if (jc.isEnum()) {
                List<String> enumConsts = new ArrayList<>();
                for (JavaField ef : jc.getEnumConstants()) {
                    enumConsts.add(ef.getName());
                }
                return new TypeMeta(fqn, simpleName, false, isArray, false, false, null,
                        enumConsts.isEmpty() ? null : enumConsts);
            }
        }

        // Check for parameterized type (generics)
        if (javaType instanceof JavaParameterizedType pt) {
            for (JavaType arg : pt.getActualTypeArguments()) {
                typeArgs.add(toTypeMeta(arg));
            }
        }

        boolean isCollection = isCollectionType(fqn);
        boolean isMap = isMapType(fqn);

        return new TypeMeta(fqn, simpleName, isPrimitive, isArray, isCollection, isMap,
                typeArgs, null);
    }

    private List<AnnotationMeta> toAnnotations(List<JavaAnnotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return Collections.emptyList();
        }
        List<AnnotationMeta> result = new ArrayList<>();
        for (JavaAnnotation ann : annotations) {
            String fqn = ann.getType().getCanonicalName();
            Map<String, Object> values = new LinkedHashMap<>();
            Map<String, Object> paramMap = ann.getNamedParameterMap();
            for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                values.put(entry.getKey(), resolveAnnotationValue(entry.getValue()));
            }
            result.add(new AnnotationMeta(fqn, values));
        }
        return result;
    }

    private Object resolveAnnotationValue(Object val) {
        if (val == null) return null;
        if (val instanceof String s) {
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }
        if (val instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(resolveAnnotationValue(item));
            }
            return result;
        }
        if (val instanceof JavaType jt) {
            return jt.getCanonicalName();
        }
        if (val instanceof JavaAnnotation nestedAnn) {
            String fqn = nestedAnn.getType().getCanonicalName();
            Map<String, Object> vals = new LinkedHashMap<>();
            for (var e : nestedAnn.getNamedParameterMap().entrySet()) {
                vals.put(e.getKey(), resolveAnnotationValue(e.getValue()));
            }
            return new AnnotationMeta(fqn, vals);
        }
        return val.toString();
    }

    private boolean isCollectionType(String fqn) {
        if (fqn == null) return false;
        return fqn.equals("java.util.List")
                || fqn.equals("java.util.Set")
                || fqn.equals("java.util.Collection")
                || fqn.equals("java.util.ArrayList")
                || fqn.equals("java.util.HashSet")
                || fqn.equals("java.util.LinkedList")
                || fqn.equals("java.util.TreeSet");
    }

    private boolean isMapType(String fqn) {
        if (fqn == null) return false;
        return fqn.equals("java.util.Map")
                || fqn.equals("java.util.HashMap")
                || fqn.equals("java.util.LinkedHashMap")
                || fqn.equals("java.util.TreeMap")
                || fqn.equals("java.util.concurrent.ConcurrentMap");
    }

    private String extractComment(String comment) {
        if (comment == null || comment.isEmpty()) {
            return "";
        }
        int dot = comment.indexOf('.');
        if (dot > 0) {
            return comment.substring(0, dot + 1).trim();
        }
        int newline = comment.indexOf('\n');
        if (newline > 0) {
            return comment.substring(0, newline).trim();
        }
        return comment.trim();
    }
}
