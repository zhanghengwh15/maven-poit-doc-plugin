package com.poit.doc.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRootDiscoveryTest {

    @Test
    void walk_finds_nested_modules_and_skips_target(@TempDir Path temp) {
        File repo = temp.toFile();
        assertTrue(new File(repo, "api/src/main/java").mkdirs());
        assertTrue(new File(repo, "app/src/main/java").mkdirs());
        assertTrue(new File(repo, "app/target/src/main/java").mkdirs());

        Set<String> out = new LinkedHashSet<>();
        SourceRootDiscovery.walkModuleTree(repo, out, 10, null);

        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(p -> p.endsWith("api" + File.separator + "src" + File.separator + "main"
                + File.separator + "java")));
        assertTrue(out.stream().anyMatch(p -> p.endsWith("app" + File.separator + "src" + File.separator + "main"
                + File.separator + "java")));
        assertTrue(out.stream().noneMatch(p -> p.contains("target")));
    }

    @Test
    void discover_from_tree_finds_module(@TempDir Path temp) {
        File repo = temp.toFile();
        assertTrue(new File(repo, "mod-a/src/main/java").mkdirs());

        List<String> roots = SourceRootDiscovery.discoverFromTree(repo, null);
        assertEquals(1, roots.size());
        assertTrue(roots.get(0).endsWith("src" + File.separator + "main" + File.separator + "java"));
    }
}
