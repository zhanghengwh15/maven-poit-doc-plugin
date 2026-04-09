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
    private Integer isDeprecated;
    private String reqModelRef;
    private String resModelRef;
    private String rawInfo;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;
    private Integer recStatus;
    private Long createBy;
    private Long modifyBy;

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

    public Integer getIsDeprecated() {
        return isDeprecated;
    }

    public void setIsDeprecated(Integer isDeprecated) {
        this.isDeprecated = isDeprecated;
    }

    public String getReqModelRef() {
        return reqModelRef;
    }

    public void setReqModelRef(String reqModelRef) {
        this.reqModelRef = reqModelRef;
    }

    public String getResModelRef() {
        return resModelRef;
    }

    public void setResModelRef(String resModelRef) {
        this.resModelRef = resModelRef;
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

    public Long getCreateBy() {
        return createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    public Long getModifyBy() {
        return modifyBy;
    }

    public void setModifyBy(Long modifyBy) {
        this.modifyBy = modifyBy;
    }
}
