package com.poit.doc.scanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller-level interface metadata.
 */
public class ApiInterface {

    private String className;
    private String classDescription;
    private String basePath;
    private List<ApiMethod> methods = new ArrayList<>();

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassDescription() {
        return classDescription;
    }

    public void setClassDescription(String classDescription) {
        this.classDescription = classDescription;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public List<ApiMethod> getMethods() {
        return methods;
    }

    public void setMethods(List<ApiMethod> methods) {
        this.methods = methods;
    }
}
