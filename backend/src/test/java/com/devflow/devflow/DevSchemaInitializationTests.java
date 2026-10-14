package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies that Hibernate update creates the mapped tables in an empty isolated database. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:devflow-dev-schema-test;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.flyway.enabled=false"
})
class DevSchemaInitializationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updateCreatesRequiredTablesInFreshDatabase() {
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN "
                + "('USER', 'PROJECT', 'SPRINT', 'ISSUE', 'COMMENT', 'AUDIT_LOG', 'TEST_EXECUTION', 'NOTIFICATION')",
            Integer.class);

        assertEquals(8, tableCount);
    }
}
