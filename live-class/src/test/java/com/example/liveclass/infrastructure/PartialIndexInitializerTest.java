// PartialIndexInitializer 통합 테스트 — pg_indexes 시스템 뷰로 uniq_active_enrollment 인덱스 존재 확인
package com.example.liveclass.infrastructure;

import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that PartialIndexInitializer creates 'uniq_active_enrollment' on the enrollments table.
 * Uses pg_indexes (PostgreSQL-specific system view) — cannot run against H2.
 *
 * The "test" profile is NOT active here, so PartialIndexInitializer is included in the context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class PartialIndexInitializerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @RegisterExtension
    static RedisContainerExtension redisContainer = new RedisContainerExtension();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.data.redis.host", RedisContainerExtension::getHost);
        registry.add("spring.data.redis.port", RedisContainerExtension::getPort);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void partialUniqueIndex_existsAfterBoot() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'enrollments' AND indexname = 'uniq_active_enrollment'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
