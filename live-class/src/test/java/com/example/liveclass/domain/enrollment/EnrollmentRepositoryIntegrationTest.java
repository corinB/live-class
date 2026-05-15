// EnrollmentRepository 통합 테스트 — Testcontainers PostgreSQL + partial unique index 검증
package com.example.liveclass.domain.enrollment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.liveclass.support.PostgresTestContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for EnrollmentRepository using a real PostgreSQL instance via Testcontainers.
 * H2 does not support partial indexes (WHERE clause), so PostgreSQL is mandatory here.
 *
 * The partial unique index 'uniq_active_enrollment' is created by PartialIndexInitializer,
 * which is excluded from the "test" profile. We create it here via an @BeforeEach SQL call
 * through the JdbcTemplate supplied by @DataJpaTest.
 */
@DataJpaTest(properties = "spring.test.database.replace=NONE")
class EnrollmentRepositoryIntegrationTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
    }

    @Autowired
    private EnrollmentRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID classId;
    private UUID classmateId;

    @BeforeEach
    void setUp() {
        classId = UUID.randomUUID();
        classmateId = UUID.randomUUID();

        // Create the partial unique index that PartialIndexInitializer would normally create.
        // It is excluded from the test profile, so we apply it manually here.
        entityManager.createNativeQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_enrollment " +
                "ON enrollments (class_id, classmate_id) " +
                "WHERE status IN ('PENDING','CONFIRMED','WAITLISTED')"
        ).executeUpdate();
        entityManager.flush();
    }

    @Test
    void duplicateActive_throwsDataIntegrityViolation() {
        // First PENDING enrollment — must succeed
        Enrollment first = Enrollment.apply(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(classmateId),
                Instant.now());
        repository.saveAndFlush(first);

        // Second PENDING enrollment for the same (classId, classmateId) — must violate partial unique index
        Enrollment second = Enrollment.apply(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(classmateId),
                Instant.now().plusMillis(100));

        assertThatThrownBy(() -> repository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledPlusNewPending_samePair_succeeds() {
        // Insert PENDING, then cancel it
        Enrollment e = Enrollment.apply(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(classmateId),
                Instant.now());
        repository.saveAndFlush(e);
        e.cancel(Instant.now());
        repository.saveAndFlush(e);

        // A new PENDING for the same pair must succeed (CANCELLED is outside the partial index)
        Enrollment second = Enrollment.apply(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(classmateId),
                Instant.now().plusMillis(200));
        repository.saveAndFlush(second);

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void countActiveSeatsByClassId_excludesCancelledAndWaitlisted() {
        UUID otherClassmate1 = UUID.randomUUID();
        UUID otherClassmate2 = UUID.randomUUID();
        UUID otherClassmate3 = UUID.randomUUID();

        // PENDING — counts
        Enrollment pending = Enrollment.apply(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(classmateId),
                Instant.now());
        repository.saveAndFlush(pending);

        // CONFIRMED — counts
        Enrollment confirmed = Enrollment.apply(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(otherClassmate1),
                Instant.now().plusMillis(10));
        confirmed.confirm(Instant.now());
        repository.saveAndFlush(confirmed);

        // WAITLISTED — does NOT count as a seat
        Enrollment waitlisted = Enrollment.waitlist(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(otherClassmate2),
                Instant.now().plusMillis(20));
        repository.saveAndFlush(waitlisted);

        // CANCELLED — does NOT count
        Enrollment cancelled = Enrollment.apply(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(otherClassmate3),
                Instant.now().plusMillis(30));
        cancelled.cancel(Instant.now());
        repository.saveAndFlush(cancelled);

        assertThat(repository.countActiveSeatsByClassId(classId)).isEqualTo(2);
    }

    @Test
    void findByClassIdAndStatusInOrderByAppliedAtAsc_returnsFifoOrder() {
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T10:00:01Z");
        Instant t3 = Instant.parse("2026-01-01T10:00:02Z");

        UUID cm1 = UUID.randomUUID();
        UUID cm2 = UUID.randomUUID();
        UUID cm3 = UUID.randomUUID();

        // Insert in reverse order to ensure ordering is by appliedAt, not insert order
        Enrollment e3 = Enrollment.waitlist(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(cm3), t3);
        Enrollment e1 = Enrollment.waitlist(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(cm1), t1);
        Enrollment e2 = Enrollment.waitlist(
                new com.example.liveclass.domain.clazz.ClassId(classId),
                new com.example.liveclass.domain.user.UserId(cm2), t2);

        repository.saveAllAndFlush(List.of(e3, e1, e2));

        List<Enrollment> result = repository.findByClassIdAndStatusInOrderByAppliedAtAsc(
                classId, List.of(EnrollmentStatus.WAITLISTED));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAppliedAt()).isEqualTo(t1);
        assertThat(result.get(1).getAppliedAt()).isEqualTo(t2);
        assertThat(result.get(2).getAppliedAt()).isEqualTo(t3);
    }
}
