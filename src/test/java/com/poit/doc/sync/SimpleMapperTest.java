package com.poit.doc.sync;

import com.poit.doc.sync.annotation.Column;
import com.poit.doc.sync.annotation.Id;
import com.poit.doc.sync.annotation.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimpleMapperTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:test_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_user");
            stmt.execute("CREATE TABLE t_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(64), " +
                    "age INT, " +
                    "email VARCHAR(128), " +
                    "create_time TIMESTAMP, " +
                    "modify_time TIMESTAMP)");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void should_insert_find_update_and_delete() throws Exception {
        TestUser user = new TestUser();
        user.setUsername("tom");
        user.setAge(18);
        user.setEmail("tom@example.com");

        int inserted = SimpleMapper.insert(conn, user, 3);
        assertEquals(1, inserted);
        assertNotNull(user.getId());
        assertNotNull(user.getCreateTime());
        assertNotNull(user.getModifyTime());

        TestUser fetched = SimpleMapper.findById(conn, TestUser.class, user.getId(), 3);
        assertNotNull(fetched);
        assertEquals("tom", fetched.getUsername());
        assertEquals(Integer.valueOf(18), fetched.getAge());

        Map<String, Object> queryConditions = new LinkedHashMap<>();
        queryConditions.put("username", "tom");
        queryConditions.put("age", 18);
        List<TestUser> queriedRows = SimpleMapper.findByColumns(conn, TestUser.class, queryConditions, 3);
        assertEquals(1, queriedRows.size());
        assertEquals(user.getId(), queriedRows.get(0).getId());

        TestUser queriedOne = SimpleMapper.findOneByColumns(conn, TestUser.class, queryConditions, 3);
        assertNotNull(queriedOne);
        assertEquals(user.getId(), queriedOne.getId());

        TestUser update = new TestUser();
        update.setId(user.getId());
        update.setEmail("new_email@example.com");
        int updated = SimpleMapper.updateById(conn, update, 3);
        assertEquals(1, updated);

        TestUser afterUpdate = SimpleMapper.findById(conn, TestUser.class, user.getId(), 3);
        assertNotNull(afterUpdate);
        assertEquals("tom", afterUpdate.getUsername());
        assertEquals("new_email@example.com", afterUpdate.getEmail());
        assertNotNull(afterUpdate.getModifyTime());

        int deleted = SimpleMapper.deleteById(conn, TestUser.class, user.getId(), 3);
        assertEquals(1, deleted);
        assertNull(SimpleMapper.findById(conn, TestUser.class, user.getId(), 3));
    }

    @Test
    void should_batch_insert_with_backfill_and_union_columns() throws Exception {
        TestUser u1 = new TestUser();
        u1.setUsername("batch_a");
        u1.setAge(1);
        u1.setEmail("a@batch.com");
        TestUser u2 = new TestUser();
        u2.setUsername("batch_b");
        u2.setAge(2);
        // u2 无 email，本批列并集仍含 email，该行对应列为 NULL
        TestUser u3 = new TestUser();
        u3.setUsername("batch_c");
        u3.setAge(3);
        u3.setEmail("c@batch.com");

        List<Object> batch = new ArrayList<>();
        batch.add(u1);
        batch.add(u2);
        batch.add(u3);

        int affected = SimpleMapper.insertBatch(conn, batch, 3);
        assertEquals(3, affected);
        assertNotNull(u1.getId());
        assertNotNull(u2.getId());
        assertNotNull(u3.getId());

        TestUser db2 = SimpleMapper.findById(conn, TestUser.class, u2.getId(), 3);
        assertNotNull(db2);
        assertNull(db2.getEmail());
    }

    @Test
    void should_batch_delete_by_ids() throws Exception {
        TestUser u1 = new TestUser();
        u1.setUsername("del_a");
        u1.setAge(1);
        u1.setEmail("da@test.com");
        TestUser u2 = new TestUser();
        u2.setUsername("del_b");
        u2.setAge(2);
        u2.setEmail("db@test.com");
        TestUser u3 = new TestUser();
        u3.setUsername("del_c");
        u3.setAge(3);
        u3.setEmail("dc@test.com");
        SimpleMapper.insertBatch(conn, Arrays.asList(u1, u2, u3), 3);

        int deleted = SimpleMapper.deleteBatchByIds(conn, TestUser.class, Arrays.asList(u1.getId(), u2.getId()), 3);
        assertEquals(2, deleted);
        assertNull(SimpleMapper.findById(conn, TestUser.class, u1.getId(), 3));
        assertNull(SimpleMapper.findById(conn, TestUser.class, u2.getId(), 3));
        assertNotNull(SimpleMapper.findById(conn, TestUser.class, u3.getId(), 3));
    }

    @Test
    void should_batch_update_grouped_by_column_signature() throws Exception {
        TestUser u1 = new TestUser();
        u1.setUsername("ub_a");
        u1.setAge(10);
        u1.setEmail("uba@test.com");
        TestUser u2 = new TestUser();
        u2.setUsername("ub_b");
        u2.setAge(20);
        u2.setEmail("ubb@test.com");
        SimpleMapper.insertBatch(conn, Arrays.asList(u1, u2), 3);

        TestUser p1 = new TestUser();
        p1.setId(u1.getId());
        p1.setEmail("new_uba@test.com");
        TestUser p2 = new TestUser();
        p2.setId(u2.getId());
        p2.setEmail("new_ubb@test.com");

        int affected = SimpleMapper.updateBatch(conn, Arrays.asList(p1, p2), 3);
        assertEquals(2, affected);

        TestUser d1 = SimpleMapper.findById(conn, TestUser.class, u1.getId(), 3);
        TestUser d2 = SimpleMapper.findById(conn, TestUser.class, u2.getId(), 3);
        assertNotNull(d1);
        assertNotNull(d2);
        assertEquals("new_uba@test.com", d1.getEmail());
        assertEquals("new_ubb@test.com", d2.getEmail());
        assertEquals(Integer.valueOf(10), d1.getAge());
        assertEquals(Integer.valueOf(20), d2.getAge());
    }

    @Table("t_user")
    static class TestUser {
        @Id("id")
        private Long id;
        @Column("user_name")
        private String username;
        private Integer age;
        private String email;
        private LocalDateTime createTime;
        private LocalDateTime modifyTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getModifyTime() {
            return modifyTime;
        }

        public void setModifyTime(LocalDateTime modifyTime) {
            this.modifyTime = modifyTime;
        }
    }
}
