// cancel 통합 테스트 — PENDING/CONFIRMED 취소, 7일 창 검증, 멱등 200, 보상 시나리오
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.enrollment.OutsideCancellationWindowException;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class EnrollmentCancelTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private EnrollmentApplicationService enrollmentApplicationService;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private UUID creatorId;
    private UUID classmateId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        User creator = userRepository.save(User.register(UserRole.CREATOR, "Creator", Instant.now()));
        User classmate = userRepository.save(User.register(UserRole.CLASSMATE, "Classmate", Instant.now()));
        creatorId = creator.getId();
        classmateId = classmate.getId();
    }

    private Class persistOpenClass(int capacity) {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Test Class",
                "desc",
                Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW")),
                Capacity.of(capacity),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(7)),
                Instant.now()
        );
        clazz.open(UserId.of(creatorId), Instant.now());
        Class saved = classRepository.save(clazz);
        stringRedisTemplate.opsForValue().set("class:status:" + saved.getId(), "OPEN");
        return saved;
    }

    private void clearZsets(UUID classId) {
        stringRedisTemplate.delete("enrolled:" + classId);
        stringRedisTemplate.delete("waitlist:" + classId);
    }

    // ─── Scenario 1: PENDING cancel → CANCELLED, ZSET 에서 ZREM 만 발생 ─────
    @Test
    void pendingCancel_removedFromEnrolledZset() {
        Class clazz = persistOpenClass(10);
        clearZsets(clazz.getId());

        enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        String enrolledKey = "enrolled:" + clazz.getId();
        assertThat(stringRedisTemplate.opsForZSet().zCard(enrolledKey)).isEqualTo(1L);

        Enrollment enrollment = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId).orElseThrow();

        EnrollmentResponse response = enrollmentApplicationService.cancel(enrollment.getId(), classmateId, Instant.now());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        // ZSET should be empty — no waitlist, so no promotion
        assertThat(stringRedisTemplate.opsForZSet().zCard(enrolledKey)).isEqualTo(0L);
        assertThat(stringRedisTemplate.opsForZSet().zCard("waitlist:" + clazz.getId())).isEqualTo(0L);
    }

    // ─── Scenario 2: CONFIRMED cancel (7일 이내) → CANCELLED + 대기열 승격 ───
    @Test
    void confirmedCancelWithWaitlisted_promotesOldest() {
        Class clazz = persistOpenClass(1);
        clearZsets(clazz.getId());

        // Fill the only seat
        enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        Enrollment confirmed = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId).orElseThrow();
        enrollmentApplicationService.confirmPayment(confirmed.getId(), classmateId, Instant.now());

        // Add a waiter
        User waiter = userRepository.save(User.register(UserRole.CLASSMATE, "Waiter", Instant.now()));
        enrollmentApplicationService.apply(waiter.getId(), clazz.getId(), Instant.now().plusMillis(1));

        Enrollment confirmedReloaded = enrollmentRepository.findById(confirmed.getId()).orElseThrow();
        assertThat(confirmedReloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);

        // Cancel within the 7-day window
        Instant cancelTime = confirmedReloaded.getPaidAt().plus(Duration.ofDays(3));
        EnrollmentResponse response = enrollmentApplicationService.cancel(
                confirmedReloaded.getId(), classmateId, cancelTime);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);

        // Waiter should be promoted to PENDING
        Enrollment promoted = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), waiter.getId())
                .orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);

        // ZSET: enrolled should have the promoted member
        assertThat(stringRedisTemplate.opsForZSet().zCard("enrolled:" + clazz.getId())).isEqualTo(1L);
        assertThat(stringRedisTemplate.opsForZSet().zCard("waitlist:" + clazz.getId())).isEqualTo(0L);
    }

    // ─── Scenario 3: CONFIRMED + paidAt + 8일 cancel → 422 OutsideCancellationWindow ──
    @Test
    void confirmedCancelAfter8Days_throwsOutsideCancellationWindow() {
        Class clazz = persistOpenClass(10);
        clearZsets(clazz.getId());

        enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        Enrollment enrollment = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId).orElseThrow();
        enrollmentApplicationService.confirmPayment(enrollment.getId(), classmateId, Instant.now());

        Enrollment confirmed = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        // Attempt to cancel 8 days after paidAt
        Instant outsideWindow = confirmed.getPaidAt().plus(Duration.ofDays(8));

        assertThatThrownBy(() ->
                enrollmentApplicationService.cancel(confirmed.getId(), classmateId, outsideWindow))
                .isInstanceOf(OutsideCancellationWindowException.class);
    }

    // ─── Scenario 4: 이미 CANCELLED인 enrollment DELETE → 200 멱등 ──────────
    @Test
    void alreadyCancelled_idempotent200() {
        Class clazz = persistOpenClass(10);
        clearZsets(clazz.getId());

        enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        Enrollment enrollment = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId).orElseThrow();

        // Cancel once
        enrollmentApplicationService.cancel(enrollment.getId(), classmateId, Instant.now());

        // Cancel again — must return 200 idempotent
        EnrollmentResponse response = enrollmentApplicationService.cancel(enrollment.getId(), classmateId, Instant.now());
        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    // ─── Scenario 5: 정확히 paidAt + 7일 시점 cancel → 200 허용 ────────────
    @Test
    void cancelExactlyAt7DayBoundary_allowed() {
        Class clazz = persistOpenClass(10);
        clearZsets(clazz.getId());

        enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        Enrollment enrollment = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId).orElseThrow();
        enrollmentApplicationService.confirmPayment(enrollment.getId(), classmateId, Instant.now());

        Enrollment confirmed = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        // Exactly paidAt + 7 days — boundary is inclusive (ARCHITECTURE §2.3)
        Instant boundary = confirmed.getPaidAt().plus(Duration.ofDays(7));

        EnrollmentResponse response = enrollmentApplicationService.cancel(confirmed.getId(), classmateId, boundary);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    }
}
