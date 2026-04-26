package com.poit.doc.scanner.resolver;

import com.poit.doc.scanner.provider.ClassMeta;
import com.poit.doc.scanner.provider.ClassMetaProvider;
import com.poit.doc.scanner.provider.AnnotationMeta;

/**
 * Identifies Controller classes via @RestController/@Controller and applies package filters.
 */
public final class ClassFilter {

    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";

    private final ClassMetaProvider provider;
    private String packageFilter;
    private String packageExcludeFilter;

    public ClassFilter(ClassMetaProvider provider) {
        this.provider = provider;
    }

    public ClassFilter withPackageFilter(String filter) {
        this.packageFilter = filter;
        return this;
    }

    public ClassFilter withExcludeFilter(String excludeFilter) {
        this.packageExcludeFilter = excludeFilter;
        return this;
    }

    /**
     * Check if a given FQN is a Spring Controller and passes package filters.
     */
    public boolean isController(String fqn) {
        if (fqn == null) return false;
        if (!passesPackageFilter(fqn)) return false;

        var metaOpt = provider.find(fqn);
        if (metaOpt.isEmpty()) return false;

        ClassMeta meta = metaOpt.get();
        return meta.getAnnotation(REST_CONTROLLER) != null || meta.getAnnotation(CONTROLLER) != null;
    }

    private boolean passesPackageFilter(String fqn) {
        if (packageExcludeFilter != null && !packageExcludeFilter.isEmpty()) {
            String[] excludes = packageExcludeFilter.split(",");
            for (String ex : excludes) {
                String trimmed = ex.trim();
                if (!trimmed.isEmpty() && fqn.startsWith(trimmed)) {
                    return false;
                }
            }
        }
        if (packageFilter != null && !packageFilter.isEmpty()) {
            String[] filters = packageFilter.split(",");
            boolean matches = false;
            for (String f : filters) {
                String trimmed = f.trim();
                if (!trimmed.isEmpty() && fqn.startsWith(trimmed)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) return false;
        }
        return true;
    }
}
