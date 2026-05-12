// ClassRepository 통합 테스트 — SELECT FOR UPDATE SQL 발행 및 @Version 자동 증가를 PostgreSQL 로 검증
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.support.PostgresTestContainer;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.test.database.replace=NONE",
                "spring.jpa.properties.hibernate.session_factory.statement_inspector=" +
                "com.example.liveclass.domain.clazz.ClassRepositoryIntegrationTest$SqlCaptor"
        }
)
class ClassRepositoryIntegrationTest {

    /**
     * StatementInspector implementation that records every SQL statement so tests can
     * assert that specific SQL fragments (e.g. "for update") were issued.
     */
    public static class SqlCaptor implements StatementInspector {
        static final List<String> CAPTURED = new ArrayList<>();

        @Override
        public String inspect(String sql) {
            CAPTURED.add(sql.toLowerCase());
            return sql;
        }
    }

    @DynamicPropertySource
    static void pgProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
    }

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private TestEntityManager em;

    private Class buildDraftClass(UUID creatorId) {
        return Class.draft(
                UserId.of(creatorId),
                "Test Class",
                "description",
                Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW")),
                Capacity.of(10),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(7)),
                Instant.now()
        );
    }

    @Test
    void findByIdForUpdate_issuesSelectForUpdateSql() {
        UUID creatorId = UUID.randomUUID();
        Class saved = classRepository.saveAndFlush(buildDraftClass(creatorId));

        // Detach the persistence-context cache so findByIdForUpdate must hit the DB
        em.clear();

        SqlCaptor.CAPTURED.clear();
        classRepository.findByIdForUpdate(saved.getId());

        boolean hasForUpdate = SqlCaptor.CAPTURED.stream()
                .anyMatch(sql -> sql.contains("for update"));
        assertThat(hasForUpdate)
                .as("findByIdForUpdate should issue a SELECT ... FOR UPDATE statement")
                .isTrue();
    }

    @Test
    void version_startsAtZeroAndIncrementsOnSave() {
        UUID creatorId = UUID.randomUUID();
        Class clazz = buildDraftClass(creatorId);
        // version stays null until persist so Spring Data treats the entity as new
        assertThat(clazz.getVersion()).isNull();

        Class saved = classRepository.saveAndFlush(clazz);
        assertThat(saved.getVersion()).isEqualTo(0L);

        // trigger an update — open the class
        saved.open(UserId.of(creatorId), Instant.now());
        Class updated = classRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
    }
}
