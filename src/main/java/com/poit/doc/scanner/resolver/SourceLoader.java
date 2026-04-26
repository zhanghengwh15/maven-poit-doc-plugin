package com.poit.doc.scanner.resolver;

import com.poit.doc.scanner.SourceRootDiscovery;
import com.thoughtworks.qdox.JavaProjectBuilder;

import java.io.File;
import java.util.List;

/**
 * Loads source roots and feeds them into a QDox JavaProjectBuilder.
 */
public final class SourceLoader {

    private final JavaProjectBuilder builder = new JavaProjectBuilder();

    /**
     * Discover source roots from a directory tree and add them to the builder.
     */
    public SourceLoader fromTree(File rootDir) {
        List<String> roots = SourceRootDiscovery.discoverFromTree(rootDir, null);
        for (String r : roots) {
            builder.addSourceTree(new File(r));
        }
        return this;
    }

    /**
     * Add explicit source roots.
     */
    public SourceLoader fromRoots(List<String> rootPaths) {
        for (String p : rootPaths) {
            builder.addSourceTree(new File(p));
        }
        return this;
    }

    public JavaProjectBuilder getBuilder() {
        return builder;
    }
}
