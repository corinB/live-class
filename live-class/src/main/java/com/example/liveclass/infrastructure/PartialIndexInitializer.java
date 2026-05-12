// 부팅 시 PostgreSQL partial unique index 를 멱등하게 생성하는 ApplicationRunner
package com.example.liveclass.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Creates the partial unique index that enforces Enrollment Invariant §3:
 *   UNIQUE (class_id, classmate_id) WHERE status IN ('PENDING','CONFIRMED','WAITLISTED')
 *
 * ddl-auto: update cannot create PostgreSQL partial indexes, so we do it here at boot time.
 * The statement is idempotent via IF NOT EXISTS.
 *
 * This component is excluded from the "test" profile because H2 does not support
 * partial indexes (WHERE clause) and would throw a syntax error.
 */
@Component
@Profile("!test")
public class PartialIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PartialIndexInitializer.class);

    private static final String CREATE_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_enrollment " +
            "ON enrollments (class_id, classmate_id) " +
            "WHERE status IN ('PENDING','CONFIRMED','WAITLISTED')";

    private final DataSource dataSource;

    public PartialIndexInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_INDEX_SQL);
            log.info("Partial unique index 'uniq_active_enrollment' ensured on enrollments table");
        } catch (Exception e) {
            // H2 or other non-PostgreSQL databases may not support partial indexes.
            // Log and continue — the index is only meaningful in the PostgreSQL production environment.
            log.warn("Could not create partial unique index (non-PostgreSQL environment?): {}", e.getMessage());
        }
    }
}
