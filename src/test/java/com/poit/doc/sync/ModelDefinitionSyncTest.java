package com.poit.doc.sync;

import com.poit.doc.sync.dataTransfer.ModelDefinitionSync;
import com.poit.doc.sync.dataTransfer.SimpleMapper;
import com.poit.doc.sync.entity.ApiModelDefinitionEntity;
import com.poit.doc.sync.entity.ModelInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2(MySQL 模式) 下验证模型拓扑插入、ref_model_id 解析与同 full名下旧版物理删除。
 */
class ModelDefinitionSyncTest {

    private static final String CHILD = "com.demo.Child";
    private static final String PARENT = "com.demo.Parent";

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:test_model_sync;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS api_model_definition");
            stmt.execute("CREATE TABLE api_model_definition (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "service_name VARCHAR(100) NOT NULL DEFAULT '', " +
                    "full_name VARCHAR(255) NOT NULL DEFAULT '', " +
                    "simple_name VARCHAR(100) NOT NULL DEFAULT '', " +
                    "description VARCHAR(255) NOT NULL DEFAULT '', " +
                    "model_md5 VARCHAR(32) NOT NULL DEFAULT '', " +
                    "fields VARCHAR(10000) NOT NULL, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "modify_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "rec_status TINYINT NOT NULL DEFAULT 1, " +
                    "UNIQUE (service_name, full_name, model_md5))");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void inserts_in_dependency_order_resolves_ref_model_id_and_deletes_old_versions()
            throws Exception {
        Map<String, ModelInfo> models = new HashMap<>();
        models.put(CHILD, new ModelInfo("Child", "c",
                "[{\"field\":\"a\",\"f\":\"a\",\"t\":\"string\"}]"));
        models.put(PARENT, new ModelInfo("Parent", "p",
                "[{\"field\":\"c\",\"f\":\"c\",\"t\":\"object\",\"ref\":\"" + CHILD + "\"}]"));

        Map<String, Long> first = ModelDefinitionSync.syncModels(conn, "svc-a", models, 5);
        Long childId = first.get(CHILD);
        Long parentId = first.get(PARENT);
        assertTrue(childId != null && childId > 0);
        assertTrue(parentId != null && parentId > 0);

        ApiModelDefinitionEntity parentRow = SimpleMapper.findById(conn, ApiModelDefinitionEntity.class, parentId, 5);
        assertTrue(parentRow.getFields().contains("\"ref_model_id\":" + childId));

        Map<String, Long> second = ModelDefinitionSync.syncModels(conn, "svc-a", models, 5);
        assertEquals(childId, second.get(CHILD));
        assertEquals(parentId, second.get(PARENT));
        assertEquals(2, countRowsWhereRecStatus(1));

        models.put(CHILD, new ModelInfo("Child", "c",
                "[{\"field\":\"a\",\"f\":\"a\",\"t\":\"int\"}]"));
        ModelDefinitionSync.syncModels(conn, "svc-a", models, 5);

        assertEquals(2, countRowsWhereRecStatus(1));
        assertEquals(0, countRowsWhereRecStatus(0));
        assertEquals(2, countAllRows());
    }

    private int countRowsWhereRecStatus(int recStatus) throws Exception {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM api_model_definition WHERE rec_status = " + recStatus)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countAllRows() throws Exception {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM api_model_definition")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
