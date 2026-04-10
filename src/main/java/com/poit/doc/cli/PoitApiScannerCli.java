package com.poit.doc.cli;

import com.ly.doc.model.ApiDoc;
import com.poit.doc.scanner.SourceRootDiscovery;
import com.poit.doc.sync.ApiDocSupport;
import com.poit.doc.sync.config.SmartDocBootstrap;
import com.poit.doc.sync.config.SmartDocRunConfig;
import com.poit.doc.sync.dataTransfer.DocSyncService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 独立可执行扫描器：扫描指定目录下源码 → Smart-doc 解析 → MySQL Upsert。
 * <p>
 * 打包后：{@code java -jar poit-api-scanner-cli-*.jar scan --scan-dir=/path/to/repo ...}
 * </p>
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

    @Option(names = {"--framework"}, defaultValue = "spring", description = "Smart-doc 框架标识")
    private String framework;

    @Option(names = {"--package-filters"}, description = "包过滤，Smart-doc 语法")
    private String packageFilters;

    @Option(names = {"--package-exclude-filters"}, description = "排除包")
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

        SmartDocRunConfig cfg = new SmartDocRunConfig();
        cfg.setBaseDir(root.getAbsolutePath());
        cfg.setProjectName(displayName);
        cfg.setFramework(framework);
        if (packageFilters != null && !packageFilters.isEmpty()) {
            cfg.setPackageFilters(packageFilters);
        }
        if (packageExcludeFilters != null && !packageExcludeFilters.isEmpty()) {
            cfg.setPackageExcludeFilters(packageExcludeFilters);
        }

        if (sourcePaths != null && !sourcePaths.isEmpty()) {
            List<String> paths = new ArrayList<>();
            for (Path p : sourcePaths) {
                if (p != null) {
                    paths.add(p.toAbsolutePath().normalize().toString());
                }
            }
            if (!paths.isEmpty()) {
                cfg.setSourcePaths(paths);
            }
        } else {
            List<String> discovered = SourceRootDiscovery.discoverFromTree(root, null);
            if (!discovered.isEmpty()) {
                cfg.setSourcePaths(discovered);
            }
        }

        List<ApiDoc> roots = SmartDocBootstrap.loadApiDocs(cfg);
        List<ApiDoc> controllers = ApiDocSupport.flattenControllerDocs(roots);

        DocSyncService sync = new DocSyncService(dbUrl, dbUser, dbPassword, effectiveServiceName, serviceVersion, env,
                art);
        try {
            sync.sync(controllers);
        } catch (SQLException e) {
            System.err.println("写入数据库失败: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }

        System.out.println("同步完成，Controller 文档数: " + controllers.size());
        return 0;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
