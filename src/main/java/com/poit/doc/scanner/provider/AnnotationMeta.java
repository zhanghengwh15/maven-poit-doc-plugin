package com.poit.doc.scanner.provider;

import java.util.Map;

/**
 * Metadata for an annotation instance on a field, method, or class.
 */
public class AnnotationMeta {

    private final String fqn;
    private final Map<String, Object> values;

    public AnnotationMeta(String fqn, Map<String, Object> values) {
        this.fqn = fqn;
        this.values = values;
    }

    public String getFqn() {
        return fqn;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(String key) {
        return values != null ? (T) values.get(key) : null;
    }

    public Object getValue(String key, Object defaultValue) {
        Object v = getValue(key);
        return v != null ? v : defaultValue;
    }

    public boolean hasValue(String key) {
        return values != null && values.containsKey(key);
    }
}
