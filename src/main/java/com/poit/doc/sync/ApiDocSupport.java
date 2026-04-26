package com.poit.doc.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.poit.doc.scanner.model.ApiField;
import com.poit.doc.scanner.model.ApiMethod;
import com.poit.doc.scanner.model.ApiParam;
import com.poit.doc.sync.entity.ModelInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Flattens self-owned ApiMethod trees into model definitions (flat JSON + ref).
 * No dependency on Smart-doc types.
 */
public final class ApiDocSupport {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ApiDocSupport() {
    }

    private static class ModelFields {
        String description = "";
        List<Map<String, Object>> fields = new ArrayList<>();
    }

    public static Map<String, ModelInfo> extractModelsFromRequestAndResponse(ApiMethod method) {
        Map<String, ModelFields> acc = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();
        // Extract from body params (request)
        for (ApiParam p : method.getParameters()) {
            if ("body".equals(p.getParamIn()) && p.getBodySchema() != null) {
                walkField(p.getBodySchema(), acc, completed);
            }
        }
        // Extract from response body
        if (method.getResponseBody() != null) {
            walkField(method.getResponseBody(), acc, completed);
        }
        return toModelInfoMap(acc);
    }

    public static String resolveReqModelRef(ApiMethod m) {
        if (m == null) return null;
        for (ApiParam p : m.getParameters()) {
            if ("body".equals(p.getParamIn()) && p.getBodySchema() != null) {
                return p.getBodySchema().getRef();
            }
            String ref = objectLikeRef(p.getBodySchema());
            if (ref != null) return ref;
        }
        return null;
    }

    public static String resolveResModelRef(ApiMethod m) {
        if (m == null) return null;
        return objectLikeRef(m.getResponseBody());
    }

    private static Map<String, ModelInfo> toModelInfoMap(Map<String, ModelFields> acc) {
        Map<String, ModelInfo> result = new LinkedHashMap<>();
        for (Map.Entry<String, ModelFields> e : acc.entrySet()) {
            String fullName = e.getKey();
            ModelFields mf = e.getValue();
            String simpleName = ModelInfo.extractSimpleName(fullName);
            String fieldsJson = GSON.toJson(mf.fields);
            result.put(fullName, new ModelInfo(simpleName, mf.description, fieldsJson));
        }
        return result;
    }

    private static void walkField(ApiField f, Map<String, ModelFields> acc, Set<String> completed) {
        if (f == null) return;
        String type = lower(f.getType());
        String full = trimToNull(f.getRef());

        if ("object".equals(type) && full != null) {
            if (completed.contains(full)) return;
            completed.add(full);

            ModelFields mf = new ModelFields();
            mf.description = f.getDescription() != null ? f.getDescription() : "";
            List<ApiField> children = f.getChildren();
            if (children != null) {
                for (ApiField c : children) {
                    mf.fields.add(fieldRow(c));
                    walkField(c, acc, completed);
                }
            }
            completed.remove(full);
            acc.putIfAbsent(full, mf);
            return;
        }

        if ("array".equals(type) || f.isCollection()) {
            List<ApiField> children = f.getChildren();
            if (children != null) {
                for (ApiField c : children) {
                    walkField(c, acc, completed);
                }
            }
            return;
        }

        List<ApiField> rest = f.getChildren();
        if (rest != null) {
            for (ApiField c : rest) {
                walkField(c, acc, completed);
            }
        }
    }

    private static Map<String, Object> fieldRow(ApiField c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", c.getName());
        row.put("type", c.getType());
        row.put("desc", c.getDescription());
        row.put("required", c.isRequired());
        String ct = lower(c.getType());
        String fn = trimToNull(c.getRef());
        if (fn != null && ("object".equals(ct) || "array".equals(ct) || c.isCollection())) {
            row.put("ref", fn);
        }
        if (c.isEnum()) {
            row.put("enumValues", c.getEnumValues());
        }
        return row;
    }

    private static String objectLikeRef(ApiField f) {
        if (f == null) return null;
        String type = lower(f.getType());
        String fn = trimToNull(f.getRef());
        if (fn == null) return null;
        if ("object".equals(type)) return fn;
        if ("array".equals(type) || f.isCollection()) {
            List<ApiField> ch = f.getChildren();
            if (ch != null && !ch.isEmpty()) {
                return objectLikeRef(ch.get(0));
            }
            return fn;
        }
        List<ApiField> ch = f.getChildren();
        if (ch != null) {
            for (ApiField c : ch) {
                String r = objectLikeRef(c);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
