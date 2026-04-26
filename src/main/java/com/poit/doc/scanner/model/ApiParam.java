package com.poit.doc.scanner.model;

import java.util.List;

/**
 * Method parameter metadata (path/query/body/header).
 */
public class ApiParam {

    private String name;
    private String type;
    private String paramIn;
    private String description;
    private boolean required;
    private String defaultValue;
    private ApiField bodySchema;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getParamIn() {
        return paramIn;
    }

    public void setParamIn(String paramIn) {
        this.paramIn = paramIn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public ApiField getBodySchema() {
        return bodySchema;
    }

    public void setBodySchema(ApiField bodySchema) {
        this.bodySchema = bodySchema;
    }
}
