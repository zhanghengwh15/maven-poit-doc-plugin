package com.poit.doc.sync.dataTransfer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiMethodDoc;
import com.poit.doc.sync.ApiDocSupport;
import com.poit.doc.sync.entity.ModelInfo;
import com.poit.doc.sync.entity.ApiInterfaceEntity;
import com.poit.doc.sync.util.ApiSignatureUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 将 Smart-doc 解析结果同步到 MySQL：模型定义（含 ref_model_id）与接口行（含 body 模型 id、参数 MD5、path/query JSON）。
 */
public class DocSyncService {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int SQL_TIMEOUT_SECONDS = 10;

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final String serviceName;
    private final String serviceVersion;
    private final String env;
    private final String projectName;

    public DocSyncService(String jdbcUrl, String user, String password, String serviceName, String serviceVersion,
            String env) {
        this(jdbcUrl, user, password, serviceName, serviceVersion, env, "");
    }

    public DocSyncService(String jdbcUrl, String user, String password, String serviceName, String serviceVersion,
            String env, String projectName) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.env = env;
        this.projectName = projectName != null ? projectName : "";
    }

    public void sync(List<ApiDoc> controllerDocs) throws SQLException {
        Map<String, ModelInfo> mergedModels = new LinkedHashMap<>();
        List<InterfaceRecord> interfaces = new ArrayList<>();

        for (ApiDoc doc : controllerDocs) {
            String moduleName = doc.getDesc();
            List<ApiMethodDoc> methods = doc.getList();
            if (methods == null) {
                continue;
            }
            for (ApiMethodDoc m : methods) {
                mergedModels.putAll(ApiDocSupport.extractModelsFromRequestAndResponse(m));
                interfaces.add(buildInterfaceRecord(moduleName, m));
            }
        }

        try (Connection conn = DataSourceManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            try {
                Map<String, Long> modelIds = ModelDefinitionSync.syncModels(conn, serviceName, mergedModels,
                        SQL_TIMEOUT_SECONDS);
                upsertAndDeleteInterfaces(conn, interfaces, modelIds);
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

        String pathParamsJson = paramsToJson(m.getPathParams());
        String queryParamsJson = paramsToJson(m.getQueryParams());
        String reqMd5 = ApiSignatureUtils.generateParamsSignature(m.getRequestParams());
        String resMd5 = ApiSignatureUtils.generateParamsSignature(m.getResponseParams());

        Map<String, Object> rawInfo = new LinkedHashMap<>();
        rawInfo.put("methodId", m.getMethodId());
        rawInfo.put("contentType", m.getContentType());
        rawInfo.put("headers", m.getHeaders());
        String rawInfoJson = GSON.toJson(rawInfo);

        return new InterfaceRecord(moduleName, apiName, path, method, reqRef, resRef, pathParamsJson, queryParamsJson,
                reqMd5, resMd5, rawInfoJson);
    }

    private static String paramsToJson(List<com.ly.doc.model.ApiParam> params) {
        if (params == null || params.isEmpty()) {
            return "[]";
        }
        return GSON.toJson(params);
    }

    private void upsertAndDeleteInterfaces(Connection conn, List<InterfaceRecord> interfaces,
            Map<String, Long> modelIds) throws SQLException {
        Map<String, ApiInterfaceEntity> existing = loadCurrentInterfaces(conn);
        List<ApiInterfaceEntity> inserts = new ArrayList<>();
        List<ApiInterfaceEntity> versionBumps = new ArrayList<>();
        List<ApiInterfaceEntity> fullUpdates = new ArrayList<>();

        for (InterfaceRecord record : interfaces) {
            String key = uniqueInterfaceKey(record.path, record.method);
            long reqBodyId = ModelDefinitionSync.resolveModelId(modelIds, record.reqRootRef);
            long resBodyId = ModelDefinitionSync.resolveModelId(modelIds, record.resRootRef);

            ApiInterfaceEntity old = existing.remove(key);
            if (old == null) {
                inserts.add(toInsertEntity(record, reqBodyId, resBodyId));
            } else if (shouldSkipContentUpdate(old, record)) {
                ApiInterfaceEntity bump = new ApiInterfaceEntity();
                bump.setId(old.getId());
                bump.setServiceVersion(serviceVersion);
                versionBumps.add(bump);
            } else {
                fullUpdates.add(toUpdateEntity(old.getId(), record, reqBodyId, resBodyId));
            }
        }

        if (!inserts.isEmpty()) {
            SimpleMapper.insertBatch(conn, inserts);
        }
        if (!versionBumps.isEmpty()) {
            SimpleMapper.updateBatch(conn, versionBumps);
        }
        if (!fullUpdates.isEmpty()) {
            SimpleMapper.updateBatch(conn, fullUpdates);
        }

        List<ApiInterfaceEntity> staleDeletes = new ArrayList<>();
        for (ApiInterfaceEntity stale : existing.values()) {
            if (stale.getId() != null) {
                ApiInterfaceEntity deleteEntity = new ApiInterfaceEntity();
                deleteEntity.setId(stale.getId());
                deleteEntity.setRecStatus(0);
                staleDeletes.add(deleteEntity);
            }
        }
        if (!staleDeletes.isEmpty()) {
            SimpleMapper.updateBatch(conn, staleDeletes);
        }
    }

    private boolean shouldSkipContentUpdate(ApiInterfaceEntity old, InterfaceRecord record) {
        return Objects.equals(old.getReqParamsMd5(), record.reqParamsMd5)
                && Objects.equals(old.getResParamsMd5(), record.resParamsMd5)
                && Objects.equals(normalizeJson(old.getPathParams()), normalizeJson(record.pathParamsJson))
                && Objects.equals(normalizeJson(old.getQueryParams()), normalizeJson(record.queryParamsJson))
                && Objects.equals(nullToEmpty(old.getModuleName()), nullToEmpty(record.moduleName))
                && Objects.equals(nullToEmpty(old.getApiName()), nullToEmpty(record.apiName))
                && Objects.equals(nullToEmpty(old.getRawInfo()), nullToEmpty(record.rawInfoJson));
    }

    private static String normalizeJson(String s) {
        return s == null ? "" : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private ApiInterfaceEntity toInsertEntity(InterfaceRecord record, long reqBodyId, long resBodyId) {
        ApiInterfaceEntity entity = new ApiInterfaceEntity();
        entity.setServiceName(serviceName);
        entity.setServiceVersion(serviceVersion);
        entity.setEnv(env);
        entity.setProjectName(projectName);
        entity.setModuleName(record.moduleName != null ? record.moduleName : "");
        entity.setApiName(record.apiName != null ? record.apiName : "");
        entity.setPath(record.path != null ? record.path : "");
        entity.setMethod(normalizeMethod(record.method));
        entity.setPathParams(record.pathParamsJson);
        entity.setQueryParams(record.queryParamsJson);
        entity.setReqBodyModelId(reqBodyId > 0 ? reqBodyId : 0L);
        entity.setResBodyModelId(resBodyId > 0 ? resBodyId : 0L);
        entity.setReqParamsMd5(record.reqParamsMd5);
        entity.setResParamsMd5(record.resParamsMd5);
        entity.setRawInfo(record.rawInfoJson);
        entity.setRecStatus(1);
        return entity;
    }

    private ApiInterfaceEntity toUpdateEntity(Long id, InterfaceRecord record, long reqBodyId, long resBodyId) {
        ApiInterfaceEntity entity = new ApiInterfaceEntity();
        entity.setId(id);
        entity.setServiceVersion(serviceVersion);
        entity.setProjectName(projectName);
        entity.setModuleName(record.moduleName != null ? record.moduleName : "");
        entity.setApiName(record.apiName != null ? record.apiName : "");
        entity.setPath(record.path != null ? record.path : "");
        entity.setMethod(normalizeMethod(record.method));
        entity.setPathParams(record.pathParamsJson);
        entity.setQueryParams(record.queryParamsJson);
        entity.setReqBodyModelId(reqBodyId > 0 ? reqBodyId : 0L);
        entity.setResBodyModelId(resBodyId > 0 ? resBodyId : 0L);
        entity.setReqParamsMd5(record.reqParamsMd5);
        entity.setResParamsMd5(record.resParamsMd5);
        entity.setRawInfo(record.rawInfoJson);
        entity.setRecStatus(1);
        return entity;
    }

    private Map<String, ApiInterfaceEntity> loadCurrentInterfaces(Connection conn) throws SQLException {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("serviceName", serviceName);
        conditions.put("env", env);
        conditions.put("recStatus", 1);
        List<ApiInterfaceEntity> rows = SimpleMapper.findByColumns(conn, ApiInterfaceEntity.class, conditions,
                SQL_TIMEOUT_SECONDS);
        Map<String, ApiInterfaceEntity> result = new HashMap<>();
        for (ApiInterfaceEntity row : rows) {
            if (row.getId() != null) {
                result.put(uniqueInterfaceKey(row.getPath(), row.getMethod()), row);
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

    private static class InterfaceRecord {
        final String moduleName;
        final String apiName;
        final String path;
        final String method;
        final String reqRootRef;
        final String resRootRef;
        final String pathParamsJson;
        final String queryParamsJson;
        final String reqParamsMd5;
        final String resParamsMd5;
        final String rawInfoJson;

        InterfaceRecord(String moduleName, String apiName, String path, String method, String reqRootRef,
                String resRootRef, String pathParamsJson, String queryParamsJson, String reqParamsMd5,
                String resParamsMd5, String rawInfoJson) {
            this.moduleName = moduleName;
            this.apiName = apiName;
            this.path = path;
            this.method = method;
            this.reqRootRef = reqRootRef;
            this.resRootRef = resRootRef;
            this.pathParamsJson = pathParamsJson;
            this.queryParamsJson = queryParamsJson;
            this.reqParamsMd5 = reqParamsMd5;
            this.resParamsMd5 = resParamsMd5;
            this.rawInfoJson = rawInfoJson;
        }
    }
}
