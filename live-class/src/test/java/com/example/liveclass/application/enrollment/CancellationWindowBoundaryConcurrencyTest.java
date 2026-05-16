// 7일 취소 창 경계값 (paidAt + 7d, +7d+1ns, +6d23h) 이 200 / 422 / 200 으로 결정론적으로 응답하는지 검증
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.enrollment.OutsideCancellationWindowException;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.infrastructure.RedisKeyFactory;
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
class CancellationWindowBoundaryConcurrencyTest {

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
        creatorId = userRepository.save(User.register(UserRole.CREATOR, "CW-Creator", Instant.now())).getId();
        classmateId = userRepository.save(User.register(UserRole.CLASSMATE, "CW-Classmate", Instant.now())).getId();
    }

    private Class persistOpenClass() {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Boundary Class",
                "desc",
                Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW")),
                Capacity.of(10),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(7)),
                Instant.now()
        );
        clazz.open(UserId.of(creatorId), Instant.now());
        Class saved = classRepository.save(clazz);
        stringRedisTemplate.opsForValue().set(RedisKeyFactory.classStatus(saved.getId()), "OPEN");
        return saved;
    }

    /**
     * paidAt 시점에 결제 완료 → 정확히 paidAt + 7일 시점에 cancel.
     * CancellationWindow.isWithin 은 폐구간 정책(`!now.isAfter(paidAt + 7d)`) 이므로 200 (CANCELLED).
     */
    @Test
    void cancelExactlyAt7Days_returnsOk() {
        UUID classId = persistOpenClass().getId();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        EnrollmentResponse applied = enrollmentApplicationService.apply(classmateId, classId, t0);
        enrollmentApplicationService.confirmPayment(applied.id(), classmateId, t0);

        Instant cancelAt = t0.plus(Duration.ofDays(7));
        EnrollmentResponse cancelled = enrollmentApplicationService.cancel(applied.id(), classmateId, cancelAt);

        assertThat(cancelled.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CANCELLED)).isEqualTo(1L);
    }

    /**
     * paidAt + 7d + 1ns → 7일 경계를 1나노초 초과 → OutsideCancellationWindowException (HTTP 422).
     * DB 상태는 CONFIRMED 유지, ZSET enrolled 변동 없음.
     */
    @Test
    void cancelOneNanoPast7Days_throwsOutsideWindow() {
        UUID classId = persistOpenClass().getId();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        EnrollmentResponse applied = enrollmentApplicationService.apply(classmateId, classId, t0);
        enrollmentApplicationService.confirmPayment(applied.id(), classmateId, t0);

        Instant cancelAt = t0.plus(Duration.ofDays(7)).plusNanos(1);

        assertThatThrownBy(() -> enrollmentApplicationService.cancel(applied.id(), classmateId, cancelAt))
                .isInstanceOf(OutsideCancellationWindowException.class);

        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CONFIRMED)).isEqualTo(1L);
        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CANCELLED)).isZero();
        assertThat(stringRedisTemplate.opsForZSet().zCard(RedisKeyFactory.enrolled(classId))).isEqualTo(1L);
    }

    /**
     * paidAt + 6일 23시간 — 7일 경계 안쪽 → 200 (CANCELLED).
     */
    @Test
    void cancelAt6Days23Hours_returnsOk() {
        UUID classId = persistOpenClass().getId();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        EnrollmentResponse applied = enrollmentApplicationService.apply(classmateId, classId, t0);
        enrollmentApplicationService.confirmPayment(applied.id(), classmateId, t0);

        Instant cancelAt = t0.plus(Duration.ofDays(6)).plus(Duration.ofHours(23));
        EnrollmentResponse cancelled = enrollmentApplicationService.cancel(applied.id(), classmateId, cancelAt);

        assertThat(cancelled.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CANCELLED)).isEqualTo(1L);
    }
}