// ClassRepository 통합 테스트 — @Version 자동 증가를 PostgreSQL 로 검증
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
class ClassRepositoryIntegrationTest {

    @DynamicPropertySource
    static void pgProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
    }

    @Autowired
    private ClassRepository classRepository;

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
