package com.poit.doc.sync.entity;

import com.poit.doc.sync.annotation.Id;
import com.poit.doc.sync.annotation.Table;

import java.time.LocalDateTime;

/**
 * 对应 schema.sql 的 api_model_definition 表。
 */
@Table("api_model_definition")
public class ApiModelDefinitionEntity {

    @Id("id")
    private Long id;
    private String serviceName;
    private String fullName;
    private String simpleName;
    private String description;
    private String modelMd5;
    private String fields;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModelMd5() {
        return modelMd5;
    }

    public void setModelMd5(String modelMd5) {
        this.modelMd5 = modelMd5;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
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
