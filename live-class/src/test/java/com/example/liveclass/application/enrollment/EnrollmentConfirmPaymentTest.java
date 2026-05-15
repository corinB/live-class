// 결제 확정(confirm-payment) 통합 테스트 — PENDING→CONFIRMED, 잘못된 상태/권한 시나리오 검증
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
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

    // ─── Scenario 1: PENDING → confirm → CONFIRMED + paidAt 기록, ZSET 변동 없음 ─
    @Test
    void pendingToConfirmed_recordsPaidAt_noZsetChange() {
        Class clazz = persistOpenClass(10);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());

        EnrollmentResponse applyResp = enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        assertThat(applyResp.status()).isEqualTo(EnrollmentStatus.PENDING);

        Long zsetBefore = stringRedisTemplate.opsForZSet().zCard("enrolled:" + clazz.getId());

        EnrollmentResponse confirmResp = enrollmentApplicationService.confirmPayment(
                applyResp.id(), classmateId, Instant.now());

        assertThat(confirmResp.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(confirmResp.paidAt()).isNotNull();

        // ZSET should be unchanged (enrolled contains both PENDING and CONFIRMED)
        Long zsetAfter = stringRedisTemplate.opsForZSet().zCard("enrolled:" + clazz.getId());
        assertThat(zsetAfter).isEqualTo(zsetBefore);
    }

    // ─── Scenario 2: WAITLISTED 상태에서 confirm → IllegalStateTransitionException ─
    @Test
    void waitlisted_confirmThrows_illegalStateTransition() {
        Class clazz = persistOpenClass(1);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());
        stringRedisTemplate.delete("waitlist:" + clazz.getId());

        // Fill the seat
        UUID cm1 = userRepository.save(User.register(UserRole.CLASSMATE, "C1", Instant.now())).getId();
        enrollmentApplicationService.apply(cm1, clazz.getId(), Instant.now());

        // classmateId goes to waitlist
        EnrollmentResponse waitlisted = enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now().plusMillis(1));
        assertThat(waitlisted.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

        assertThatThrownBy(() ->
                enrollmentApplicationService.confirmPayment(waitlisted.id(), classmateId, Instant.now()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    // ─── Scenario 3: 다른 사용자가 confirm 시도 → AccessDeniedDomainException ───
    @Test
    void otherUser_confirmThrows_accessDenied() {
        Class clazz = persistOpenClass(10);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());

        EnrollmentResponse applyResp = enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());

        UUID otherUser = userRepository.save(User.register(UserRole.CLASSMATE, "Other", Instant.now())).getId();

        assertThatThrownBy(() ->
                enrollmentApplicationService.confirmPayment(applyResp.id(), otherUser, Instant.now()))
                .isInstanceOf(AccessDeniedDomainException.class);
    }
}
