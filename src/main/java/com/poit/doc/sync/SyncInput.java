package com.poit.doc.sync;

import com.poit.doc.scanner.model.ApiInterface;
import com.poit.doc.scanner.model.ApiMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level input for DocSyncService.sync(). Replaces List<ApiDoc>.
 */
public class SyncInput {

    private final List<SyncController> controllers;

    public SyncInput(List<SyncController> controllers) {
        this.controllers = controllers;
    }

    /**
     * Build from ApiScannerEngine output.
     */
    public static SyncInput from(List<ApiInterface> interfaces) {
        List<SyncController> controllers = new ArrayList<>();
        if (interfaces == null) return new SyncInput(controllers);
        for (ApiInterface iface : interfaces) {
            List<ApiMethod> methods = iface.getMethods();
            if (methods != null && !methods.isEmpty()) {
                controllers.add(new SyncController(iface, methods));
            }
        }
        return new SyncInput(controllers);
    }

    public List<SyncController> getControllers() {
        return controllers;
    }

    /**
     * Single controller with its methods.
     */
    public static class SyncController {
        private final String className;
        private final String description;
        private final String basePath;
        private final List<ApiMethod> methods;

        SyncController(ApiInterface iface, List<ApiMethod> methods) {
            this.className = iface.getClassName();
            this.description = iface.getClassDescription();
            this.basePath = iface.getBasePath();
            this.methods = methods;
        }

        public String getClassName() {
            return className;
        }

        public String getDescription() {
            return description;
        }

        public String getBasePath() {
            return basePath;
        }

        public List<ApiMethod> getMethods() {
            return methods;
        }
    }
}
