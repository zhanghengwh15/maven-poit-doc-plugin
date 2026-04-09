package com.poit.doc.sync.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ly.doc.model.ApiParam;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 参数树 / 模型字段的确定性摘要（MD5），用于忽略源顺序检测实质性变更。
 */
public final class ApiSignatureUtils {

    private static final Gson GSON = new Gson();
    private static final Type MAP_LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {
    }.getType();

    private static final String EMPTY_MARK = "EMPTY";

    private ApiSignatureUtils() {
    }

    /**
     * 根据 ApiParam 列表生成 MD5 签名（按 field 名排序后递归规范化，忽略原顺序）。
     */
    public static String generateParamsSignature(List<ApiParam> params) {
        if (params == null || params.isEmpty()) {
            return md5(EMPTY_MARK);
        }
        String normalized = buildNormalizedString(params);
        return md5(normalized);
    }

    /**
     * path / query 参数列表的签名（与 {@link #generateParamsSignature(List)} 算法相同）。
     */
    public static String generatePathQuerySignature(List<ApiParam> params) {
        return generateParamsSignature(params);
    }

    /**
     * 模型 fields（已解析为 Map 列表，可含 ref_model_id）的确定性 MD5。
     */
    public static String md5ModelFieldsRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return md5(EMPTY_MARK);
        }
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(m -> String.valueOf(m.get("name")), Comparator.nullsLast(String::compareTo)));
        String canonical = GSON.toJson(sorted);
        return md5(canonical);
    }

    /**
     * 从 JSON 字符串解析字段列表后计算模型 MD5（入库前 ref 已替换为 ref_model_id 时调用）。
     */
    public static String md5ModelFieldsJson(String fieldsJson) {
        if (fieldsJson == null || fieldsJson.trim().isEmpty() || "[]".equals(fieldsJson.trim())) {
            return md5(EMPTY_MARK);
        }
        List<Map<String, Object>> rows = GSON.fromJson(fieldsJson, MAP_LIST_TYPE);
        if (rows == null || rows.isEmpty()) {
            return md5(EMPTY_MARK);
        }
        return md5ModelFieldsRows(rows);
    }

    private static String buildNormalizedString(List<ApiParam> params) {
        List<ApiParam> sorted = new ArrayList<>(params);
        sorted.sort(Comparator.comparing(ApiParam::getField, Comparator.nullsLast(String::compareTo)));

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (ApiParam p : sorted) {
            sb.append("{");
            sb.append("f:").append(nullToEmpty(p.getField())).append(",");
            sb.append("t:").append(nullToEmpty(p.getType())).append(",");
            sb.append("r:").append(p.isRequired()).append(",");
            // 注释改了，这个也要改
            sb.append("d:").append(p.getDesc()).append(",");
            if (p.getChildren() != null && !p.getChildren().isEmpty()) {
                sb.append("c:").append(buildNormalizedString(p.getChildren()));
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 failed", e);
        }
    }
}
