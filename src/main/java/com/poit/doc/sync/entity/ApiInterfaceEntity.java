package com.poit.doc.sync.entity;

import com.poit.doc.sync.annotation.Id;
import com.poit.doc.sync.annotation.Table;

import java.time.LocalDateTime;

/**
 * 对应 schema.sql 的 api_interface 表。
 */
@Table("api_interface")
public class ApiInterfaceEntity {

    @Id("id")
    private Long id;
    private String serviceName;
    private String serviceVersion;
    private String env;
    private String projectName;
    private String moduleName;
    private String apiName;
    private String path;
    private String method;
    private String pathParams;
    private String queryParams;
    private Long reqBodyModelId;
    private Long resBodyModelId;
    private String reqParamsMd5;
    private String resParamsMd5;
    private Integer isDeprecated;
    private String rawInfo;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;
    private Integer recStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPathParams() {
        return pathParams;
    }

    public void setPathParams(String pathParams) {
        this.pathParams = pathParams;
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public Long getReqBodyModelId() {
        return reqBodyModelId;
    }

    public void setReqBodyModelId(Long reqBodyModelId) {
        this.reqBodyModelId = reqBodyModelId;
    }

    public Long getResBodyModelId() {
        return resBodyModelId;
    }

    public void setResBodyModelId(Long resBodyModelId) {
        this.resBodyModelId = resBodyModelId;
    }

    public String getReqParamsMd5() {
        return reqParamsMd5;
    }

    public void setReqParamsMd5(String reqParamsMd5) {
        this.reqParamsMd5 = reqParamsMd5;
    }

    public String getResParamsMd5() {
        return resParamsMd5;
    }

    public void setResParamsMd5(String resParamsMd5) {
        this.resParamsMd5 = resParamsMd5;
    }

    public Integer getIsDeprecated() {
        return isDeprecated;
    }

    public void setIsDeprecated(Integer isDeprecated) {
        this.isDeprecated = isDeprecated;
    }

    public String getRawInfo() {
        return rawInfo;
    }

    public void setRawInfo(String rawInfo) {
        this.rawInfo = rawInfo;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getModifyTime() {
        return modifyTime;
    }

    public void setModifyTime(LocalDateTime modifyTime) {
        this.modifyTime = modifyTime;
    }

    public Integer getRecStatus() {
        return recStatus;
    }

    public void setRecStatus(Integer recStatus) {
        this.recStatus = recStatus;
    }
}
