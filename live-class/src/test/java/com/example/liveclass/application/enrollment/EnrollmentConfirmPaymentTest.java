// confirm-payment 통합 테스트 — PENDING→CONFIRMED 전이, 상태 오류 409, 소유자 검증 403
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.enrollment.IllegalStateTransitionException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class EnrollmentConfirmPaymentTest {

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
    private UUID otherClassmateId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        User creator = userRepository.save(User.register(UserRole.CREATOR, "Creator", Instant.now()));
        User classmate = userRepository.save(User.register(UserRole.CLASSMATE, "Classmate", Instant.now()));
        User other = userRepository.save(User.register(UserRole.CLASSMATE, "Other", Instant.now()));
        creatorId = creator.getId();
        classmateId = classmate.getId();
        otherClassmateId = other.getId();
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

    private Enrollment applyAndGet(UUID classmateId, Class clazz) {
        enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        return enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId)
                .orElseThrow();
    }

    // ─── Scenario 1: 정상 PENDING → CONFIRMED + paidAt 기록 ──────────────────
    @Test
    void pendingToConfirmed_recordsPaidAt() {
        Class clazz = persistOpenClass(10);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());
        Enrollment enrollment = applyAndGet(classmateId, clazz);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);

        String enrolledKey = "enrolled:" + clazz.getId();
        Long zcardBefore = stringRedisTemplate.opsForZSet().zCard(enrolledKey);

        EnrollmentResponse response = enrollmentApplicationService.confirmPayment(
                enrollment.getId(), classmateId, Instant.now());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(response.paidAt()).isNotNull();

        // ZSET must NOT change — enrolled covers both PENDING and CONFIRMED
        Long zcardAfter = stringRedisTemplate.opsForZSet().zCard(enrolledKey);
        assertThat(zcardAfter).isEqualTo(zcardBefore);
    }

    // ─── Scenario 2: WAITLISTED 상태에서 confirm → IllegalStateTransitionException 409 ──
    @Test
    void waitlistedEnrollment_confirmThrowsIllegalStateTransition() {
        Class clazz = persistOpenClass(1);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());
        stringRedisTemplate.delete("waitlist:" + clazz.getId());

        // Fill the seat
        User filler = userRepository.save(User.register(UserRole.CLASSMATE, "Filler", Instant.now()));
        enrollmentApplicationService.apply(filler.getId(), clazz.getId(), Instant.now());

        // classmateId goes to waitlist
        enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now().plusMillis(1));
        Enrollment waitlisted = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId)
                .orElseThrow();
        assertThat(waitlisted.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        assertThatThrownBy(() ->
                enrollmentApplicationService.confirmPayment(waitlisted.getId(), classmateId, Instant.now()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    // ─── Scenario 3: 다른 사용자가 confirm → AccessDeniedDomainException 403 ────
    @Test
    void otherUser_confirmThrowsAccessDenied() {
        Class clazz = persistOpenClass(10);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());
        Enrollment enrollment = applyAndGet(classmateId, clazz);

        assertThatThrownBy(() ->
                enrollmentApplicationService.confirmPayment(enrollment.getId(), otherClassmateId, Instant.now()))
                .isInstanceOf(AccessDeniedDomainException.class);
    }
}
