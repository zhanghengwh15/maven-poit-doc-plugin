package com.poit.doc.sync.dataTransfer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HikariCP 数据源管理器。
 * <p>
 * 使用连接池避免每次同步都新建连接，支持超时配置防止插件卡死构建流程。
 * </p>
 */
public final class DataSourceManager {

    private static final Map<String, HikariDataSource> DATA_SOURCES = new ConcurrentHashMap<>();

    private static final int DEFAULT_POOL_SIZE = 3;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 30000; // 30秒
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000; // 30秒
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String H2_DRIVER = "org.h2.Driver";

    private DataSourceManager() {
    }

    /**
     * 获取数据源连接。
     *
     * @param jdbcUrl  JDBC URL
     * @param user     用户名
     * @param password 密码
     * @return 数据库连接
     */
    public static Connection getConnection(String jdbcUrl, String user, String password) throws SQLException {
        String key = buildKey(jdbcUrl, user);
        HikariDataSource ds = DATA_SOURCES.computeIfAbsent(key, k -> createDataSource(jdbcUrl, user, password));
        return ds.getConnection();
    }

    /**
     * 关闭所有数据源（通常在 JVM 关闭时调用）。
     */
    public static void closeAll() {
        for (HikariDataSource ds : DATA_SOURCES.values()) {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
        }
        DATA_SOURCES.clear();
    }

    private static HikariDataSource createDataSource(String jdbcUrl, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName(resolveDriverClassName(jdbcUrl));

        // 连接池配置
        config.setMaximumPoolSize(DEFAULT_POOL_SIZE);
        config.setMinimumIdle(1);

        // 超时配置（防止插件卡死构建流程）
        config.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(1800000);

        // MySQL 特定的超时设置
        if (isMySqlUrl(jdbcUrl)) {
            config.addDataSourceProperty("connectTimeout", String.valueOf(DEFAULT_CONNECTION_TIMEOUT));
            config.addDataSourceProperty("socketTimeout", String.valueOf(DEFAULT_SOCKET_TIMEOUT));
        }

        // 连接测试
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }

    private static String buildKey(String jdbcUrl, String user) {
        return jdbcUrl + "|" + user;
    }

    private static String resolveDriverClassName(String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) {
            return H2_DRIVER;
        }
        return MYSQL_DRIVER;
    }

    private static boolean isMySqlUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql:");
    }
}