package com.poit.doc.sync.dataTransfer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.poit.doc.sync.entity.ModelInfo;
import com.poit.doc.sync.entity.ApiModelDefinitionEntity;
import com.poit.doc.sync.util.ApiSignatureUtils;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 将 {@link ModelInfo} 按依赖自底向上写入 {@code api_model_definition}，字段中的 {@code ref} 替换为 {@code ref_model_id}，
 * 插入新版本后对同 (service_name, full_name) 的其它行物理删除；无依赖的连续待插入行合并为批量插入。
 */
public final class ModelDefinitionSync {

    private static final Gson GSON = new Gson();
    private static final Type MAP_LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {
    }.getType();

    private ModelDefinitionSync() {
    }

    /**
     * 同步一批模型定义，返回 fullName -&gt; 当前生效行 id（本批插入或已存在同 MD5 行）。
     */
    public static Map<String, Long> syncModels(Connection conn, String serviceName, Map<String, ModelInfo> models,
            int timeoutSeconds) throws SQLException {
        Map<String, Long> idByFullName = new LinkedHashMap<>();
        if (models == null || models.isEmpty()) {
            return idByFullName;
        }
        Set<String> nodeSet = models.keySet();
        Map<String, List<String>> deps = new HashMap<>();
        for (String fn : nodeSet) {
            deps.put(fn, extractRefFqns(models.get(fn).getFieldsJson(), nodeSet));
        }

        List<String> order = topologicalSort(nodeSet, deps);
        List<ApiModelDefinitionEntity> pendingInserts = new ArrayList<>();
        for (String fullName : order) {
            for (String r : deps.getOrDefault(fullName, Collections.emptyList())) {
                if (!idByFullName.containsKey(r)) {
                    flushPendingInserts(conn, pendingInserts, idByFullName, timeoutSeconds);
                    break;
                }
            }

            ModelInfo info = models.get(fullName);
            List<Map<String, Object>> rows = parseFieldRows(info.getFieldsJson());
            List<Map<String, Object>> resolved = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> copy = new LinkedHashMap<>(row);
                Object ref = copy.remove("ref");
                if (ref instanceof String) {
                    String refFqn = (String) ref;
                    Long childId = idByFullName.get(refFqn);
                    if (childId != null) {
                        copy.put("ref_model_id", childId);
                    }
                }
                resolved.add(copy);
            }
            String fieldsJson = GSON.toJson(resolved);
            String modelMd5 = ApiSignatureUtils.md5ModelFieldsJson(fieldsJson);

            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("serviceName", serviceName);
            cond.put("fullName", fullName);
            cond.put("modelMd5", modelMd5);
            cond.put("recStatus", 1);
            ApiModelDefinitionEntity existing = SimpleMapper.findOneByColumns(conn, ApiModelDefinitionEntity.class, cond,
                    timeoutSeconds);
            if (existing != null && existing.getId() != null) {
                idByFullName.put(fullName, existing.getId());
                continue;
            }

            ApiModelDefinitionEntity insert = new ApiModelDefinitionEntity();
            insert.setServiceName(serviceName);
            insert.setFullName(fullName);
            insert.setSimpleName(info.getSimpleName());
            insert.setDescription(info.getDescription());
            insert.setModelMd5(modelMd5);
            insert.setFields(fieldsJson);
            insert.setRecStatus(1);
            pendingInserts.add(insert);
        }
        flushPendingInserts(conn, pendingInserts, idByFullName, timeoutSeconds);
        return idByFullName;
    }

    private static void flushPendingInserts(Connection conn, List<ApiModelDefinitionEntity> pending,
            Map<String, Long> idByFullName, int timeoutSeconds) throws SQLException {
        if (pending.isEmpty()) {
            return;
        }
        SimpleMapper.insertBatch(conn, pending, timeoutSeconds);
        for (ApiModelDefinitionEntity row : pending) {
            Long newId = row.getId();
            if (newId != null) {
                idByFullName.put(row.getFullName(), newId);
                deleteOtherModelRows(conn, row.getServiceName(), row.getFullName(), newId, timeoutSeconds);
            }
        }
        pending.clear();
    }

    /**
     * 物理删除同服务、同全限定名下除当前行以外的记录（新版本替代旧版本）。
     */
    private static void deleteOtherModelRows(Connection conn, String serviceName, String fullName, long keepId,
            int timeoutSeconds) throws SQLException {
        String sql = "DELETE FROM api_model_definition WHERE service_name = ? AND full_name = ? AND id <> ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(Math.max(0, timeoutSeconds));
            ps.setString(1, serviceName);
            ps.setString(2, fullName);
            ps.setLong(3, keepId);
            ps.executeUpdate();
        }
    }

    private static List<String> extractRefFqns(String fieldsJson, Set<String> validNames) {
        List<String> refs = new ArrayList<>();
        for (Map<String, Object> row : parseFieldRows(fieldsJson)) {
            Object ref = row.get("ref");
            if (ref instanceof String) {
                String r = (String) ref;
                if (validNames.contains(r)) {
                    refs.add(r);
                }
            }
        }
        return refs;
    }

    private static List<Map<String, Object>> parseFieldRows(String fieldsJson) {
        if (fieldsJson == null || fieldsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = GSON.fromJson(fieldsJson, MAP_LIST_TYPE);
        return rows != null ? rows : new ArrayList<>();
    }

    /**
     * 拓扑序：被依赖的 fullName 先于引用它的模型。
     */
    private static List<String> topologicalSort(Set<String> nodes, Map<String, List<String>> deps) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String n : nodes) {
            inDegree.put(n, deps.getOrDefault(n, new ArrayList<>()).size());
            for (String r : deps.getOrDefault(n, new ArrayList<>())) {
                dependents.computeIfAbsent(r, k -> new ArrayList<>()).add(n);
            }
        }
        Queue<String> q = new ArrayDeque<>();
        for (String n : nodes) {
            if (inDegree.get(n) == 0) {
                q.add(n);
            }
        }
        List<String> out = new ArrayList<>();
        while (!q.isEmpty()) {
            String n = q.remove();
            out.add(n);
            for (String p : dependents.getOrDefault(n, new ArrayList<>())) {
                int v = inDegree.get(p) - 1;
                inDegree.put(p, v);
                if (v == 0) {
                    q.add(p);
                }
            }
        }
        if (out.size() < nodes.size()) {
            for (String n : nodes) {
                if (!out.contains(n)) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /**
     * 解析根类型 FQN 对应的模型 id（0 表示无）。
     */
    public static long resolveModelId(Map<String, Long> idByFullName, String rootFqn) {
        if (rootFqn == null || rootFqn.isEmpty()) {
            return 0L;
        }
        Long id = idByFullName.get(rootFqn);
        return id != null ? id : 0L;
    }
}
