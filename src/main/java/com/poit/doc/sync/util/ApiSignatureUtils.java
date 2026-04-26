package com.poit.doc.sync.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.poit.doc.scanner.model.ApiParam;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic MD5 summaries of param trees / model fields for change detection.
 */
public final class ApiSignatureUtils {

    private static final Gson GSON = new Gson();
    private static final Type MAP_LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {
    }.getType();

    private static final String EMPTY_mark = "EMPTY";

    private ApiSignatureUtils() {
    }

    public static String generateParamsSignature(List<ApiParam> params) {
        if (params == null || params.isEmpty()) {
            return md5(EMPTY_mark);
        }
        String normalized = buildNormalizedString(params);
        return md5(normalized);
    }

    public static String generatePathQuerySignature(List<ApiParam> params) {
        return generateParamsSignature(params);
    }

    public static String md5ModelFieldsRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return md5(EMPTY_mark);
        }
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(m -> String.valueOf(m.get("name")), Comparator.nullsLast(String::compareTo)));
        String canonical = GSON.toJson(sorted);
        return md5(canonical);
    }

    public static String md5ModelFieldsJson(String fieldsJson) {
        if (fieldsJson == null || fieldsJson.trim().isEmpty() || "[]".equals(fieldsJson.trim())) {
            return md5(EMPTY_mark);
        }
        List<Map<String, Object>> rows = GSON.fromJson(fieldsJson, MAP_LIST_TYPE);
        if (rows == null || rows.isEmpty()) {
            return md5(EMPTY_mark);
        }
        return md5ModelFieldsRows(rows);
    }

    private static String buildNormalizedString(List<ApiParam> params) {
        List<ApiParam> sorted = new ArrayList<>(params);
        sorted.sort(Comparator.comparing(ApiParam::getName, Comparator.nullsLast(String::compareTo)));

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (ApiParam p : sorted) {
            sb.append("{");
            sb.append("f:").append(nullToEmpty(p.getName())).append(",");
            sb.append("t:").append(nullToEmpty(p.getType())).append(",");
            sb.append("r:").append(p.isRequired()).append(",");
            sb.append("d:").append(nullToEmpty(p.getDescription())).append(",");
            if (p.getBodySchema() != null) {
                sb.append("ref:").append(nullToEmpty(p.getBodySchema().getRef()));
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
