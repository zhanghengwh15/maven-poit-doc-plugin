package com.poit.doc.sync;

import com.ly.doc.builder.ApiDataBuilder;
import com.ly.doc.model.ApiAllData;
import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.SourceCodePath;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * 构建 {@link ApiConfig} 并调用 Smart-doc 引擎拉取 {@link ApiDoc} 列表。
 */
public final class SmartDocBootstrap {

    private SmartDocBootstrap() {
    }

    public static List<ApiDoc> loadApiDocs(SmartDocRunConfig cfg) {
        ApiConfig config = new ApiConfig();
        config.setProjectName(cfg.getProjectName());
        config.setFramework(cfg.getFramework());
        if (cfg.getPackageFilters() != null && !cfg.getPackageFilters().isEmpty()) {
            config.setPackageFilters(cfg.getPackageFilters());
        }
        if (cfg.getPackageExcludeFilters() != null && !cfg.getPackageExcludeFilters().isEmpty()) {
            config.setPackageExcludeFilters(cfg.getPackageExcludeFilters());
        }
        config.setBaseDir(cfg.getBaseDir());

        List<SourceCodePath> paths = new ArrayList<>();
        List<String> src = cfg.getSourcePaths();
        String defaultPath = cfg.getBaseDir() + File.separator + "src" + File.separator + "main" + File.separator + "java";

        if (src == null || src.isEmpty()) {
            // 默认路径，验证是否存在
            File defaultPathFile = new File(defaultPath);
            if (defaultPathFile.exists() && defaultPathFile.isDirectory()) {
                paths.add(SourceCodePath.builder().setPath(defaultPath));
            }
        } else {
            for (String p : src) {
                if (p != null && !p.isEmpty()) {
                    paths.add(SourceCodePath.builder().setPath(p));
                }
            }
        }

        // 如果最终没有有效的源码路径，直接返回空结果
        if (paths.isEmpty()) {
            ApiAllData emptyData = new ApiAllData();
            emptyData.setApiDocList(new ArrayList<>());
            return emptyData.getApiDocList();
        }

        config.setSourceCodePaths(paths);

        try {
            ApiAllData data = ApiDataBuilder.getApiData(config);
            List<ApiDoc> list = data.getApiDocList();
            return list != null ? list : new ArrayList<>();
        } catch (RuntimeException e) {
            // 当没有找到有效的 Controller 或源码路径为空时，返回空列表
            if (e.getMessage() != null && e.getMessage().contains("can't be empty")) {
                return new ArrayList<>();
            }
            throw e;
        }
    }

    public static ClassLoader compileClasspathLoader(List<String> classpathElements, ClassLoader parent)
            throws MalformedURLException {
        if (classpathElements == null || classpathElements.isEmpty()) {
            return parent;
        }
        List<URL> urls = new ArrayList<>();
        for (String el : classpathElements) {
            if (el == null || el.isEmpty()) {
                continue;
            }
            urls.add(new File(el).toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }
}
