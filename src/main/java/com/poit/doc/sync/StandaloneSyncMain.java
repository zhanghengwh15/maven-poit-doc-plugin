package com.poit.doc.sync;

import com.ly.doc.model.ApiDoc;
import com.poit.doc.sync.dataTransfer.DocSyncService;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 可执行入口：{@code java -jar ...standalone.jar --project=/path/to/module ...}
 * <p>需使用 {@code mvn -Pstandalone package} 生成带依赖的 shaded JAR。</p>
 */
public final class StandaloneSyncMain {

    private StandaloneSyncMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parseArgs(args);
        String project = require(opt, "project");
        String dbUrl = require(opt, "dbUrl");
        String dbUser = require(opt, "dbUser");
        String dbPassword = require(opt, "dbPassword");
        String svc = require(opt, "serviceName");
        String ver = require(opt, "serviceVersion");
        String env = require(opt, "env");

        SmartDocRunConfig cfg = new SmartDocRunConfig();
        cfg.setBaseDir(new File(project).getAbsolutePath());
        cfg.setProjectName(opt.getOrDefault("projectname", new File(project).getName()));
        cfg.setFramework(opt.getOrDefault("framework", "spring"));
        if (opt.containsKey("packageFilters")) {
            cfg.setPackageFilters(opt.get("packageFilters"));
        }
        if (opt.containsKey("packageExcludeFilters")) {
            cfg.setPackageExcludeFilters(opt.get("packageExcludeFilters"));
        }

        List<ApiDoc> roots = SmartDocBootstrap.loadApiDocs(cfg);
        List<ApiDoc> controllers = ApiDocSupport.flattenControllerDocs(roots);
        DocSyncService sync = new DocSyncService(dbUrl, dbUser, dbPassword, svc, ver, env,
                opt.getOrDefault("projectname", new File(project).getName()));
        sync.sync(controllers);
        System.out.println("同步完成，Controller 文档数: " + controllers.size());
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        if (args == null) {
            return m;
        }
        for (String a : args) {
            if (a == null || !a.startsWith("--")) {
                continue;
            }
            String body = a.substring(2);
            int eq = body.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = body.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = body.substring(eq + 1);
            m.put(k, v);
        }
        return m;
    }

    private static String require(Map<String, String> m, String key) {
        String v = m.get(key.toLowerCase(Locale.ROOT));
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("缺少参数: --" + key + "=...");
        }
        return v;
    }
}
