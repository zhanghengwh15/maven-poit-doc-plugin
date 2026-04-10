package com.poit.doc.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MavenPomArtifactIdResolverTest {

    @Test
    void readsArtifactIdFromScanDirPom(@TempDir Path tmp) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <parent>\n"
                + "    <groupId>org.springframework.boot</groupId>\n"
                + "    <artifactId>spring-boot-starter-parent</artifactId>\n"
                + "    <version>3.0.0</version>\n"
                + "  </parent>\n"
                + "  <artifactId>my-app</artifactId>\n"
                + "  <version>1.0.0</version>\n"
                + "</project>\n";
        Files.write(tmp.resolve("pom.xml"), xml.getBytes(StandardCharsets.UTF_8));

        assertEquals("my-app", MavenPomArtifactIdResolver.readDirectProjectArtifactId(tmp.resolve("pom.xml")));
        assertEquals("my-app", MavenPomArtifactIdResolver.resolveFromProjectRoot(tmp));
    }

    @Test
    void onlyScanDirNotParentDirectory(@TempDir Path tmp) throws Exception {
        Path module = tmp.resolve("module");
        Path deep = module.resolve("src").resolve("main");
        Files.createDirectories(deep);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <artifactId>mod-artifact</artifactId>\n"
                + "  <version>1</version>\n"
                + "</project>\n";
        Files.write(module.resolve("pom.xml"), xml.getBytes(StandardCharsets.UTF_8));

        assertNull(MavenPomArtifactIdResolver.resolveFromProjectRoot(deep));
        assertEquals("mod-artifact", MavenPomArtifactIdResolver.resolveFromProjectRoot(module));
    }

    @Test
    void missingPomInScanDirReturnsNull(@TempDir Path tmp) throws Exception {
        Path empty = tmp.resolve("empty");
        Files.createDirectories(empty);
        assertNull(MavenPomArtifactIdResolver.resolveFromProjectRoot(empty));
    }
}
