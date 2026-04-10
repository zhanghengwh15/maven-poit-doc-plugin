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

    @Option(names = {"--service-name"}, required = true, description = "服务名，写入库表")
    private String serviceName;

    @Option(names = {"--service-version"}, required = true)
    private String serviceVersion;

    @Option(names = {"--env"}, required = true, description = "环境，如 dev / test / prod")
    private String env;

    @Option(names = {"--project-name"}, description = "展示用项目名，默认取扫描目录名")
    private String projectName;

    @Option(names = {"--artifact-id"}, description = "写入库时使用的 artifactId，默认与项目名相同")
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
        File root = scanDir.toAbsolutePath().normalize().toFile();
        if (!root.isDirectory()) {
            System.err.println("扫描目录不存在或不是目录: " + root);
            return 2;
        }

        String displayName = projectName != null && !projectName.isEmpty()
                ? projectName
                : root.getName();
        String art = artifactId != null && !artifactId.isEmpty() ? artifactId : displayName;

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

        DocSyncService sync = new DocSyncService(dbUrl, dbUser, dbPassword, serviceName, serviceVersion, env, art);
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
}
