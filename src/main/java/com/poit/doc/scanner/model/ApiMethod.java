package com.poit.doc.scanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Single endpoint method metadata.
 */
public class ApiMethod {

    private String methodName;
    private String httpMethod;
    private String path;
    private String summary;
    private String description;
    private boolean deprecated;
    private List<ApiParam> parameters = new ArrayList<>();
    private ApiField responseBody;

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public List<ApiParam> getParameters() {
        return parameters;
    }

    public void setParameters(List<ApiParam> parameters) {
        this.parameters = parameters;
    }

    public ApiField getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(ApiField responseBody) {
        this.responseBody = responseBody;
    }
}
