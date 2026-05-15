// 취소-보상 E2E 통합 테스트 — DB save 실패 시 Lua 원자 보상 스크립트가 ZSET을 원상복귀하는지 검증
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Verifies that when the DB UPDATE for a promoted waitlist member fails,
 * the single-Lua reverseCancelPromote script atomically restores both ZSET entries.
 * Covers the catch block at lines 197-201 of EnrollmentApplicationService.cancel().
 */
@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class EnrollmentCancelCompensationTest {

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

    @MockitoSpyBean
    private EnrollmentRepository enrollmentRepositorySpy;

    private UUID creatorId;
    private UUID classmateId;
    private UUID waitlistUserId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        Mockito.reset(enrollmentRepositorySpy);
        User creator = userRepository.save(User.register(UserRole.CREATOR, "Creator", Instant.now()));
        User classmate = userRepository.save(User.register(UserRole.CLASSMATE, "Classmate", Instant.now()));
        User waitlistUser = userRepository.save(User.register(UserRole.CLASSMATE, "WaitlistUser", Instant.now()));
        creatorId = creator.getId();
        classmateId = classmate.getId();
        waitlistUserId = waitlistUser.getId();
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

    /**
     * E2E 보상 시나리오:
     * 1. capacity=1 클래스에 PENDING 1명 + WAITLISTED 1명 설정
     * 2. PENDING → CONFIRMED 전이
     * 3. cancel 호출 시 Lua cancel_promote 성공 → promoted DB save에서 RuntimeException 발생
     * 4. reverseCancelPromote Lua가 호출되어 ZSET 원상복귀 검증
     *    - enrolled에 canceller 복귀
     *    - waitlist에 promoted 복귀
     */
    @Test
    void cancelWithDbFailure_compensationRestoresBothZsets() {
        Class clazz = persistOpenClass(1);
        UUID classId = clazz.getId();
        stringRedisTemplate.delete("enrolled:" + classId);
        stringRedisTemplate.delete("waitlist:" + classId);

        // classmateId gets the only seat → PENDING
        EnrollmentResponse applyResp = enrollmentApplicationService.apply(classmateId, classId, Instant.now());
        assertThat(applyResp.status()).isEqualTo(EnrollmentStatus.PENDING);

        // waitlistUserId joins → WAITLISTED
        EnrollmentResponse wlResp = enrollmentApplicationService.apply(waitlistUserId, classId, Instant.now().plusMillis(1));
        assertThat(wlResp.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

        // Confirm payment
        Instant paidAt = Instant.now();
        enrollmentApplicationService.confirmPayment(applyResp.id(), classmateId, paidAt);

        // Verify pre-cancel ZSET state: canceller in enrolled, promoted in waitlist
        assertThat(stringRedisTemplate.opsForZSet().score("enrolled:" + classId, classmateId.toString())).isNotNull();
        assertThat(stringRedisTemplate.opsForZSet().score("waitlist:" + classId, waitlistUserId.toString())).isNotNull();

        // Intercept: make save() throw RuntimeException when saving the promoted enrollment
        // The spy's real save() is used for the canceller's own save, but when called with
        // the promoted Enrollment (identified by classmateId != waitlistUserId), we throw.
        doThrow(new RuntimeException("Simulated DB failure on promoted save"))
                .when(enrollmentRepositorySpy)
                .save(Mockito.argThat(e ->
                        e != null &&
                        e.getClassmateId() != null &&
                        e.getClassmateId().equals(waitlistUserId) &&
                        e.getStatus() == EnrollmentStatus.PENDING));

        // cancel should throw because the promoted DB save failed
        Instant cancelAt = paidAt.plus(Duration.ofDays(1));
        assertThatThrownBy(() ->
                enrollmentApplicationService.cancel(applyResp.id(), classmateId, cancelAt))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated DB failure on promoted save");

        // After compensation: canceller must be back in enrolled ZSET
        Double cancellerScore = stringRedisTemplate.opsForZSet().score("enrolled:" + classId, classmateId.toString());
        assertThat(cancellerScore)
                .as("canceller should be restored to enrolled ZSET after compensation")
                .isNotNull();

        // After compensation: promoted must be back in waitlist ZSET
        Double promotedScore = stringRedisTemplate.opsForZSet().score("waitlist:" + classId, waitlistUserId.toString());
        assertThat(promotedScore)
                .as("promoted should be restored to waitlist ZSET after compensation")
                .isNotNull();

        // promoted must NOT remain in enrolled ZSET
        Double promotedInEnrolled = stringRedisTemplate.opsForZSet().score("enrolled:" + classId, waitlistUserId.toString());
        assertThat(promotedInEnrolled)
                .as("promoted should not remain in enrolled ZSET")
                .isNull();
    }
}
