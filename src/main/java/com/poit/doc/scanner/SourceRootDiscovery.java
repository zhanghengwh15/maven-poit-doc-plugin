package com.poit.doc.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 从指定根目录递归收集各模块 {@code src/main/java}，供 Smart-doc 作为多 {@code SourceCodePath} 使用（无需 Maven 上下文）。
 */
public final class SourceRootDiscovery {

    private static final int MAX_WALK_DEPTH = 10;

    private SourceRootDiscovery() {
    }

    /**
     * @param treeRoot 仓库或多模块根目录
     * @param debugSink 非空时输出调试信息（例如源码根路径）
     */
    public static List<String> discoverFromTree(File treeRoot, Consumer<String> debugSink) {
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        if (treeRoot != null && treeRoot.isDirectory()) {
            walkModuleTree(treeRoot, roots, MAX_WALK_DEPTH, debugSink);
        }
        return new ArrayList<>(roots);
    }

    static void walkModuleTree(File dir, Set<String> out, int depthRemaining, Consumer<String> debugSink) {
        if (depthRemaining < 0 || dir == null || !dir.isDirectory()) {
            return;
        }
        File javaRoot = new File(dir, "src" + File.separator + "main" + File.separator + "java");
        if (javaRoot.isDirectory()) {
            String abs = javaRoot.getAbsolutePath();
            if (out.add(abs) && debugSink != null) {
                debugSink.accept("自动发现源码根: " + abs);
            }
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isDirectory() || shouldSkipDirectoryName(child.getName())) {
                continue;
            }
            walkModuleTree(child, out, depthRemaining - 1, debugSink);
        }
    }

    static boolean shouldSkipDirectoryName(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        switch (name) {
            case "target":
            case ".git":
            case ".svn":
            case ".idea":
            case "node_modules":
            case "build":
                return true;
            default:
                return name.startsWith(".");
        }
    }
}
