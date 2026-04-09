package com.poit.doc.sync.util;

import com.poit.doc.sync.annotation.Column;
import com.poit.doc.sync.annotation.Id;
import com.poit.doc.sync.annotation.Table;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体类元数据解析与缓存（按 {@link Class} 维度），与 {@link com.poit.doc.sync.SimpleMapper} 解耦。
 */
public final class MetaCacheUtil {

    private static final Map<Class<?>, EntityMeta> CACHE = new ConcurrentHashMap<>();

    private MetaCacheUtil() {
    }

    /**
     * 获取或解析并缓存实体元数据。
     */
    public static EntityMeta getEntityMeta(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz, MetaCacheUtil::parseEntity);
    }

    /**
     * 清空缓存（例如测试或热加载场景）。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private static EntityMeta parseEntity(Class<?> c) {
        Table table = c.getAnnotation(Table.class);
        if (table == null || isBlank(table.value())) {
            throw new IllegalArgumentException("Entity must declare @Table with table name: " + c.getName());
        }
        EntityMeta meta = new EntityMeta();
        meta.tableName = table.value();

        for (Field field : c.getDeclaredFields()) {
            field.setAccessible(true);
            boolean isId = field.isAnnotationPresent(Id.class);
            String columnName = resolveColumnName(field, isId);
            FieldMeta fieldMeta = new FieldMeta(field, columnName, isId);
            meta.fields.add(fieldMeta);
            meta.fieldMetaByName.put(field.getName(), fieldMeta);
            if (isId) {
                if (meta.idField != null) {
                    throw new IllegalArgumentException("Entity has multiple @Id fields: " + c.getName());
                }
                meta.idField = field;
                meta.idColumn = columnName;
            }
            if ("createTime".equals(field.getName())) {
                meta.createTimeField = field;
            }
            if ("modifyTime".equals(field.getName())) {
                meta.modifyTimeField = field;
            }
        }
        if (meta.idField == null) {
            throw new IllegalArgumentException("Entity must have @Id field: " + c.getName());
        }
        return meta;
    }

    private static String resolveColumnName(Field field, boolean isId) {
        if (isId) {
            return field.getAnnotation(Id.class).value();
        }
        if (field.isAnnotationPresent(Column.class)) {
            return field.getAnnotation(Column.class).value();
        }
        return camelToSnake(field.getName());
    }

    private static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 实体映射元数据（表名、主键、字段与列名等）。
     */
    public static final class EntityMeta {
        public String tableName;
        public Field idField;
        public String idColumn;
        public Field createTimeField;
        public Field modifyTimeField;
        public final List<FieldMeta> fields = new ArrayList<>();
        public final Map<String, FieldMeta> fieldMetaByName = new LinkedHashMap<String, FieldMeta>();
    }

    /**
     * 单个字段的列映射信息。
     */
    public static final class FieldMeta {
        public final Field field;
        public final String columnName;
        public final boolean isId;

        public FieldMeta(Field field, String columnName, boolean isId) {
            this.field = field;
            this.columnName = columnName;
            this.isId = isId;
        }
    }
}
