package com.poit.doc.scanner.provider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ClassMetaProvider backed by Java reflection.
 * Uses Class.forName(fqn, false, loader) to avoid static initialization.
 */
public final class ReflectionClassMetaProvider implements ClassMetaProvider {

    private final ClassLoader classLoader;

    public ReflectionClassMetaProvider(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Optional<ClassMeta> find(String fqn) {
        try {
            Class<?> cls = Class.forName(fqn, false, classLoader);
            return Optional.of(toClassMeta(cls));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    private ClassMeta toClassMeta(Class<?> cls) {
        String fqn = cls.getName();
        String simpleName = cls.getSimpleName();
        boolean isEnum = cls.isEnum();
        boolean isInterface = cls.isInterface();

        List<FieldMeta> fields = new ArrayList<>();
        // Collect fields from this class and superclasses (excluding Object)
        collectFields(cls, fields);

        List<AnnotationMeta> annotations = toAnnotations(cls.getAnnotations());

        List<TypeMeta> interfaces = new ArrayList<>();
        for (Type iface : cls.getGenericInterfaces()) {
            interfaces.add(toTypeMeta(iface));
        }

        TypeMeta superclass = null;
        Type superType = cls.getGenericSuperclass();
        if (superType != null && !Object.class.equals(cls.getSuperclass())) {
            superclass = toTypeMeta(superType);
        }

        List<String> enumConstants = null;
        if (isEnum) {
            Object[] constants = cls.getEnumConstants();
            if (constants != null) {
                enumConstants = new ArrayList<>();
                for (Object c : constants) {
                    enumConstants.add(((Enum<?>) c).name());
                }
            }
        }

        return new ClassMeta(fqn, simpleName, isEnum, isInterface,
                fields, annotations, interfaces, superclass, "", null);
    }

    private void collectFields(Class<?> cls, List<FieldMeta> fields) {
        if (cls == null || Object.class.equals(cls)) {
            return;
        }
        collectFields(cls.getSuperclass(), fields);
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !cls.isEnum()) continue;
            if (field.isSynthetic()) continue;
            fields.add(toFieldMeta(field));
        }
    }

    private FieldMeta toFieldMeta(Field field) {
        String name = field.getName();
        TypeMeta type = toTypeMeta(field.getGenericType());
        List<AnnotationMeta> annotations = toAnnotations(field.getAnnotations());
        return new FieldMeta(name, type, annotations, "");
    }

    private TypeMeta toTypeMeta(Type type) {
        if (type instanceof ParameterizedType pt) {
            String rawName = ((Class<?>) pt.getRawType()).getName();
            String simpleName = ((Class<?>) pt.getRawType()).getSimpleName();
            List<TypeMeta> typeArgs = new ArrayList<>();
            for (Type arg : pt.getActualTypeArguments()) {
                typeArgs.add(toTypeMeta(arg));
            }
            boolean isCollection = isCollectionType(rawName);
            boolean isMap = isMapType(rawName);
            return new TypeMeta(rawName, simpleName, false, false, isCollection, isMap, typeArgs, null);
        } else if (type instanceof Class<?> cls) {
            String fqn = cls.getName();
            String simpleName = cls.getSimpleName();
            boolean isPrimitive = cls.isPrimitive();
            boolean isArray = cls.isArray();
            boolean isCollection = isCollectionType(fqn);
            boolean isMap = isMapType(fqn);
            List<String> enumConstants = null;
            if (cls.isEnum()) {
                Object[] constants = cls.getEnumConstants();
                if (constants != null) {
                    enumConstants = new ArrayList<>();
                    for (Object c : constants) {
                        enumConstants.add(((Enum<?>) c).name());
                    }
                }
            }
            return new TypeMeta(fqn, simpleName, isPrimitive, isArray, isCollection, isMap, null, enumConstants);
        }
        return new TypeMeta(type.toString(), "Unknown", false, false, false, false, null, null);
    }

    private List<AnnotationMeta> toAnnotations(java.lang.annotation.Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return Collections.emptyList();
        }
        List<AnnotationMeta> result = new ArrayList<>();
        for (java.lang.annotation.Annotation a : annotations) {
            Map<String, Object> values = new HashMap<>();
            try {
                for (var method : a.annotationType().getDeclaredMethods()) {
                    values.put(method.getName(), method.invoke(a));
                }
            } catch (Exception ignored) {
                // Reflection invocation failure, skip
            }
            result.add(new AnnotationMeta(a.annotationType().getName(), values));
        }
        return result;
    }

    private boolean isCollectionType(String fqn) {
        if (fqn == null) return false;
        return fqn.startsWith("java.util.List")
                || fqn.startsWith("java.util.Set")
                || fqn.startsWith("java.util.Collection")
                || fqn.startsWith("java.util.ArrayList")
                || fqn.startsWith("java.util.HashSet");
    }

    private boolean isMapType(String fqn) {
        if (fqn == null) return false;
        return fqn.startsWith("java.util.Map")
                || fqn.startsWith("java.util.HashMap")
                || fqn.startsWith("java.util.LinkedHashMap")
                || fqn.startsWith("java.util.TreeMap");
    }
}
