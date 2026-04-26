package com.poit.doc.scanner.merger;

import com.poit.doc.scanner.provider.AnnotationMeta;
import com.poit.doc.scanner.provider.FieldMeta;

/**
 * Merges description and required values from multiple annotation sources with priority chains.
 */
public final class DocMerger {

    private DocMerger() {
    }

    /**
     * Resolve field description with priority:
     * @ApiModelProperty.value > @Schema.description > @JsonPropertyDescription > JavaDoc > empty
     */
    public static String resolveDescription(FieldMeta field) {
        return pickFirst(
                apiModelPropertyValue(field, "value"),
                schemaValue(field, "description"),
                jsonPropertyDescriptionValue(field),
                field.getComment()
        );
    }

    /**
     * Resolve required with priority:
     * @ApiModelProperty.required > @RequestParam.required > JSR-303 > false
     */
    public static boolean resolveRequired(FieldMeta field) {
        Boolean required = boolFirst(
                apiModelPropertyRequired(field),
                requestParamRequired(field)
        );
        if (required != null) return required;

        // JSR-303 validation annotations
        if (hasValidationAnnotation(field, "javax.validation.constraints.NotNull")
                || hasValidationAnnotation(field, "jakarta.validation.constraints.NotNull")
                || hasValidationAnnotation(field, "javax.validation.constraints.NotBlank")
                || hasValidationAnnotation(field, "jakarta.validation.constraints.NotBlank")
                || hasValidationAnnotation(field, "javax.validation.constraints.NotEmpty")
                || hasValidationAnnotation(field, "jakarta.validation.constraints.NotEmpty")) {
            return true;
        }

        return false;
    }

    /**
     * Check if field should be hidden (excluded from output).
     */
    public static boolean isHidden(FieldMeta field) {
        return apiModelPropertyHidden(field) || schemaHidden(field);
    }

    /**
     * Resolve the actual field name, potentially overridden by @JsonProperty.
     */
    public static String resolveName(FieldMeta field) {
        String jsonPropName = jsonPropertyValue(field);
        return jsonPropName != null && !jsonPropName.isEmpty() ? jsonPropName : field.getName();
    }

    // --- Utility methods ---

    /**
     * Return the first non-null, non-empty string from the candidates.
     */
    public static String pickFirst(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isEmpty()) {
                return c;
            }
        }
        return "";
    }

    /**
     * Return the first non-null boolean value.
     */
    public static Boolean boolFirst(Boolean... candidates) {
        for (Boolean c : candidates) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private static String apiModelPropertyValue(FieldMeta field, String key) {
        AnnotationMeta ann = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
        if (ann != null && ann.hasValue(key)) {
            Object v = ann.getValue(key);
            return v != null ? v.toString() : null;
        }
        return null;
    }

    private static String schemaValue(FieldMeta field, String key) {
        AnnotationMeta ann = field.getAnnotation("io.swagger.v3.oas.annotations.media.Schema");
        if (ann != null && ann.hasValue(key)) {
            Object v = ann.getValue(key);
            return v != null ? v.toString() : null;
        }
        return null;
    }

    private static String jsonPropertyDescriptionValue(FieldMeta field) {
        AnnotationMeta ann = field.getAnnotation("com.fasterxml.jackson.annotation.JsonPropertyDescription");
        if (ann != null) {
            Object v = ann.getValue("value");
            if (v != null) return v.toString();
        }
        return null;
    }

    private static Boolean apiModelPropertyRequired(FieldMeta field) {
        AnnotationMeta ann = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
        if (ann != null && ann.hasValue("required")) {
            Object v = ann.getValue("required");
            if (v instanceof Boolean b) return b;
            return Boolean.parseBoolean(v.toString());
        }
        return null;
    }

    private static Boolean requestParamRequired(FieldMeta field) {
        AnnotationMeta ann = field.getAnnotation("org.springframework.web.bind.annotation.RequestParam");
        if (ann != null && ann.hasValue("required")) {
            Object v = ann.getValue("required");
            if (v instanceof Boolean b) return b;
            return Boolean.parseBoolean(v.toString());
        }
        return null;
    }

    private static boolean hasValidationAnnotation(FieldMeta field, String fqn) {
        return field.getAnnotation(fqn) != null;
    }

    private static boolean apiModelPropertyHidden(FieldMeta field) {
        AnnotationMeta ann = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
        if (ann != null) {
            Object hidden = ann.getValue("hidden");
            if (hidden instanceof Boolean b && b) return true;
            if ("true".equals(String.valueOf(hidden))) return true;
        }
        return false;
    }

    private static boolean schemaHidden(FieldMeta field) {
        AnnotationMeta ann = field.getAnnotation("io.swagger.v3.oas.annotations.media.Schema");
        if (ann != null) {
            Object hidden = ann.getValue("hidden");
            if (hidden instanceof Boolean b && b) return true;
            if ("true".equals(String.valueOf(hidden))) return true;
        }
        return false;
    }

    private static String jsonPropertyValue(FieldMeta field) {
        AnnotationMeta ann = field.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty");
        if (ann != null) {
            Object v = ann.getValue("value");
            if (v != null) return v.toString();
        }
        return null;
    }
}
