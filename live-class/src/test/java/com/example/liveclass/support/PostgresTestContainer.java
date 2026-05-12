// Testcontainers PostgreSQL static 인스턴스 및 @DynamicPropertySource 헬퍼 제공
package com.example.liveclass.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresTestContainer {

    public static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:16")
                    .withReuse(true);

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {
    }

    public static void applyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", INSTANCE::getUsername);
        registry.add("spring.datasource.password", INSTANCE::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
