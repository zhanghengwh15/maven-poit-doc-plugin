package com.poit.doc.scanner.provider;

import java.util.List;
import java.util.Map;

/**
 * Metadata for a single class.
 */
public class ClassMeta {

    private final String fqn;
    private final String simpleName;
    private final boolean isEnum;
    private final boolean isInterface;
    private final List<FieldMeta> fields;
    private final List<AnnotationMeta> annotations;
    private final List<TypeMeta> interfaces;
    private final TypeMeta superclass;
    private final String classComment;
    private final Map<String, String> javadocParams;

    public ClassMeta(String fqn, String simpleName, boolean isEnum, boolean isInterface,
            List<FieldMeta> fields, List<AnnotationMeta> annotations,
            List<TypeMeta> interfaces, TypeMeta superclass,
            String classComment, Map<String, String> javadocParams) {
        this.fqn = fqn;
        this.simpleName = simpleName;
        this.isEnum = isEnum;
        this.isInterface = isInterface;
        this.fields = fields;
        this.annotations = annotations;
        this.interfaces = interfaces;
        this.superclass = superclass;
        this.classComment = classComment;
        this.javadocParams = javadocParams;
    }

    public String getFqn() {
        return fqn;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public List<FieldMeta> getFields() {
        return fields;
    }

    public List<AnnotationMeta> getAnnotations() {
        return annotations;
    }

    public List<TypeMeta> getInterfaces() {
        return interfaces;
    }

    public TypeMeta getSuperclass() {
        return superclass;
    }

    public String getClassComment() {
        return classComment;
    }

    public Map<String, String> getJavadocParams() {
        return javadocParams;
    }

    public AnnotationMeta getAnnotation(String annotationFqn) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationMeta a : annotations) {
            if (annotationFqn.equals(a.getFqn())) {
                return a;
            }
        }
        return null;
    }
}
