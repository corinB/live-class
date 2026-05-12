// Class 낙관적 잠금 테스트 — stale 버전으로 UPDATE 시 OptimisticLockingFailureException 발생 검증
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class ClassOptimisticLockTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private Class buildDraftClass(UUID creatorId) {
        return Class.draft(
                UserId.of(creatorId),
                "Optimistic Lock Test",
                null,
                Money.of(BigDecimal.valueOf(1000), Currency.getInstance("KRW")),
                Capacity.of(3),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(2)),
                Instant.now()
        );
    }

    @Test
    void staleSave_throwsOptimisticLockingFailureException() {
        UUID creatorId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // Persist initial entity
        UUID classId = tx.execute(status -> {
            Class c = buildDraftClass(creatorId);
            return classRepository.save(c).getId();
        });

        // Load two separate stale instances (both at version 0)
        AtomicReference<Class> staleRef = new AtomicReference<>();
        tx.execute(status -> {
            Class stale = classRepository.findById(classId).orElseThrow();
            staleRef.set(stale);
            return null;
        });

        // First update — succeeds, version becomes 1
        tx.execute(status -> {
            Class fresh = classRepository.findById(classId).orElseThrow();
            fresh.open(UserId.of(creatorId), Instant.now());
            classRepository.save(fresh);
            return null;
        });

        // Second update using the stale instance (still at version 0) — must fail
        assertThatThrownBy(() -> tx.execute(status -> {
            Class stale = staleRef.get();
            stale.open(UserId.of(creatorId), Instant.now());
            classRepository.saveAndFlush(stale);
            return null;
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // Final version in DB should be 1 (only the first update committed)
        Long finalVersion = tx.execute(status ->
                classRepository.findById(classId).orElseThrow().getVersion());
        assertThat(finalVersion).isEqualTo(1L);
    }
}
