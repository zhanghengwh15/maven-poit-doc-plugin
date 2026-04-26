package com.poit.doc.scanner.resolver;

import com.poit.doc.scanner.model.ApiField;
import com.poit.doc.scanner.provider.*;

import java.util.*;

/**
 * Recursively resolves type information into ApiField trees.
 * Handles generics, collections, maps, circular references, inheritance, enums, wrapper class stripping.
 */
public final class TypeResolver {

    private final CompositeClassMetaProvider provider;
    private final Set<String> wrapperClasses;
    private final int maxDepth;

    public TypeResolver(CompositeClassMetaProvider provider) {
        this(provider, Set.of(
                "com.poit.common.Result",
                "com.poit.common.vo.Result",
                "org.springframework.http.ResponseEntity",
                "com.poit.common.ApiResponse"), 5);
    }

    public TypeResolver(CompositeClassMetaProvider provider, Set<String> wrapperClasses, int maxDepth) {
        this.provider = provider;
        this.wrapperClasses = wrapperClasses;
        this.maxDepth = maxDepth;
    }

    /**
     * Resolve a type into an ApiField, recursively expanding nested objects.
     */
    public ApiField resolve(String fqn) {
        return resolve(fqn, new LinkedHashSet<>(), 0);
    }

    private ApiField resolve(String fqn, Set<String> visited, int depth) {
        if (fqn == null || depth > maxDepth) {
            return placeholderField(fqn != null ? fqn : "unknown");
        }

        // Circular reference guard
        if (visited.contains(fqn)) {
            ApiField placeholder = new ApiField();
            placeholder.setName(fqn);
            placeholder.setType("object");
            placeholder.setRef(fqn);
            placeholder.setDescription("(circular reference)");
            return placeholder;
        }

        // Primitive/basic type
        if (isBasicType(fqn)) {
            return buildBasicField(fqn);
        }

        Optional<ClassMeta> metaOpt = provider.find(fqn);
        if (metaOpt.isEmpty()) {
            ApiField fallback = new ApiField();
            fallback.setName(fqn);
            fallback.setType("object");
            fallback.setDescription("(class not found in source/classpath)");
            return fallback;
        }

        ClassMeta meta = metaOpt.get();

        // Enum
        if (meta.isEnum()) {
            return buildEnumField(meta);
        }

        visited.add(fqn);
        try {
            List<ApiField> children = new ArrayList<>();
            for (FieldMeta field : meta.getFields()) {
                if (isHidden(field)) continue;
                children.add(resolveField(field, visited, depth + 1));
            }

            ApiField result = new ApiField();
            result.setName(meta.getSimpleName());
            result.setType("object");
            result.setRef(fqn);
            result.setDescription(meta.getClassComment());
            result.setChildren(children);
            return result;
        } finally {
            visited.remove(fqn);
        }
    }

    /**
     * Resolve a type with a generic context (e.g., Result<UserVO>).
     */
    public ApiField resolveWithGeneric(String fqn, List<String> typeArguments, Set<String> visited, int depth) {
        if (isBasicType(fqn)) {
            return buildBasicField(fqn);
        }

        Optional<ClassMeta> metaOpt = provider.find(fqn);
        if (metaOpt.isEmpty()) {
            ApiField fallback = new ApiField();
            fallback.setName(fqn);
            fallback.setType("object");
            fallback.setDescription("(class not found)");
            return fallback;
        }

        ClassMeta meta = metaOpt.get();

        // Check if this is a wrapper class — strip it
        if (wrapperClasses.contains(fqn) && typeArguments != null && !typeArguments.isEmpty()) {
            return resolveWithGeneric(typeArguments.get(0), Collections.emptyList(), visited, depth);
        }

        if (meta.isEnum()) {
            return buildEnumField(meta);
        }

        if (visited.contains(fqn)) {
            ApiField placeholder = new ApiField();
            placeholder.setName(fqn);
            placeholder.setType("object");
            placeholder.setRef(fqn);
            placeholder.setDescription("(circular reference)");
            return placeholder;
        }

        visited.add(fqn);
        try {
            List<ApiField> children = new ArrayList<>();
            for (FieldMeta field : meta.getFields()) {
                if (isHidden(field)) continue;
                children.add(resolveFieldWithContext(field, typeArguments, visited, depth + 1));
            }

            ApiField result = new ApiField();
            result.setName(meta.getSimpleName());
            result.setType("object");
            result.setRef(fqn);
            result.setDescription(meta.getClassComment());
            result.setChildren(children);
            return result;
        } finally {
            visited.remove(fqn);
        }
    }

    private ApiField resolveField(FieldMeta field, Set<String> visited, int depth) {
        TypeMeta type = field.getType();
        if (type == null) {
            return simpleField(field.getName(), "unknown", "");
        }

        String fqn = type.getFqn();
        if (fqn == null || isBasicType(fqn)) {
            return buildBasicField(fqn != null ? fqn : type.getSimpleName());
        }

        // Collection
        if (type.isCollection() || type.isArray()) {
            ApiField collField = new ApiField();
            collField.setName(field.getName());
            collField.setType("array");
            collField.setDescription(field.getComment());
            collField.setCollection(true);

            if (!type.getTypeArguments().isEmpty()) {
                TypeMeta elemType = type.getTypeArguments().get(0);
                collField.setRef(elemType.getFqn());
                if (!isBasicType(elemType.getFqn())) {
                    ApiField child = resolve(elemType.getFqn(), visited, depth + 1);
                    collField.setChildren(List.of(child));
                }
            }
            return collField;
        }

        // Map
        if (type.isMap()) {
            ApiField mapField = new ApiField();
            mapField.setName(field.getName());
            mapField.setType("object");
            mapField.setDescription(field.getComment());
            if (!type.getTypeArguments().isEmpty() && type.getTypeArguments().size() >= 2) {
                TypeMeta valType = type.getTypeArguments().get(1);
                mapField.setRef(valType.getFqn());
                if (!isBasicType(valType.getFqn())) {
                    ApiField child = resolve(valType.getFqn(), visited, depth + 1);
                    mapField.setChildren(List.of(child));
                }
            }
            return mapField;
        }

        // Regular object
        return resolveFieldForObject(field, type, visited, depth);
    }

    private ApiField resolveFieldWithContext(FieldMeta field, List<String> typeArguments, Set<String> visited, int depth) {
        TypeMeta type = field.getType();
        if (type == null) {
            return simpleField(field.getName(), "unknown", "");
        }

        String fqn = type.getFqn();
        if (fqn == null || isBasicType(fqn)) {
            return buildBasicField(fqn != null ? fqn : type.getSimpleName());
        }

        // Use type arguments from context if the field type matches a generic parameter
        if (type.isCollection() || type.isArray()) {
            ApiField collField = new ApiField();
            collField.setName(field.getName());
            collField.setType("array");
            collField.setDescription(field.getComment());
            collField.setCollection(true);

            if (!type.getTypeArguments().isEmpty()) {
                TypeMeta elemType = type.getTypeArguments().get(0);
                collField.setRef(elemType.getFqn());
                if (!isBasicType(elemType.getFqn())) {
                    ApiField child = resolve(elemType.getFqn(), visited, depth + 1);
                    collField.setChildren(List.of(child));
                }
            } else if (typeArguments != null && !typeArguments.isEmpty()) {
                collField.setRef(typeArguments.get(0));
                if (!isBasicType(typeArguments.get(0))) {
                    ApiField child = resolveWithGeneric(typeArguments.get(0), Collections.emptyList(), visited, depth + 1);
                    collField.setChildren(List.of(child));
                }
            }
            return collField;
        }

        return resolveFieldForObject(field, type, visited, depth);
    }

    private ApiField resolveFieldForObject(FieldMeta field, TypeMeta type, Set<String> visited, int depth) {
        ApiField result = new ApiField();
        result.setName(field.getName());
        result.setType("object");
        result.setRef(type.getFqn());
        result.setDescription(field.getComment());

        if (!isBasicType(type.getFqn()) && !visited.contains(type.getFqn())) {
            ApiField child = resolve(type.getFqn(), visited, depth + 1);
            result.setChildren(child.getChildren());
            result.setDescription(child.getDescription() != null && !child.getDescription().isEmpty()
                    ? child.getDescription()
                    : field.getComment());
        }

        return result;
    }

    private boolean isBasicType(String fqn) {
        if (fqn == null) return false;
        return fqn.equals("java.lang.String")
                || fqn.equals("java.lang.Integer")
                || fqn.equals("int")
                || fqn.equals("java.lang.Long")
                || fqn.equals("long")
                || fqn.equals("java.lang.Double")
                || fqn.equals("double")
                || fqn.equals("java.lang.Float")
                || fqn.equals("float")
                || fqn.equals("java.lang.Boolean")
                || fqn.equals("boolean")
                || fqn.equals("java.lang.Byte")
                || fqn.equals("byte")
                || fqn.equals("java.lang.Short")
                || fqn.equals("short")
                || fqn.equals("java.lang.Character")
                || fqn.equals("char")
                || fqn.equals("java.math.BigDecimal")
                || fqn.equals("java.math.BigInteger")
                || fqn.equals("java.util.Date")
                || fqn.equals("java.time.LocalDate")
                || fqn.equals("java.time.LocalDateTime")
                || fqn.equals("java.time.LocalTime")
                || fqn.equals("java.time.ZonedDateTime")
                || fqn.equals("java.util.UUID")
                || fqn.equals("void")
                || fqn.equals("java.lang.Object");
    }

    private ApiField buildBasicField(String fqn) {
        ApiField field = new ApiField();
        field.setName(fqn);
        field.setType(mapBasicType(fqn));
        return field;
    }

    private ApiField buildEnumField(ClassMeta meta) {
        ApiField field = new ApiField();
        field.setName(meta.getSimpleName());
        field.setType("string");
        field.setEnum(true);
        field.setEnumValues(meta.getFields().stream()
                .map(FieldMeta::getName)
                .toList());
        return field;
    }

    private ApiField simpleField(String name, String type, String desc) {
        ApiField field = new ApiField();
        field.setName(name);
        field.setType(type);
        field.setDescription(desc);
        return field;
    }

    private ApiField placeholderField(String name) {
        ApiField field = new ApiField();
        field.setName(name);
        field.setType("object");
        field.setDescription("(max depth exceeded)");
        return field;
    }

    private String mapBasicType(String fqn) {
        return switch (fqn) {
            case "java.lang.String" -> "string";
            case "java.lang.Integer", "int" -> "integer";
            case "java.lang.Long", "long" -> "long";
            case "java.lang.Double", "double" -> "double";
            case "java.lang.Float", "float" -> "float";
            case "java.lang.Boolean", "boolean" -> "boolean";
            case "java.lang.Byte", "byte" -> "byte";
            case "java.lang.Short", "short" -> "short";
            case "java.lang.Character", "char" -> "char";
            case "java.math.BigDecimal" -> "bigdecimal";
            case "java.math.BigInteger" -> "biginteger";
            case "java.util.Date" -> "date";
            case "java.time.LocalDate" -> "localdate";
            case "java.time.LocalDateTime" -> "localdatetime";
            case "java.time.LocalTime" -> "localtime";
            case "java.util.UUID" -> "uuid";
            default -> fqn.contains(".") ? "object" : fqn;
        };
    }

    private boolean isHidden(FieldMeta field) {
        AnnotationMeta apiProp = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
        if (apiProp != null) {
            Object hidden = apiProp.getValue("hidden");
            if (hidden instanceof Boolean b && b) return true;
            if ("true".equals(String.valueOf(hidden))) return true;
        }
        apiProp = field.getAnnotation("io.swagger.v3.oas.annotations.media.Schema");
        if (apiProp != null) {
            Object hidden = apiProp.getValue("hidden");
            if (hidden instanceof Boolean b && b) return true;
            if ("true".equals(String.valueOf(hidden))) return true;
        }
        return false;
    }
}
