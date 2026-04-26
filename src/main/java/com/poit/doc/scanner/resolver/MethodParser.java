package com.poit.doc.scanner.resolver;

import com.poit.doc.scanner.model.ApiField;
import com.poit.doc.scanner.model.ApiMethod;
import com.poit.doc.scanner.model.ApiParam;
import com.poit.doc.scanner.provider.*;

import java.util.*;

/**
 * Parses Controller methods into ApiMethod metadata.
 * Reads @RequestMapping series annotations, HTTP method, path, deprecated, summary.
 */
public final class MethodParser {

    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private static final String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
    private static final String PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";

    private static final String DEPRECATED = "java.lang.Deprecated";

    private final CompositeClassMetaProvider provider;
    private final TypeResolver typeResolver;

    public MethodParser(CompositeClassMetaProvider provider, TypeResolver typeResolver) {
        this.provider = provider;
        this.typeResolver = typeResolver;
    }

    /**
     * Parse all public methods of a controller class into ApiMethod list.
     */
    public List<ApiMethod> parseMethods(ClassMeta controllerMeta, String basePath) {
        // We need the QDox JavaClass to get method details
        // This method works with the ClassMeta we have
        List<ApiMethod> methods = new ArrayList<>();

        // In QDox, we can get method info from the JavaClass
        // For ASM/Reflection, we'd need additional method parsing
        // For now, we store method data in ClassMeta during construction
        // and use it here

        return methods;
    }

    /**
     * Parse a single method given its metadata.
     */
    public ApiMethod parseMethod(String methodName, String httpMethod, String methodPath,
            String summary, String description, boolean deprecated,
            List<MethodParameter> parameters, String returnType, ClassMeta controllerMeta) {
        ApiMethod method = new ApiMethod();
        method.setMethodName(methodName);
        method.setHttpMethod(httpMethod);
        method.setPath(buildPath(controllerMeta != null ? extractBasePath(controllerMeta) : "", methodPath));
        method.setSummary(summary);
        method.setDescription(description);
        method.setDeprecated(deprecated);
        return method;
    }

    /**
     * Resolve the HTTP method from a mapping annotation.
     */
    public static String resolveHttpMethod(String mappingFqn, AnnotationMeta meta) {
        return switch (mappingFqn) {
            case GET_MAPPING -> "GET";
            case POST_MAPPING -> "POST";
            case PUT_MAPPING -> "PUT";
            case DELETE_MAPPING -> "DELETE";
            case PATCH_MAPPING -> "PATCH";
            case REQUEST_MAPPING -> {
                if (meta != null && meta.hasValue("method")) {
                    Object m = meta.getValue("method");
                    yield resolveRequestMethod(m);
                }
                yield "GET"; // default
            }
            default -> "GET";
        };
    }

    private static String resolveRequestMethod(Object methodValue) {
        if (methodValue == null) return "GET";
        if (methodValue instanceof String s) {
            return s.contains("/") ? "GET" : s;
        }
        // For enum values like RequestMethod.GET
        String str = methodValue.toString();
        if (str.contains("GET")) return "GET";
        if (str.contains("POST")) return "POST";
        if (str.contains("PUT")) return "PUT";
        if (str.contains("DELETE")) return "DELETE";
        if (str.contains("PATCH")) return "PATCH";
        return "GET";
    }

    /**
     * Extract base path from controller's @RequestMapping.
     */
    public static String extractBasePath(ClassMeta meta) {
        AnnotationMeta rm = meta.getAnnotation(REQUEST_MAPPING);
        if (rm == null) return "";
        Object path = rm.getValue("value");
        if (path == null) path = rm.getValue("path");
        if (path == null) return "";
        String p = path.toString();
        return p.startsWith("[") ? extractFirstArrayElement(p) : p;
    }

    /**
     * Extract first element from array-like string representation.
     */
    private static String extractFirstArrayElement(String arr) {
        // Handle formats like "[/api/users]" or "value1"
        if (arr.startsWith("[") && arr.endsWith("]")) {
            String inner = arr.substring(1, arr.length() - 1).trim();
            int comma = inner.indexOf(',');
            if (comma > 0) {
                return inner.substring(0, comma).trim();
            }
            return inner;
        }
        return arr;
    }

    /**
     * Build full path by combining base and method path.
     */
    public static String buildPath(String basePath, String methodPath) {
        String base = normalizePath(basePath);
        String method = normalizePath(methodPath);
        if (base.isEmpty()) return method.isEmpty() ? "/" : method;
        if (method.isEmpty()) return base;
        return base + method;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "";
        if (!path.startsWith("/")) path = "/" + path;
        return path;
    }

    /**
     * Represents a parsed method parameter.
     */
    public static class MethodParameter {
        public String name;
        public String typeName;
        public String paramIn; // path, query, body, header
        public String description;
        public boolean required;
        public String defaultValue;

        public MethodParameter(String name, String typeName) {
            this.name = name;
            this.typeName = typeName;
        }
    }
}
