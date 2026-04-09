package com.poit.doc.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiMethodDoc;
import com.ly.doc.model.ApiParam;
import com.poit.doc.sync.entity.ModelInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 Smart-doc 的 {@link ApiDoc} 树打平，并抽取模型字段（扁平 JSON + ref）与接口根类型引用。
 */
public final class ApiDocSupport {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ApiDocSupport() {
    }

    /**
     * 内部类：用于累积模型的字段列表和描述。
     */
    private static class ModelFields {
        String description = "";
        List<Map<String, Object>> fields = new ArrayList<>();
    }

    public static List<ApiDoc> flattenControllerDocs(List<ApiDoc> roots) {
        List<ApiDoc> out = new ArrayList<>();
        if (roots == null) {
            return out;
        }
        for (ApiDoc root : roots) {
            collectWithMethods(root, out);
        }
        return out;
    }

    private static void collectWithMethods(ApiDoc doc, List<ApiDoc> sink) {
        if (doc == null) {
            return;
        }
        List<ApiMethodDoc> methods = doc.getList();
        if (methods != null && !methods.isEmpty()) {
            sink.add(doc);
        }
        List<ApiDoc> children = doc.getChildrenApiDocs();
        if (children != null) {
            for (ApiDoc c : children) {
                collectWithMethods(c, sink);
            }
        }
    }

    public static Map<String, ModelInfo> extractModelsFromMethod(ApiMethodDoc method) {
        Map<String, ModelFields> acc = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();
        visitParamList(method.getRequestParams(), acc, completed);
        visitParamList(method.getResponseParams(), acc, completed);
        visitParamList(method.getPathParams(), acc, completed);
        visitParamList(method.getQueryParams(), acc, completed);

        return toModelInfoMap(acc);
    }

    /**
     * 仅从请求体 / 响应体参数树抽取模型（不含 path/query），用于 api_model_definition 入库。
     */
    public static Map<String, ModelInfo> extractModelsFromRequestAndResponse(ApiMethodDoc method) {
        Map<String, ModelFields> acc = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();
        visitParamList(method.getRequestParams(), acc, completed);
        visitParamList(method.getResponseParams(), acc, completed);
        return toModelInfoMap(acc);
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

    public static String resolveReqModelRef(ApiMethodDoc m) {
        if (m == null) {
            return null;
        }
        if (m.getIsRequestArray() != null && m.getIsRequestArray() == 1 && notBlank(m.getRequestArrayType())) {
            return m.getRequestArrayType();
        }
        return firstObjectLikeRef(m.getRequestParams());
    }

    public static String resolveResModelRef(ApiMethodDoc m) {
        if (m == null) {
            return null;
        }
        if (m.getIsResponseArray() != null && m.getIsResponseArray() == 1 && notBlank(m.getResponseArrayType())) {
            return m.getResponseArrayType();
        }
        return firstObjectLikeRef(m.getResponseParams());
    }

    private static void visitParamList(List<ApiParam> params, Map<String, ModelFields> acc, Set<String> completed) {
        if (params == null) {
            return;
        }
        for (ApiParam p : params) {
            walkParam(p, acc, completed, new ArrayDeque<>());
        }
    }

    private static void walkParam(ApiParam p, Map<String, ModelFields> acc, Set<String> completed,
            Deque<String> stack) {
        if (p == null) {
            return;
        }
        String type = lower(p.getType());
        String full = trimToNull(p.getFullyTypeName());

        if ("object".equals(type) && full != null) {
            if (completed.contains(full)) {
                return;
            }
            if (stack.contains(full)) {
                return;
            }
            stack.addLast(full);
            ModelFields mf = new ModelFields();
            // 从 ApiParam 的 desc 提取模型描述
            mf.description = p.getDesc() != null ? p.getDesc() : "";
            List<ApiParam> children = p.getChildren();
            if (children != null) {
                for (ApiParam c : children) {
                    mf.fields.add(fieldRow(c));
                    walkParam(c, acc, completed, stack);
                }
            }
            stack.removeLast();
            acc.putIfAbsent(full, mf);
            completed.add(full);
            return;
        }

        if ("array".equals(type)) {
            List<ApiParam> children = p.getChildren();
            if (children != null) {
                for (ApiParam c : children) {
                    walkParam(c, acc, completed, stack);
                }
            }
            return;
        }

        List<ApiParam> rest = p.getChildren();
        if (rest != null) {
            for (ApiParam c : rest) {
                walkParam(c, acc, completed, stack);
            }
        }
    }

    private static Map<String, Object> fieldRow(ApiParam c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", c.getField());
        row.put("type", c.getType());
        row.put("desc", c.getDesc());
        row.put("required", c.isRequired());
        String ct = lower(c.getType());
        String fn = trimToNull(c.getFullyTypeName());
        if (fn != null && ("object".equals(ct) || "array".equals(ct))) {
            row.put("ref", fn);
        }
        return row;
    }

    private static String firstObjectLikeRef(List<ApiParam> params) {
        if (params == null) {
            return null;
        }
        for (ApiParam p : params) {
            String r = objectLikeRef(p);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static String objectLikeRef(ApiParam p) {
        if (p == null) {
            return null;
        }
        String type = lower(p.getType());
        String fn = trimToNull(p.getFullyTypeName());
        if (fn == null) {
            return null;
        }
        if ("object".equals(type)) {
            return fn;
        }
        if ("array".equals(type)) {
            List<ApiParam> ch = p.getChildren();
            if (ch != null && !ch.isEmpty()) {
                return objectLikeRef(ch.get(0));
            }
            return fn;
        }
        List<ApiParam> ch = p.getChildren();
        if (ch != null) {
            for (ApiParam c : ch) {
                String r = objectLikeRef(c);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
