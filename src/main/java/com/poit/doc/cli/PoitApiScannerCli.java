package com.poit.doc.cli;

import com.poit.doc.scanner.ApiScannerEngine;
import com.poit.doc.scanner.SourceRootDiscovery;
import com.poit.doc.sync.SyncInput;
import com.poit.doc.sync.dataTransfer.DocSyncService;
import com.thoughtworks.qdox.JavaProjectBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Standalone CLI: scan Java source code for API definitions and sync to MySQL.
 * Uses QDox/ASM-based scanner (no Smart-doc dependency).
 */
@Command(
        name = "poit-api-scanner",
        mixinStandardHelpOptions = true,
        version = "poit-api-scanner-cli 1.0.0-SNAPSHOT",
        description = "扫描 Java 源码中的 API 与模型定义并写入 MySQL")
public final class PoitApiScannerCli implements Callable<Integer> {

    @Option(
            names = {"--scan-dir"},
            required = true,
            description = "扫描根目录（递归发现各模块下的 src/main/java）")
    private Path scanDir;

    @Option(names = {"--db-url"}, required = true, description = "JDBC URL（MySQL）")
    private String dbUrl;

    @Option(names = {"--db-user"}, required = true)
    private String dbUser;

    @Option(names = {"--db-password"}, required = true)
    private String dbPassword;

    @Option(
            names = {"--service-name"},
            description = "服务名，写入库表；省略时从 --scan-dir/pom.xml 读取 artifactId（须为 Maven 工程根目录）")
    private String serviceName;

    @Option(names = {"--service-version"}, required = true)
    private String serviceVersion;

    @Option(names = {"--env"}, required = true, description = "环境，如 dev / test / prod")
    private String env;

    @Option(names = {"--project-name"}, description = "展示用项目名，默认取扫描目录名")
    private String projectName;

    @Option(
            names = {"--artifact-id"},
            description = "写入库时使用的 artifactId；省略时从 --scan-dir/pom.xml 读取 artifactId（须为 Maven 工程根目录）")
    private String artifactId;

    @Option(names = {"--package-filters"}, description = "包过滤（逗号分隔的包前缀）")
    private String packageFilters;

    @Option(names = {"--package-exclude-filters"}, description = "排除包（逗号分隔的包前缀）")
    private String packageExcludeFilters;

    @Option(
            names = {"--source-path"},
            description = "显式源码根，可多次指定；若指定则不再自动发现",
            arity = "0..*")
    private List<Path> sourcePaths;

    public static void main(String[] args) {
        int code = new CommandLine(new PoitApiScannerCli()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() throws Exception {
        Path rootPath = scanDir.toAbsolutePath().normalize();
        File root = rootPath.toFile();
        if (!root.isDirectory()) {
            System.err.println("扫描目录不存在或不是目录: " + root);
            return 2;
        }

        boolean needPomArtifactId = !nonBlank(serviceName) || !nonBlank(artifactId);
        Path pomPath = rootPath.resolve("pom.xml");
        String pomArtifactId = null;
        if (needPomArtifactId) {
            if (!Files.isRegularFile(pomPath)) {
                System.err.println(
                        "--scan-dir 下未找到 pom.xml，无法作为 Maven/Java 工程识别；请将 --scan-dir 指向工程根目录，或显式传入 --service-name 与 --artifact-id。");
                return 2;
            }
            pomArtifactId = MavenPomArtifactIdResolver.resolveFromProjectRoot(rootPath);
            if (!nonBlank(pomArtifactId)) {
                System.err.println("无法从 pom.xml 解析 artifactId，请检查文件内容或显式指定 --service-name / --artifact-id。");
                return 2;
            }
        }

        String displayName = projectName != null && !projectName.isEmpty()
                ? projectName
                : root.getName();

        String effectiveServiceName = nonBlank(serviceName) ? serviceName.trim() : pomArtifactId;
        if (!nonBlank(effectiveServiceName)) {
            System.err.println("未指定 --service-name，且未能从 pom.xml 得到 artifactId。");
            return 2;
        }

        String art = nonBlank(artifactId) ? artifactId.trim() : pomArtifactId;
        if (!nonBlank(art)) {
            System.err.println("未指定 --artifact-id，且未能从 pom.xml 得到 artifactId。");
            return 2;
        }

        // Build source path list
        List<String> effectiveSourcePaths;
        if (sourcePaths != null && !sourcePaths.isEmpty()) {
            effectiveSourcePaths = new ArrayList<>();
            for (Path p : sourcePaths) {
                if (p != null) {
                    effectiveSourcePaths.add(p.toAbsolutePath().normalize().toString());
                }
            }
        } else {
            List<String> discovered = SourceRootDiscovery.discoverFromTree(root, null);
            effectiveSourcePaths = filterApiAndAppModules(discovered);
        }

        // Build QDox JavaProjectBuilder
        JavaProjectBuilder builder = new JavaProjectBuilder();
        for (String p : effectiveSourcePaths) {
            File dir = new File(p);
            if (dir.isDirectory()) {
                builder.addSourceTree(dir);
            }
        }

        // Run scanner
        ClassLoader classLoader = buildClassLoader();
        ApiScannerEngine engine = new ApiScannerEngine(builder, classLoader,
                packageFilters, packageExcludeFilters);

        SyncInput input = engine.scan();
        System.out.println("扫描完成，Controller 数: " + input.getControllers().size());

        // Sync to database
        DocSyncService sync = new DocSyncService(dbUrl, dbUser, dbPassword, effectiveServiceName, serviceVersion, env,
                art);
        try {
            sync.sync(input);
        } catch (SQLException e) {
            System.err.println("写入数据库失败: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }

        System.out.println("同步完成");
        return 0;
    }

    private static ClassLoader buildClassLoader() {
        try {
            List<URL> urls = new ArrayList<>();
            String classpath = System.getProperty("java.class.path");
            if (classpath != null) {
                for (String el : classpath.split(File.pathSeparator)) {
                    if (!el.isEmpty()) {
                        urls.add(new File(el).toURI().toURL());
                    }
                }
            }
            return new URLClassLoader(urls.toArray(new URL[0]),
                    Thread.currentThread().getContextClassLoader());
        } catch (Exception e) {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * Filter source paths to keep only -api or -app modules.
     */
    private static List<String> filterApiAndAppModules(List<String> sourcePaths) {
        if (sourcePaths == null || sourcePaths.isEmpty()) {
            return sourcePaths;
        }
        List<String> filtered = new ArrayList<>();
        for (String path : sourcePaths) {
            if (isApiOrAppModule(path)) {
                filtered.add(path);
            }
        }
        return filtered;
    }

    /**
     * Check if a path belongs to an -api or -app module.
     */
    private static boolean isApiOrAppModule(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String normalizedPath = path.replace("\\", "/");
        String[] segments = normalizedPath.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if ("src".equals(segments[i])) {
                if (i > 0) {
                    String moduleName = segments[i - 1];
                    return moduleName.endsWith("-api") || moduleName.endsWith("-app");
                }
                break;
            }
        }
        return false;
    }
}
