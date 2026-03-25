package com.poit.doc.sync;

import com.google.gson.Gson;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiMethodDoc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将打平后的接口与模型定义 Upsert 到 MySQL（JSON 字段存字段明细）。
 * <p>
 * 使用 HikariCP 连接池 + 批量操作，支持超时配置防止插件卡死构建流程。
 * </p>
 */
public class DocSyncService {

    private static final Gson GSON = new Gson();
    private static final int BATCH_SIZE = 100;

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final String serviceName;
    private final String serviceVersion;
    private final String env;

    public DocSyncService(String jdbcUrl, String user, String password, String serviceName, String serviceVersion,
            String env) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.env = env;
    }

    public void sync(List<ApiDoc> controllerDocs) throws SQLException {
        Map<String, ModelInfo> mergedModels = new LinkedHashMap<>();
        List<InterfaceRecord> interfaces = new ArrayList<>();

        // 1. 收集所有数据
        for (ApiDoc doc : controllerDocs) {
            String moduleName = doc.getDesc();
            List<ApiMethodDoc> methods = doc.getList();
            if (methods == null) {
                continue;
            }
            for (ApiMethodDoc m : methods) {
                mergedModels.putAll(ApiDocSupport.extractModelsFromMethod(m));
                interfaces.add(buildInterfaceRecord(moduleName, m));
            }
        }

        // 2. 使用连接池批量写入
        try (Connection conn = DataSourceManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            try {
                batchUpsertModels(conn, new ArrayList<>(mergedModels.entrySet()));
                batchUpsertInterfaces(conn, interfaces);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private InterfaceRecord buildInterfaceRecord(String moduleName, ApiMethodDoc m) {
        String path = m.getPath() != null ? m.getPath() : m.getUrl();
        String method = m.getType() != null ? m.getType() : "";
        String apiName = m.getName();
        String reqRef = ApiDocSupport.resolveReqModelRef(m);
        String resRef = ApiDocSupport.resolveResModelRef(m);

        // raw_info: 存储额外的接口元数据
        Map<String, Object> rawInfo = new LinkedHashMap<>();
        rawInfo.put("methodId", m.getMethodId());
        rawInfo.put("contentType", m.getContentType());
        rawInfo.put("headers", m.getHeaders());
        rawInfo.put("pathParams", m.getPathParams());
        rawInfo.put("queryParams", m.getQueryParams());
        rawInfo.put("requestParams", m.getRequestParams());
        rawInfo.put("responseParams", m.getResponseParams());
        String rawInfoJson = GSON.toJson(rawInfo);

        return new InterfaceRecord(moduleName, apiName, path, method, reqRef, resRef, rawInfoJson);
    }

    private void batchUpsertModels(Connection conn, List<Map.Entry<String, ModelInfo>> models) throws SQLException {
        String sql = "INSERT INTO api_model_definition (full_name, simple_name, description, fields) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE simple_name = VALUES(simple_name), description = VALUES(description), "
                + "fields = VALUES(fields), modify_time = CURRENT_TIMESTAMP";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (Map.Entry<String, ModelInfo> e : models) {
                String fullName = e.getKey();
                ModelInfo info = e.getValue();
                ps.setString(1, fullName);
                ps.setString(2, info.getSimpleName());
                ps.setString(3, info.getDescription());
                ps.setString(4, info.getFieldsJson());
                ps.addBatch();

                if (++count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            if (count % BATCH_SIZE != 0) {
                ps.executeBatch();
            }
        }
    }

    private void batchUpsertInterfaces(Connection conn, List<InterfaceRecord> interfaces) throws SQLException {
        String sql = "INSERT INTO api_interface (service_name, service_version, env, module_name, api_name, "
                + "path, method, req_model_ref, res_model_ref, raw_info) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE service_version = VALUES(service_version), module_name = VALUES(module_name), "
                + "api_name = VALUES(api_name), req_model_ref = VALUES(req_model_ref), "
                + "res_model_ref = VALUES(res_model_ref), raw_info = VALUES(raw_info), "
                + "modify_time = CURRENT_TIMESTAMP";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (InterfaceRecord r : interfaces) {
                ps.setString(1, serviceName);
                ps.setString(2, serviceVersion);
                ps.setString(3, env);
                ps.setString(4, r.moduleName != null ? r.moduleName : "");
                ps.setString(5, r.apiName != null ? r.apiName : "");
                ps.setString(6, r.path != null ? r.path : "");
                ps.setString(7, r.method);
                ps.setString(8, r.reqRef != null ? r.reqRef : "");
                ps.setString(9, r.resRef != null ? r.resRef : "");
                ps.setString(10, r.rawInfoJson);
                ps.addBatch();

                if (++count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            if (count % BATCH_SIZE != 0) {
                ps.executeBatch();
            }
        }
    }

    /**
     * 接口记录内部类。
     */
    private static class InterfaceRecord {
        final String moduleName;
        final String apiName;
        final String path;
        final String method;
        final String reqRef;
        final String resRef;
        final String rawInfoJson;

        InterfaceRecord(String moduleName, String apiName, String path, String method,
                String reqRef, String resRef, String rawInfoJson) {
            this.moduleName = moduleName;
            this.apiName = apiName;
            this.path = path;
            this.method = method;
            this.reqRef = reqRef;
            this.resRef = resRef;
            this.rawInfoJson = rawInfoJson;
        }
    }
}