package com.poit.doc.sync;

import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiMethodDoc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocSyncServiceTest {

    private final Properties props = loadTestProps();

    @BeforeEach
    void setUpSchema() throws Exception {
        try (Connection conn = newConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS api_interface");
            st.execute("DROP TABLE IF EXISTS api_model_definition");

            st.execute("CREATE TABLE api_interface (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "service_name VARCHAR(100) NOT NULL," +
                    "service_version VARCHAR(20) NOT NULL," +
                    "env VARCHAR(20) NOT NULL," +
                    "module_name VARCHAR(100) NOT NULL," +
                    "api_name VARCHAR(255) NOT NULL," +
                    "path VARCHAR(255) NOT NULL," +
                    "method VARCHAR(20) NOT NULL," +
                    "req_model_ref VARCHAR(255) NOT NULL," +
                    "res_model_ref VARCHAR(255) NOT NULL," +
                    "raw_info VARCHAR(4096)," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "modify_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(service_name, env, path, method))");

            st.execute("CREATE TABLE api_model_definition (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "full_name VARCHAR(255) NOT NULL," +
                    "simple_name VARCHAR(100) NOT NULL," +
                    "description VARCHAR(255) NOT NULL," +
                    "fields VARCHAR(4096) NOT NULL," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "modify_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(full_name))");
        }
    }

    @AfterEach
    void cleanDataSource() {
        DataSourceManager.closeAll();
    }

    @Test
    void should_upsert_interface_when_sync_called_twice() throws SQLException {
        ApiMethodDoc methodDoc = Mockito.mock(ApiMethodDoc.class);
        Mockito.when(methodDoc.getPath()).thenReturn("/users/{id}");
        Mockito.when(methodDoc.getUrl()).thenReturn(null);
        Mockito.when(methodDoc.getType()).thenReturn("GET");
        Mockito.when(methodDoc.getName()).thenReturn("查询用户", "查询用户-更新");
        Mockito.when(methodDoc.getMethodId()).thenReturn("m-1");
        Mockito.when(methodDoc.getContentType()).thenReturn("application/json");
        Mockito.when(methodDoc.getHeaders()).thenReturn(null);
        Mockito.when(methodDoc.getPathParams()).thenReturn(null);
        Mockito.when(methodDoc.getQueryParams()).thenReturn(null);
        Mockito.when(methodDoc.getRequestParams()).thenReturn(null);
        Mockito.when(methodDoc.getResponseParams()).thenReturn(null);
        Mockito.when(methodDoc.getIsRequestArray()).thenReturn(null);
        Mockito.when(methodDoc.getRequestArrayType()).thenReturn(null);
        Mockito.when(methodDoc.getIsResponseArray()).thenReturn(null);
        Mockito.when(methodDoc.getResponseArrayType()).thenReturn(null);

        ApiDoc apiDoc = Mockito.mock(ApiDoc.class);
        Mockito.when(apiDoc.getDesc()).thenReturn("用户模块");
        Mockito.when(apiDoc.getList()).thenReturn(Collections.singletonList(methodDoc));

        DocSyncService service = new DocSyncService(
                props.getProperty("jdbc.url"),
                props.getProperty("jdbc.user"),
                props.getProperty("jdbc.password"),
                props.getProperty("service.name"),
                props.getProperty("service.version"),
                props.getProperty("env"));

        service.sync(Collections.singletonList(apiDoc));
        service.sync(Collections.singletonList(apiDoc));

        try (Connection conn = newConnection();
             Statement st = conn.createStatement()) {
            ResultSet rsCount = st.executeQuery("SELECT COUNT(*) FROM api_interface");
            rsCount.next();
            assertEquals(1, rsCount.getInt(1), "同一个唯一键应只保留一条记录");
            rsCount.close();

            ResultSet rsName = st.executeQuery("SELECT api_name FROM api_interface " +
                    "WHERE service_name='test-service' AND env='test' AND path='/users/{id}' AND method='GET'");
            rsName.next();
            assertEquals("查询用户-更新", rsName.getString(1), "重复同步应触发 upsert 更新 api_name");
            rsName.close();
        }
    }

    private Connection newConnection() throws SQLException {
        return DriverManager.getConnection(
                props.getProperty("jdbc.url"),
                props.getProperty("jdbc.user"),
                props.getProperty("jdbc.password"));
    }

    private static Properties loadTestProps() {
        Properties properties = new Properties();
        try (InputStream in = DocSyncServiceTest.class.getClassLoader().getResourceAsStream("doc-sync-test.properties")) {
            Objects.requireNonNull(in, "doc-sync-test.properties 未找到");
            properties.load(in);
            return properties;
        } catch (Exception e) {
            throw new IllegalStateException("加载测试配置失败", e);
        }
    }
}
