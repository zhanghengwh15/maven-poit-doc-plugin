package com.poit.doc.sync.entity;

/**
 * 模型信息，用于存储到 api_model_definition 表。
 */
public class ModelInfo {

    private final String simpleName;
    private final String description;
    private final String fieldsJson;

    public ModelInfo(String simpleName, String description, String fieldsJson) {
        this.simpleName = simpleName != null ? simpleName : "";
        this.description = description != null ? description : "";
        this.fieldsJson = fieldsJson != null ? fieldsJson : "[]";
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getDescription() {
        return description;
    }

    public String getFieldsJson() {
        return fieldsJson;
    }

    /**
     * 从全限定名提取短类名。
     */
    public static String extractSimpleName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "";
        }
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fullName.length() - 1) {
            return fullName.substring(lastDot + 1);
        }
        return fullName;
    }
}