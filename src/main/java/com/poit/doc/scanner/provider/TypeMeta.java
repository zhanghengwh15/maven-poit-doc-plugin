package com.poit.doc.scanner.provider;

import java.util.List;

/**
 * Represents a Java type — primitive, class reference, generic, array, or collection.
 */
public class TypeMeta {

    private final String fqn;
    private final String simpleName;
    private final boolean isPrimitive;
    private final boolean isArray;
    private final boolean isCollection;
    private final boolean isMap;
    private final List<TypeMeta> typeArguments;
    private final List<String> enumConstants;

    public TypeMeta(String fqn, String simpleName, boolean isPrimitive, boolean isArray,
            boolean isCollection, boolean isMap, List<TypeMeta> typeArguments,
            List<String> enumConstants) {
        this.fqn = fqn;
        this.simpleName = simpleName;
        this.isPrimitive = isPrimitive;
        this.isArray = isArray;
        this.isCollection = isCollection;
        this.isMap = isMap;
        this.typeArguments = typeArguments;
        this.enumConstants = enumConstants;
    }

    public String getFqn() {
        return fqn;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    public boolean isArray() {
        return isArray;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public boolean isMap() {
        return isMap;
    }

    public List<TypeMeta> getTypeArguments() {
        return typeArguments;
    }

    public List<String> getEnumConstants() {
        return enumConstants;
    }

    @Override
    public String toString() {
        if (fqn == null) {
            return simpleName != null ? simpleName : "?";
        }
        return fqn;
    }
}
