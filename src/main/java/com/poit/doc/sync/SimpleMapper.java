package com.poit.doc.sync;

import com.poit.doc.sync.util.MetaCacheUtil;
import com.poit.doc.sync.util.MetaCacheUtil.EntityMeta;
import com.poit.doc.sync.util.MetaCacheUtil.FieldMeta;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级反射 Mapper，支持按主键查询/更新/插入/删除。
 * <p>
 * 支持：
 * 1) 本地元数据缓存（降低高频反射开销）
 * 2) 驼峰与下划线映射 + @Column 显式列名
 * 3) createTime / modifyTime 自动填充
 * 4) 自增主键回填
 * </p>
 */
public final class SimpleMapper {

    /** 未显式指定超时时的默认查询/更新超时（秒）。 */
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    /** 批量插入单批最大条数（含），超过则自动拆批。 */
    private static final int INSERT_BATCH_MAX_SIZE = 500;

    private SimpleMapper() {
    }

    public static <T> T findById(Connection conn, Class<T> clazz, Object id) {
        return findById(conn, clazz, id, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static <T> T findById(Connection conn, Class<T> clazz, Object id, int timeoutSeconds) {
        EntityMeta meta = MetaCacheUtil.getEntityMeta(clazz);
        String sql = "SELECT * FROM " + meta.tableName + " WHERE `" + meta.idColumn + "` = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setQueryTimeout(normalizeTimeout(timeoutSeconds));
            pstmt.setObject(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(clazz, rs, meta);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error finding entity by ID: " + clazz.getName(), e);
        }
    }

    public static <T> T findOneByColumns(Connection conn, Class<T> clazz, Map<String, Object> conditions) {
        return findOneByColumns(conn, clazz, conditions, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static <T> T findOneByColumns(Connection conn, Class<T> clazz, Map<String, Object> conditions,
            int timeoutSeconds) {
        List<T> rows = queryByColumns(conn, clazz, conditions, timeoutSeconds, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static <T> List<T> findByColumns(Connection conn, Class<T> clazz, Map<String, Object> conditions) {
        return findByColumns(conn, clazz, conditions, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static <T> List<T> findByColumns(Connection conn, Class<T> clazz, Map<String, Object> conditions,
            int timeoutSeconds) {
        return queryByColumns(conn, clazz, conditions, timeoutSeconds, null);
    }

    public static int insert(Connection conn, Object entity) {
        return insert(conn, entity, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static int insert(Connection conn, Object entity, int timeoutSeconds) {
        Class<?> clazz = entity.getClass();
        EntityMeta meta = MetaCacheUtil.getEntityMeta(clazz);
        autoFillTime(meta, entity, true);

        List<String> columns = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        try {
            for (FieldMeta fieldMeta : meta.fields) {
                Object value = fieldMeta.field.get(entity);
                if (value == null) {
                    continue;
                }
                if (fieldMeta.isId && isEmptyNumber(value)) {
                    // 自增主键无值时交给数据库生成。
                    continue;
                }
                columns.add("`" + fieldMeta.columnName + "`");
                params.add(toJdbcValue(value));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error reading entity fields for insert", e);
        }

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("No non-null fields to insert");
        }

        String sql = "INSERT INTO " + meta.tableName + " (" + join(columns) + ") VALUES (" + placeholders(columns.size())
                + ")";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setQueryTimeout(normalizeTimeout(timeoutSeconds));
            bindParams(pstmt, params);
            int affected = pstmt.executeUpdate();
            fillGeneratedId(entity, meta, pstmt);
            return affected;
        } catch (Exception e) {
            throw new RuntimeException("Error executing SQL: " + sql, e);
        }
    }

    /**
     * 批量插入同一实体类型的多条记录，单批最多 500 条，超出自动拆批。
     * <p>
     * 列集合为本批内各行的并集（按实体字段声明顺序），某行未提供的列在 SQL 中为 {@code NULL}。
     * 规则与 {@link #insert(Connection, Object, int)} 一致：跳过 {@code null}、自增主键空值跳过写入等。
     * </p>
     *
     * @return 受影响总行数（各批 {@link PreparedStatement#executeBatch()} 结果之和）
     */
    public static int insertBatch(Connection conn, List<?> entities) {
        return insertBatch(conn, entities, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static int insertBatch(Connection conn, List<?> entities, int timeoutSeconds) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        List<Object> list = new ArrayList<>();
        for (Object e : entities) {
            if (e != null) {
                list.add(e);
            }
        }
        if (list.isEmpty()) {
            return 0;
        }
        Class<?> clazz = list.get(0).getClass();
        for (Object e : list) {
            if (e.getClass() != clazz) {
                throw new IllegalArgumentException(
                        "insertBatch requires same entity class, expected " + clazz.getName() + " but got "
                                + e.getClass().getName());
            }
        }

        EntityMeta meta = MetaCacheUtil.getEntityMeta(clazz);
        int totalAffected = 0;
        for (int from = 0; from < list.size(); from += INSERT_BATCH_MAX_SIZE) {
            int to = Math.min(from + INSERT_BATCH_MAX_SIZE, list.size());
            List<Object> chunk = list.subList(from, to);
            totalAffected += insertBatchChunk(conn, meta, chunk, timeoutSeconds);
        }
        return totalAffected;
    }

    private static int insertBatchChunk(Connection conn, EntityMeta meta, List<Object> chunk, int timeoutSeconds) {
        for (Object entity : chunk) {
            autoFillTime(meta, entity, true);
        }

        List<FieldMeta> batchColumns = computeBatchInsertColumns(meta, chunk);
        if (batchColumns.isEmpty()) {
            throw new IllegalArgumentException("No insertable columns for batch (all null or empty id?)");
        }

        List<String> quotedCols = new ArrayList<>();
        for (FieldMeta fm : batchColumns) {
            quotedCols.add("`" + fm.columnName + "`");
        }
        String sql = "INSERT INTO " + meta.tableName + " (" + join(quotedCols) + ") VALUES ("
                + placeholders(batchColumns.size()) + ")";

        List<Integer> needGeneratedKeyIndex = new ArrayList<>();
        int batchAffected = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setQueryTimeout(normalizeTimeout(timeoutSeconds));
            for (int i = 0; i < chunk.size(); i++) {
                Object entity = chunk.get(i);
                if (isEmptyNumber(meta.idField.get(entity))) {
                    needGeneratedKeyIndex.add(i);
                }
                List<Object> rowParams = buildInsertRowParams(batchColumns, entity);
                bindParams(pstmt, rowParams);
                pstmt.addBatch();
            }
            int[] counts = pstmt.executeBatch();
            for (int c : counts) {
                batchAffected += (c >= 0 ? c : 0);
            }
            fillGeneratedIdsFromBatch(meta, chunk, needGeneratedKeyIndex, pstmt);
        } catch (Exception e) {
            throw new RuntimeException("Error executing batch insert: " + sql, e);
        }
        return batchAffected;
    }

    private static List<FieldMeta> computeBatchInsertColumns(EntityMeta meta, List<Object> chunk) {
        boolean[] needed = new boolean[meta.fields.size()];
        try {
            for (Object entity : chunk) {
                for (int i = 0; i < meta.fields.size(); i++) {
                    FieldMeta fm = meta.fields.get(i);
                    Object value = fm.field.get(entity);
                    if (value == null) {
                        continue;
                    }
                    if (fm.isId && isEmptyNumber(value)) {
                        continue;
                    }
                    needed[i] = true;
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error reading entity fields for batch insert", e);
        }
        List<FieldMeta> cols = new ArrayList<>();
        for (int i = 0; i < needed.length; i++) {
            if (needed[i]) {
                cols.add(meta.fields.get(i));
            }
        }
        return cols;
    }

    private static List<Object> buildInsertRowParams(List<FieldMeta> batchColumns, Object entity) {
        List<Object> row = new ArrayList<>();
        try {
            for (FieldMeta fm : batchColumns) {
                Object value = fm.field.get(entity);
                if (value == null || (fm.isId && isEmptyNumber(value))) {
                    row.add(null);
                } else {
                    row.add(toJdbcValue(value));
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error reading entity for batch insert row", e);
        }
        return row;
    }

    private static void fillGeneratedIdsFromBatch(EntityMeta meta, List<Object> chunk, List<Integer> needKeyIndices,
            PreparedStatement pstmt) {
        if (needKeyIndices.isEmpty()) {
            return;
        }
        try (ResultSet rs = pstmt.getGeneratedKeys()) {
            int k = 0;
            while (rs.next() && k < needKeyIndices.size()) {
                int chunkIndex = needKeyIndices.get(k++);
                Object generated = rs.getObject(1);
                if (generated == null) {
                    continue;
                }
                Object converted = convertNumberToTargetType(generated, meta.idField.getType());
                try {
                    meta.idField.set(chunk.get(chunkIndex), converted);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException("Error setting generated id after batch insert", ex);
                }
            }
        } catch (SQLException ignore) {
            // 驱动不支持批量回填时忽略
        }
    }

    public static int updateById(Connection conn, Object entity) {
        return updateById(conn, entity, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static int updateById(Connection conn, Object entity, int timeoutSeconds) {
        EntityMeta meta = MetaCacheUtil.getEntityMeta(entity.getClass());
        autoFillTime(meta, entity, false);

        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        Object idValue = null;

        try {
            for (FieldMeta fieldMeta : meta.fields) {
                Object value = fieldMeta.field.get(entity);
                if (fieldMeta.isId) {
                    idValue = value;
                    continue;
                }
                if (value == null) {
                    continue;
                }
                setClauses.add("`" + fieldMeta.columnName + "` = ?");
                params.add(toJdbcValue(value));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error reading entity fields for update", e);
        }

        if (idValue == null) {
            throw new IllegalArgumentException("ID value cannot be null for update");
        }
        if (setClauses.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE " + meta.tableName + " SET " + join(setClauses) + " WHERE `" + meta.idColumn + "` = ?";
        params.add(idValue);
        return executeUpdate(conn, sql, params, timeoutSeconds);
    }

    /**
     * 批量按主键更新，单批最多 500 条实体，超出自动拆批。
     * <p>
     * 与 {@link #updateById(Connection, Object, int)} 规则一致：主键必填；{@code null} 字段不参与 SET；
     * 自动填充 {@code modifyTime}（若实体存在该字段且类型支持）。批内按「待更新列集合」分组，同组共用一条
     * {@code UPDATE} 模板并 {@link PreparedStatement#addBatch()}。
     * </p>
     * <p>
     * 若某条记录在填充时间后仍无任何非主键列需要更新，则跳过该条（相当于单行 {@code updateById} 返回 0）。
     * </p>
     *
     * @return 受影响总行数
     */
    public static int updateBatch(Connection conn, List<?> entities) {
        return updateBatch(conn, entities, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static int updateBatch(Connection conn, List<?> entities, int timeoutSeconds) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        List<Object> list = new ArrayList<>();
        for (Object e : entities) {
            if (e != null) {
                list.add(e);
            }
        }
        if (list.isEmpty()) {
            return 0;
        }
        Class<?> clazz = list.get(0).getClass();
        for (Object e : list) {
            if (e.getClass() != clazz) {
                throw new IllegalArgumentException(
                        "updateBatch requires same entity class, expected " + clazz.getName() + " but got "
                                + e.getClass().getName());
            }
        }

        EntityMeta meta = MetaCacheUtil.getEntityMeta(clazz);
        int totalAffected = 0;
        for (int from = 0; from < list.size(); from += INSERT_BATCH_MAX_SIZE) {
            int to = Math.min(from + INSERT_BATCH_MAX_SIZE, list.size());
            List<Object> chunk = list.subList(from, to);
            totalAffected += updateBatchChunk(conn, meta, chunk, timeoutSeconds);
        }
        return totalAffected;
    }

    private static int updateBatchChunk(Connection conn, EntityMeta meta, List<Object> chunk, int timeoutSeconds) {
        for (Object entity : chunk) {
            autoFillTime(meta, entity, false);
        }

        Map<String, List<Object>> groups = new LinkedHashMap<>();
        Map<String, List<FieldMeta>> groupFields = new LinkedHashMap<>();

        try {
            for (Object entity : chunk) {
                Object idValue = null;
                List<FieldMeta> setFields = new ArrayList<>();
                for (FieldMeta fm : meta.fields) {
                    Object value = fm.field.get(entity);
                    if (fm.isId) {
                        idValue = value;
                        continue;
                    }
                    if (value == null) {
                        continue;
                    }
                    setFields.add(fm);
                }
                if (idValue == null) {
                    throw new IllegalArgumentException("ID value cannot be null for update (batch)");
                }
                if (setFields.isEmpty()) {
                    continue;
                }
                String sig = updateSignature(setFields);
                if (!groups.containsKey(sig)) {
                    groups.put(sig, new ArrayList<Object>());
                    groupFields.put(sig, setFields);
                }
                groups.get(sig).add(entity);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error reading entity fields for batch update", e);
        }

        int batchAffected = 0;
        for (Map.Entry<String, List<Object>> e : groups.entrySet()) {
            List<FieldMeta> setFields = groupFields.get(e.getKey());
            batchAffected += executeUpdateBatch(conn, meta, setFields, e.getValue(), timeoutSeconds);
        }
        return batchAffected;
    }

    private static String updateSignature(List<FieldMeta> setFields) {
        StringBuilder sb = new StringBuilder();
        for (FieldMeta fm : setFields) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(fm.columnName);
        }
        return sb.toString();
    }

    private static int executeUpdateBatch(Connection conn, EntityMeta meta, List<FieldMeta> setFields,
            List<Object> entities, int timeoutSeconds) {
        List<String> clauses = new ArrayList<>();
        for (FieldMeta fm : setFields) {
            clauses.add("`" + fm.columnName + "` = ?");
        }
        String sql = "UPDATE " + meta.tableName + " SET " + join(clauses) + " WHERE `" + meta.idColumn + "` = ?";
        int sum = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setQueryTimeout(normalizeTimeout(timeoutSeconds));
            for (Object entity : entities) {
                List<Object> params = new ArrayList<>();
                try {
                    for (FieldMeta fm : setFields) {
                        params.add(toJdbcValue(fm.field.get(entity)));
                    }
                    params.add(meta.idField.get(entity));
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException("Error reading entity for batch update row", ex);
                }
                bindParams(pstmt, params);
                pstmt.addBatch();
            }
            int[] counts = pstmt.executeBatch();
            for (int c : counts) {
                sum += (c >= 0 ? c : 0);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error executing batch update: " + sql, e);
        }
        return sum;
    }

    public static int deleteById(Connection conn, Class<?> clazz, Object id) {
        return deleteById(conn, clazz, id, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static int deleteById(Connection conn, Class<?> clazz, Object id, int timeoutSeconds) {
        EntityMeta meta = MetaCacheUtil.getEntityMeta(clazz);
        String sql = "DELETE FROM " + meta.tableName + " WHERE `" + meta.idColumn + "` = ?";
        List<Object> params = new ArrayList<>();
        params.add(id);
        return executeUpdate(conn, sql, params, timeoutSeconds);
    }

    /**
     * 按主键批量物理删除，单批最多 500 个 ID，超出自动拆批。
     *
     * @param ids 主键集合，{@code null} 元素会被跳过
     * @return 受影响总行数
     */
    public static int deleteBatchByIds(Connection conn, Class<?> clazz, Iterable<?> ids) {
        return deleteBatchByIds(conn, clazz, ids, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public static int deleteBatchByIds(Connection conn, Class<?> clazz, Iterable<?> ids, int timeoutSeconds) {
        if (ids == null) {
            return 0;
        }
        List<Object> idList = new ArrayList<>();
        for (Object id : ids) {
            if (id != null) {
                idList.add(id);
            }
        }
        if (idList.isEmpty()) {
            return 0;
        }
        EntityMeta meta = MetaCacheUtil.getEntityMeta(clazz);
        int totalAffected = 0;
        for (int from = 0; from < idList.size(); from += INSERT_BATCH_MAX_SIZE) {
            int to = Math.min(from + INSERT_BATCH_MAX_SIZE, idList.size());
            List<Object> chunk = idList.subList(from, to);
            totalAffected += deleteBatchByIdsChunk(conn, meta, chunk, timeoutSeconds);
        }
        return totalAffected;
    }

    private static int deleteBatchByIdsChunk(Connection conn, EntityMeta meta, List<Object> ids, int timeoutSeconds) {
        String sql = "DELETE FROM " + meta.tableName + " WHERE `" + meta.idColumn + "` IN ("
                + placeholders(ids.size()) + ")";
        return executeUpdate(conn, sql, new ArrayList<>(ids), timeoutSeconds);
    }

    private static int executeUpdate(Connection conn, String sql, List<Object> params, int timeoutSeconds) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setQueryTimeout(normalizeTimeout(timeoutSeconds));
            bindParams(pstmt, params);
            return pstmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error executing SQL: " + sql, e);
        }
    }

    private static <T> List<T> queryByColumns(Connection conn, Class<T> clazz, Map<String, Object> conditions,
            int timeoutSeconds, Integer limit) {
        EntityMeta meta = MetaCacheUtil.getEntityMeta(clazz);
        Map<String, Object> safeConditions = conditions == null ? Collections.<String, Object>emptyMap() : conditions;
        List<Object> params = new ArrayList<>();
        String whereSql = buildWhereSql(meta, safeConditions, params);

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(meta.tableName);
        if (!whereSql.isEmpty()) {
            sql.append(" WHERE ").append(whereSql);
        }
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            pstmt.setQueryTimeout(normalizeTimeout(timeoutSeconds));
            bindParams(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRow(clazz, rs, meta));
                }
                return rows;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error querying entity by conditions: " + clazz.getName(), e);
        }
    }

    private static <T> T mapRow(Class<T> clazz, ResultSet rs, EntityMeta meta) throws Exception {
        java.lang.reflect.Constructor<T> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        T entity = ctor.newInstance();
        for (FieldMeta fieldMeta : meta.fields) {
            Object value = rs.getObject(fieldMeta.columnName);
            if (value == null) {
                continue;
            }
            fieldMeta.field.set(entity, adaptValue(value, fieldMeta.field.getType()));
        }
        return entity;
    }

    private static Object adaptValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return asNumber(value).intValue();
        }
        if (targetType == Long.class || targetType == long.class) {
            return asNumber(value).longValue();
        }
        if (targetType == Double.class || targetType == double.class) {
            return asNumber(value).doubleValue();
        }
        if (targetType == Float.class || targetType == float.class) {
            return asNumber(value).floatValue();
        }
        if (targetType == Short.class || targetType == short.class) {
            return asNumber(value).shortValue();
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return asNumber(value).byteValue();
        }
        if (targetType == BigDecimal.class && value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (targetType == BigInteger.class && value instanceof Number) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime();
            }
            if (value instanceof java.util.Date) {
                return new Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
            }
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }
        if (targetType == Timestamp.class && value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime());
        }
        return value;
    }

    private static Object toJdbcValue(Object value) {
        if (value instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) value);
        }
        return value;
    }

    private static Number asNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String buildWhereSql(EntityMeta meta, Map<String, Object> conditions, List<Object> params) {
        if (conditions.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            FieldMeta fieldMeta = meta.fieldMetaByName.get(entry.getKey());
            if (fieldMeta == null) {
                throw new IllegalArgumentException(
                        "Unknown field '" + entry.getKey() + "' for table " + meta.tableName);
            }
            if (entry.getValue() == null) {
                clauses.add("`" + fieldMeta.columnName + "` IS NULL");
            } else {
                clauses.add("`" + fieldMeta.columnName + "` = ?");
                params.add(toJdbcValue(entry.getValue()));
            }
        }
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            if (i > 0) {
                where.append(" AND ");
            }
            where.append(clauses.get(i));
        }
        return where.toString();
    }

    private static void autoFillTime(EntityMeta meta, Object entity, boolean isInsert) {
        LocalDateTime now = LocalDateTime.now();
        try {
            if (isInsert && meta.createTimeField != null && meta.createTimeField.get(entity) == null) {
                Object createTimeValue = adaptTimeForField(meta.createTimeField, now);
                if (createTimeValue != null) {
                    meta.createTimeField.set(entity, createTimeValue);
                }
            }
            if (meta.modifyTimeField != null) {
                Object modifyTimeValue = adaptTimeForField(meta.modifyTimeField, now);
                if (modifyTimeValue != null) {
                    meta.modifyTimeField.set(entity, modifyTimeValue);
                }
            }
        } catch (Exception ignore) {
            // 无时间字段或类型不兼容时忽略自动填充，保持轻量容错。
        }
    }

    private static Object adaptTimeForField(Field field, LocalDateTime now) {
        Class<?> type = field.getType();
        if (type == LocalDateTime.class) {
            return now;
        }
        if (type == Timestamp.class) {
            return Timestamp.valueOf(now);
        }
        if (type == java.util.Date.class) {
            return Timestamp.valueOf(now);
        }
        return null;
    }

    private static void fillGeneratedId(Object entity, EntityMeta meta, PreparedStatement pstmt) {
        try (ResultSet rs = pstmt.getGeneratedKeys()) {
            if (!rs.next()) {
                return;
            }
            Object generatedId = rs.getObject(1);
            if (generatedId == null) {
                return;
            }
            Object converted = convertNumberToTargetType(generatedId, meta.idField.getType());
            meta.idField.set(entity, converted);
        } catch (Exception ignore) {
            // 部分数据库/驱动可能不支持 generated keys，保持降级可用。
        }
    }

    private static Object convertNumberToTargetType(Object value, Class<?> targetType) {
        if (!(value instanceof Number)) {
            return value;
        }
        Number num = (Number) value;
        if (targetType == Long.class || targetType == long.class) {
            return num.longValue();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return num.intValue();
        }
        if (targetType == Short.class || targetType == short.class) {
            return num.shortValue();
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return num.byteValue();
        }
        if (targetType == BigInteger.class) {
            return BigInteger.valueOf(num.longValue());
        }
        if (targetType == BigDecimal.class) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        return value;
    }

    private static void bindParams(PreparedStatement pstmt, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            pstmt.setObject(i + 1, params.get(i));
        }
    }

    private static boolean isEmptyNumber(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue() == 0L;
        }
        return false;
    }

    private static int normalizeTimeout(int timeoutSeconds) {
        return Math.max(0, timeoutSeconds);
    }

    private static String placeholders(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
