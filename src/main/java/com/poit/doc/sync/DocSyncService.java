package com.poit.doc.sync;

import com.google.gson.Gson;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiMethodDoc;
import com.poit.doc.sync.entity.ApiInterfaceEntity;
import com.poit.doc.sync.entity.ApiModelDefinitionEntity;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 将打平后的接口与模型定义同步到 MySQL（JSON 字段存字段明细）。
 * <p>
 * 使用 HikariCP 连接池 + 轻量反射 Mapper，支持超时配置防止插件卡死构建流程。
 * </p>
 */
public class DocSyncService {

    private static final Gson GSON = new Gson();
    private static final int SQL_TIMEOUT_SECONDS = 10;

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

        // 2. 使用连接池写入
        try (Connection conn = DataSourceManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            try {
                upsertModels(conn, mergedModels);
                upsertAndDeleteInterfaces(conn, interfaces);
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

    private void upsertModels(Connection conn, Map<String, ModelInfo> models) throws SQLException {
        for (Map.Entry<String, ModelInfo> entry : models.entrySet()) {
            String fullName = entry.getKey();
            ModelInfo info = entry.getValue();
            Map<String, Object> modelConditions = new LinkedHashMap<>();
            modelConditions.put("fullName", fullName);
            ApiModelDefinitionEntity existingModel = SimpleMapper.findOneByColumns(
                    conn, ApiModelDefinitionEntity.class, modelConditions, SQL_TIMEOUT_SECONDS);
            Long existingId = existingModel != null ? existingModel.getId() : null;
            if (existingId == null) {
                ApiModelDefinitionEntity insertEntity = new ApiModelDefinitionEntity();
                insertEntity.setFullName(fullName);
                insertEntity.setSimpleName(info.getSimpleName());
                insertEntity.setDescription(info.getDescription());
                insertEntity.setFields(info.getFieldsJson());
                insertEntity.setRecStatus(1);
                SimpleMapper.insert(conn, insertEntity, SQL_TIMEOUT_SECONDS);
            } else {
                ApiModelDefinitionEntity updateEntity = new ApiModelDefinitionEntity();
                updateEntity.setId(existingId);
                updateEntity.setSimpleName(info.getSimpleName());
                updateEntity.setDescription(info.getDescription());
                updateEntity.setFields(info.getFieldsJson());
                updateEntity.setRecStatus(1);
                SimpleMapper.updateById(conn, updateEntity, SQL_TIMEOUT_SECONDS);
            }
        }
    }

    private void upsertAndDeleteInterfaces(Connection conn, List<InterfaceRecord> interfaces) throws SQLException {
        Map<String, Long> existing = loadCurrentInterfaces(conn);
        for (InterfaceRecord record : interfaces) {
            String key = uniqueInterfaceKey(record.path, record.method);
            Long existingId = existing.remove(key);
            if (existingId == null) {
                ApiInterfaceEntity insertEntity = toInsertEntity(record);
                SimpleMapper.insert(conn, insertEntity, SQL_TIMEOUT_SECONDS);
            } else {
                ApiInterfaceEntity updateEntity = toUpdateEntity(existingId, record);
                SimpleMapper.updateById(conn, updateEntity, SQL_TIMEOUT_SECONDS);
            }
        }

        // 本次同步没有出现的接口，执行逻辑删除。
        for (Long staleId : existing.values()) {
            ApiInterfaceEntity deleteEntity = new ApiInterfaceEntity();
            deleteEntity.setId(staleId);
            deleteEntity.setRecStatus(0);
            SimpleMapper.updateById(conn, deleteEntity, SQL_TIMEOUT_SECONDS);
        }
    }

    private ApiInterfaceEntity toInsertEntity(InterfaceRecord record) {
        ApiInterfaceEntity entity = new ApiInterfaceEntity();
        entity.setServiceName(serviceName);
        entity.setServiceVersion(serviceVersion);
        entity.setEnv(env);
        entity.setModuleName(record.moduleName != null ? record.moduleName : "");
        entity.setApiName(record.apiName != null ? record.apiName : "");
        entity.setPath(record.path != null ? record.path : "");
        entity.setMethod(normalizeMethod(record.method));
        entity.setReqModelRef(record.reqRef != null ? record.reqRef : "");
        entity.setResModelRef(record.resRef != null ? record.resRef : "");
        entity.setRawInfo(record.rawInfoJson);
        entity.setRecStatus(1);
        return entity;
    }

    private ApiInterfaceEntity toUpdateEntity(Long id, InterfaceRecord record) {
        ApiInterfaceEntity entity = new ApiInterfaceEntity();
        entity.setId(id);
        entity.setServiceVersion(serviceVersion);
        entity.setModuleName(record.moduleName != null ? record.moduleName : "");
        entity.setApiName(record.apiName != null ? record.apiName : "");
        entity.setReqModelRef(record.reqRef != null ? record.reqRef : "");
        entity.setResModelRef(record.resRef != null ? record.resRef : "");
        entity.setRawInfo(record.rawInfoJson);
        entity.setRecStatus(1);
        return entity;
    }

    private Map<String, Long> loadCurrentInterfaces(Connection conn) throws SQLException {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("serviceName", serviceName);
        conditions.put("env", env);
        conditions.put("recStatus", 1);
        List<ApiInterfaceEntity> rows = SimpleMapper.findByColumns(
                conn, ApiInterfaceEntity.class, conditions, SQL_TIMEOUT_SECONDS);
        Map<String, Long> result = new HashMap<>();
        for (ApiInterfaceEntity row : rows) {
            if (row.getId() != null) {
                result.put(uniqueInterfaceKey(row.getPath(), row.getMethod()), row.getId());
            }
        }
        return result;
    }

    private String uniqueInterfaceKey(String path, String method) {
        String safePath = path == null ? "" : path;
        return safePath + "#" + normalizeMethod(method);
    }

    private String normalizeMethod(String method) {
        return method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
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