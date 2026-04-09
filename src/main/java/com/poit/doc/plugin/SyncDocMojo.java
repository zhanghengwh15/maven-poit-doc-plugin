package com.poit.doc.plugin;

import com.ly.doc.model.ApiDoc;
import com.poit.doc.sync.ApiDocSupport;
import com.poit.doc.sync.dataTransfer.DocSyncService;
import com.poit.doc.sync.SmartDocBootstrap;
import com.poit.doc.sync.SmartDocRunConfig;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.List;

/**
 * 调用 Smart-doc 解析当前工程源码，并将接口与模型定义同步至 MySQL。
 * <p>
 * 执行：{@code mvn com.poit.doc:maven-poit-doc-plugin:1.0.0-SNAPSHOT:sync} 或配置 goalPrefix 后
 * {@code mvn my-doc-plugin:sync}。
 * </p>
 */
@Mojo(name = "sync", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class SyncDocMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "poit.doc.serviceName", required = true)
    private String serviceName;

    @Parameter(property = "poit.doc.serviceVersion", required = true)
    private String serviceVersion;

    @Parameter(property = "poit.doc.env", required = true)
    private String env;

    @Parameter(property = "poit.doc.dbUrl", required = true, alias = "dbUrl")
    private String dbUrl;

    @Parameter(property = "poit.doc.dbUser", required = true, alias = "dbUser")
    private String dbUser;

    @Parameter(property = "poit.doc.dbPassword", required = true, alias = "dbPassword")
    private String dbPassword;

    @Parameter(property = "poit.doc.framework", defaultValue = "spring")
    private String framework;

    @Parameter(property = "poit.doc.packageFilters")
    private String packageFilters;

    @Parameter(property = "poit.doc.packageExcludeFilters")
    private String packageExcludeFilters;

    @Parameter
    private List<String> sourcePaths;

    @Parameter(property = "poit.doc.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("poit.doc.skip=true，跳过文档同步");
            return;
        }
        SmartDocRunConfig cfg = new SmartDocRunConfig();
        cfg.setProjectName(project.getName());
        cfg.setBaseDir(project.getBasedir().getAbsolutePath());
        cfg.setSourcePaths(sourcePaths);
        cfg.setFramework(framework);
        cfg.setPackageFilters(packageFilters);
        cfg.setPackageExcludeFilters(packageExcludeFilters);
        try {
            cfg.setProjectClassLoader(SmartDocBootstrap.compileClasspathLoader(project.getCompileClasspathElements(),
                    Thread.currentThread().getContextClassLoader()));
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("请先执行到至少 compile 阶段以解析依赖（requiresDependencyResolution=compile）", e);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("无法构建编译期 ClassLoader", e);
        }

        List<ApiDoc> roots;
        try {
            roots = SmartDocBootstrap.loadApiDocs(cfg);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Smart-doc 解析失败", e);
        }

        List<ApiDoc> controllers = ApiDocSupport.flattenControllerDocs(roots);
        getLog().info("Smart-doc 解析完成，待同步 Controller 文档数: " + controllers.size());

        DocSyncService syncService = new DocSyncService(dbUrl, dbUser, dbPassword, serviceName, serviceVersion, env,
                project.getArtifactId());
        try {
            syncService.sync(controllers);
        } catch (SQLException e) {
            throw new MojoExecutionException("文档写入数据库失败: " + e.getMessage(), e);
        }
        getLog().info("接口文档已同步至数据库");
    }
}
