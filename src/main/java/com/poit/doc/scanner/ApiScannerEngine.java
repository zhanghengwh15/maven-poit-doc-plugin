package com.poit.doc.scanner;

import com.poit.doc.scanner.merger.DocMerger;
import com.poit.doc.scanner.model.*;
import com.poit.doc.sync.SyncInput;
import com.poit.doc.scanner.provider.*;
import com.poit.doc.scanner.resolver.*;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.*;

import java.io.File;
import java.util.*;

/**
 * Main scanner entry point: replaces ApiDataBuilder.getApiData(config).
 * Scans source roots, identifies Controllers, parses methods, and returns List<ApiInterface>.
 */
public final class ApiScannerEngine {

    private final JavaProjectBuilder builder;
    private final CompositeClassMetaProvider provider;
    private final TypeResolver typeResolver;
    private final ClassFilter classFilter;
    private final MethodParser methodParser;

    public ApiScannerEngine(JavaProjectBuilder builder, ClassLoader classLoader) {
        this(builder, classLoader, null, null);
    }

    public ApiScannerEngine(JavaProjectBuilder builder, ClassLoader classLoader,
            String packageFilter, String packageExcludeFilter) {
        this.builder = builder;

        AsmClassMetaProvider asmProvider = new AsmClassMetaProvider(
                classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader());
        ReflectionClassMetaProvider refProvider = new ReflectionClassMetaProvider(
                classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader());
        this.provider = new CompositeClassMetaProvider(
                new QDoxClassMetaProvider(builder), asmProvider, refProvider);
        this.typeResolver = new TypeResolver(provider);
        this.classFilter = new ClassFilter(provider)
                .withPackageFilter(packageFilter)
                .withExcludeFilter(packageExcludeFilter);
        this.methodParser = new MethodParser(provider, typeResolver);
    }

    /**
     * Scan all loaded source roots and return sync-ready input.
     */
    public SyncInput scan() {
        List<ApiInterface> result = new ArrayList<>();

        for (JavaClass javaClass : builder.getClasses()) {
            String fqn = javaClass.getCanonicalName();
            if (!classFilter.isController(fqn)) {
                continue;
            }

            Optional<ClassMeta> metaOpt = provider.find(fqn);
            if (metaOpt.isEmpty()) continue;

            ClassMeta meta = metaOpt.get();
            ApiInterface iface = new ApiInterface();
            iface.setClassName(fqn);
            iface.setClassDescription(meta.getClassComment());
            iface.setBasePath(MethodParser.extractBasePath(meta));

            // Parse methods from QDox JavaClass
            List<ApiMethod> methods = parseMethodsFromQDox(javaClass, meta);
            iface.setMethods(methods);

            if (!methods.isEmpty()) {
                result.add(iface);
            }
        }

        return SyncInput.from(result);
    }

    private List<ApiMethod> parseMethodsFromQDox(JavaClass javaClass, ClassMeta classMeta) {
        List<ApiMethod> methods = new ArrayList<>();
        String basePath = MethodParser.extractBasePath(classMeta);

        for (JavaMethod method : javaClass.getMethods()) {
            if (!method.isPublic()) continue;

            // Find mapping annotation
            AnnotationMeta mappingAnn = findRequestMapping(method);
            if (mappingAnn == null) continue;

            String httpMethod = MethodParser.resolveHttpMethod(mappingAnn.getFqn(), mappingAnn);
            String methodPath = extractPath(mappingAnn);

            // Check if method itself is @Deprecated
            boolean deprecated = isMethodDeprecated(method);

            // Extract summary/description
            String summary = extractMethodSummary(method);
            String description = extractMethodDescription(method);

            ApiMethod apiMethod = new ApiMethod();
            apiMethod.setMethodName(method.getName());
            apiMethod.setHttpMethod(httpMethod);
            apiMethod.setPath(MethodParser.buildPath(basePath, methodPath));
            apiMethod.setSummary(summary);
            apiMethod.setDescription(description);
            apiMethod.setDeprecated(deprecated);

            // Parse parameters
            List<ApiParam> params = parseParameters(method, classMeta);
            apiMethod.setParameters(params);

            // Parse response body
            ApiField responseBody = parseResponseBody(method);
            apiMethod.setResponseBody(responseBody);

            methods.add(apiMethod);
        }

        return methods;
    }

    private AnnotationMeta findRequestMapping(JavaMethod method) {
        String[] mappingAnns = {
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping",
                "org.springframework.web.bind.annotation.RequestMapping"
        };
        for (String annFqn : mappingAnns) {
            for (JavaAnnotation ann : method.getAnnotations()) {
                if (annFqn.equals(ann.getType().getCanonicalName())) {
                    return toAnnotationMeta(ann);
                }
            }
        }
        return null;
    }

    private String extractPath(AnnotationMeta ann) {
        if (ann == null) return "";
        Object path = ann.getValue("path");
        if (path == null) path = ann.getValue("value");
        if (path == null) return "";
        return extractFirstPath(path.toString());
    }

    private String extractFirstPath(String pathStr) {
        if (pathStr.startsWith("{") && pathStr.contains("}")) {
            // Handle annotation toString format
            // Look for "path" or "value" key
            int start = pathStr.indexOf("value=");
            if (start < 0) start = pathStr.indexOf("path=");
            if (start < 0) return "";
            start += 6;
            int bracketStart = pathStr.indexOf('[', start);
            int bracketEnd = pathStr.indexOf(']', start);
            if (bracketStart >= 0 && bracketEnd > bracketStart) {
                String arr = pathStr.substring(bracketStart + 1, bracketEnd);
                int comma = arr.indexOf(',');
                if (comma > 0) {
                    return arr.substring(0, comma).trim();
                }
                return arr.trim();
            }
        }
        if (pathStr.startsWith("[")) {
            if (pathStr.endsWith("]")) {
                String inner = pathStr.substring(1, pathStr.length() - 1).trim();
                int comma = inner.indexOf(',');
                return comma > 0 ? inner.substring(0, comma).trim() : inner;
            }
        }
        return pathStr;
    }

    private boolean isMethodDeprecated(JavaMethod method) {
        for (JavaAnnotation ann : method.getAnnotations()) {
            if ("java.lang.Deprecated".equals(ann.getType().getCanonicalName())) {
                return true;
            }
        }
        return false;
    }

    private String extractMethodSummary(JavaMethod method) {
        // Try from method JavaDoc
        String comment = method.getComment();
        if (comment != null && !comment.isEmpty()) {
            int dot = comment.indexOf('.');
            if (dot > 0) return comment.substring(0, dot + 1).trim();
            int nl = comment.indexOf('\n');
            if (nl > 0) return comment.substring(0, nl).trim();
            return comment.trim();
        }
        return "";
    }

    private String extractMethodDescription(JavaMethod method) {
        String comment = method.getComment();
        if (comment != null && !comment.isEmpty()) {
            int dot = comment.indexOf('.');
            if (dot > 0 && comment.length() > dot + 1) {
                return comment.substring(dot + 1).trim();
            }
        }
        return "";
    }

    private List<ApiParam> parseParameters(JavaMethod method, ClassMeta classMeta) {
        List<ApiParam> params = new ArrayList<>();
        List<JavaParameter> qdoxParams = method.getParameters();

        // Extract javadoc @param tags
        Map<String, String> javadocParams = new HashMap<>();
        String comment = method.getComment();
        if (comment != null) {
            String[] lines = comment.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("@param")) {
                    String[] parts = line.substring(6).trim().split("\\s+", 2);
                    if (parts.length >= 2) {
                        javadocParams.put(parts[0], parts[1]);
                    }
                }
            }
        }

        for (JavaParameter param : qdoxParams) {
            JavaType type = param.getType();
            if (type == null) continue;

            MethodParser.MethodParameter mp = new MethodParser.MethodParameter(
                    param.getName(), type.getCanonicalName());

            // Determine param position from annotations
            String paramIn = resolveParamIn(param);
            mp.paramIn = paramIn;

            // Get description from javadoc
            if (javadocParams.containsKey(param.getName())) {
                mp.description = javadocParams.get(param.getName());
            }

            ApiParam apiParam = new ApiParam();
            apiParam.setName(mp.name);
            apiParam.setType(mapTypeName(mp.typeName));
            apiParam.setParamIn(mp.paramIn);
            apiParam.setDescription(mp.description);
            apiParam.setRequired(mp.required);
            apiParam.setDefaultValue(mp.defaultValue);

            // For @RequestBody, build recursive schema
            if ("body".equals(mp.paramIn) && !isBasicType(mp.typeName)) {
                ApiField schema = typeResolver.resolve(mp.typeName);
                apiParam.setBodySchema(schema);
            }

            params.add(apiParam);
        }

        return params;
    }

    private String resolveParamIn(JavaParameter param) {
        for (JavaAnnotation ann : param.getAnnotations()) {
            String fqn = ann.getType().getCanonicalName();
            if (fqn.equals("org.springframework.web.bind.annotation.PathVariable")) {
                return "path";
            }
            if (fqn.equals("org.springframework.web.bind.annotation.RequestParam")) {
                return "query";
            }
            if (fqn.equals("org.springframework.web.bind.annotation.RequestBody")) {
                return "body";
            }
            if (fqn.equals("org.springframework.web.bind.annotation.RequestHeader")) {
                return "header";
            }
        }
        // Fallback: infer from type
        String typeFqn = param.getType() != null ? param.getType().getCanonicalName() : "";
        if (isBasicType(typeFqn)) {
            return "query"; // simple types default to query
        }
        return "body"; // complex types default to body
    }

    private ApiField parseResponseBody(JavaMethod method) {
        JavaType returnType = method.getReturns();
        if (returnType == null) return null;

        String fqn = returnType.getCanonicalName();
        if (fqn == null || "void".equals(fqn)) return null;

        return typeResolver.resolve(fqn);
    }

    private AnnotationMeta toAnnotationMeta(JavaAnnotation ann) {
        String fqn = ann.getType().getCanonicalName();
        Map<String, Object> values = new LinkedHashMap<>();
        for (var entry : ann.getNamedParameterMap().entrySet()) {
            if (entry.getValue() != null) {
                Object v = entry.getValue();
                values.put(entry.getKey(), v.toString());
            }
        }
        return new AnnotationMeta(fqn, values);
    }

    private boolean isBasicType(String fqn) {
        if (fqn == null) return true;
        return fqn.equals("java.lang.String")
                || fqn.equals("int") || fqn.equals("java.lang.Integer")
                || fqn.equals("long") || fqn.equals("java.lang.Long")
                || fqn.equals("double") || fqn.equals("java.lang.Double")
                || fqn.equals("float") || fqn.equals("java.lang.Float")
                || fqn.equals("boolean") || fqn.equals("java.lang.Boolean")
                || fqn.equals("byte") || fqn.equals("java.lang.Byte")
                || fqn.equals("short") || fqn.equals("java.lang.Short")
                || fqn.equals("char") || fqn.equals("java.lang.Character")
                || fqn.equals("void") || fqn.equals("java.lang.Object");
    }

    private String mapTypeName(String fqn) {
        if (fqn == null) return "unknown";
        return switch (fqn) {
            case "java.lang.String" -> "string";
            case "int", "java.lang.Integer" -> "int";
            case "long", "java.lang.Long" -> "long";
            case "double", "java.lang.Double" -> "double";
            case "float", "java.lang.Float" -> "float";
            case "boolean", "java.lang.Boolean" -> "boolean";
            case "byte", "java.lang.Byte" -> "byte";
            case "short", "java.lang.Short" -> "short";
            default -> fqn.contains("List") || fqn.contains("Set") || fqn.contains("Collection") || fqn.contains("Array")
                    ? "array" : fqn.contains("Map") ? "object" : "object";
        };
    }
}
