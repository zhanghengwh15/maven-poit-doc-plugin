package com.poit.doc.scanner.provider;

import java.util.List;
import java.util.Map;

/**
 * Metadata for a single field (including from bytecode or source).
 */
public class FieldMeta {

    private final String name;
    private final TypeMeta type;
    private final List<AnnotationMeta> annotations;
    private final String comment;

    public FieldMeta(String name, TypeMeta type, List<AnnotationMeta> annotations, String comment) {
        this.name = name;
        this.type = type;
        this.annotations = annotations;
        this.comment = comment;
    }

    public String getName() {
        return name;
    }

    public TypeMeta getType() {
        return type;
    }

    public List<AnnotationMeta> getAnnotations() {
        return annotations;
    }

    public String getComment() {
        return comment;
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
