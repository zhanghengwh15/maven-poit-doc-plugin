package com.poit.doc.sync.dataTransfer;

import com.alibaba.fastjson2.JSON;
import com.poit.doc.scanner.model.ApiMethod;
import com.poit.doc.scanner.model.ApiParam;
import com.poit.doc.sync.ApiDocSupport;
import com.poit.doc.sync.SyncInput;
import com.poit.doc.sync.SyncInput.SyncController;
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
 * Syncs scanner output to MySQL: model definitions (with ref_model_id) and interface rows
 * (with body model id, param MD5, path/query JSON).
 */
public class DocSyncService {

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

    public void sync(SyncInput input) throws SQLException {
        Map<String, ModelInfo> mergedModels = new LinkedHashMap<>();
        List<InterfaceRecord> interfaces = new ArrayList<>();

        for (SyncController ctrl : input.getControllers()) {
            String moduleName = ctrl.getDescription();
            for (ApiMethod m : ctrl.getMethods()) {
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

    private InterfaceRecord buildInterfaceRecord(String moduleName, ApiMethod m) {
        String path = m.getPath();
        String method = m.getHttpMethod();
        String apiName = m.getMethodName();
        String reqRef = ApiDocSupport.resolveReqModelRef(m);
        String resRef = ApiDocSupport.resolveResModelRef(m);

        // Build path/query params from parameters list
        List<SimpleParam> pathParams = new ArrayList<>();
        List<SimpleParam> queryParams = new ArrayList<>();
        for (ApiParam p : m.getParameters()) {
            SimpleParam sp = new SimpleParam(p.getName(), p.getType(), p.getDescription(),
                    p.isRequired(), p.getDefaultValue());
            if ("path".equals(p.getParamIn())) {
                pathParams.add(sp);
            } else if ("query".equals(p.getParamIn())) {
                queryParams.add(sp);
            }
        }

        String pathParamsJson = JSON.toJSONString(pathParams);
        String queryParamsJson = JSON.toJSONString(queryParams);
        String reqMd5 = ""; // MD5 from request body schema
        String resMd5 = ""; // MD5 from response body schema

        Map<String, Object> rawInfo = new LinkedHashMap<>();
        rawInfo.put("deprecated", m.isDeprecated());
        rawInfo.put("summary", m.getSummary());
        String rawInfoJson = JSON.toJSONString(rawInfo);

        return new InterfaceRecord(moduleName, apiName, path, method, reqRef, resRef, pathParamsJson, queryParamsJson,
                reqMd5, resMd5, rawInfoJson);
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

    private static class SimpleParam {
        final String name;
        final String type;
        final String desc;
        final boolean required;
        final String defaultValue;

        SimpleParam(String name, String type, String desc, boolean required, String defaultValue) {
            this.name = name;
            this.type = type;
            this.desc = desc;
            this.required = required;
            this.defaultValue = defaultValue;
        }
    }
}
