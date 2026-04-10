package com.poit.doc.cli;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Maven {@code pom.xml} 工具类：仅从<strong>扫描根目录</strong>下的 {@code pom.xml} 读取当前模块 {@code artifactId}
 *（{@code &lt;project&gt;} 的直接子元素，不含 {@code parent} 内）。
 */
public final class MavenPomArtifactIdResolver {

    private MavenPomArtifactIdResolver() {
    }

    /**
     * 读取 {@code scanDir/pom.xml} 中当前工程的 {@code artifactId}。
     *
     * @return artifactId；{@code scanDir/pom.xml} 不存在或解析失败时返回 {@code null}
     */
    public static String resolveFromProjectRoot(Path scanDir) {
        if (scanDir == null) {
            return null;
        }
        Path pom = scanDir.toAbsolutePath().normalize().resolve("pom.xml");
        return readDirectProjectArtifactId(pom);
    }

    /**
     * 读取指定 pom 文件中 {@code &lt;project&gt;} 的直接子元素 {@code artifactId}。
     */
    static String readDirectProjectArtifactId(Path pomFile) {
        if (pomFile == null || !Files.isRegularFile(pomFile)) {
            return null;
        }
        Document doc = parsePom(pomFile);
        if (doc == null) {
            return null;
        }
        Element project = doc.getDocumentElement();
        if (project == null) {
            return null;
        }
        Element artifactIdEl = findDirectChildElementByLocalName(project, "artifactId");
        if (artifactIdEl == null) {
            return null;
        }
        String text = artifactIdEl.getTextContent();
        return text != null ? text.trim() : null;
    }

    private static Document parsePom(Path pomFile) {
        try (InputStream in = Files.newInputStream(pomFile)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return dbf.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static Element findDirectChildElementByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) n;
            if (localName.equals(el.getLocalName()) || localName.equals(stripNs(el.getTagName()))) {
                return el;
            }
        }
        return null;
    }

    private static String stripNs(String tagName) {
        if (tagName == null) {
            return null;
        }
        int idx = tagName.indexOf(':');
        return idx >= 0 ? tagName.substring(idx + 1) : tagName;
    }
}
