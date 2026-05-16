// 수강 취소 통합 테스트 — PENDING/CONFIRMED 취소, 7일 창 검증, 멱등성, 대기열 승격, 보상 시나리오
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

    @MockitoSpyBean
    private EnrollmentMirrorService mirrorServiceSpy;

    private UUID creatorId;
    private UUID classmateId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        Mockito.reset(mirrorServiceSpy);
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

    // ─── Scenario 1: PENDING cancel → CANCELLED, ZSET 에서 ZREM 만 일어남 ────
    @Test
    void pendingCancel_cancelled_noPromotion() {
        Class clazz = persistOpenClass(10);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());
        stringRedisTemplate.delete("waitlist:" + clazz.getId());

        EnrollmentResponse applyResp = enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        assertThat(applyResp.status()).isEqualTo(EnrollmentStatus.PENDING);

        EnrollmentResponse cancelResp = enrollmentApplicationService.cancel(applyResp.id(), classmateId, Instant.now());

        assertThat(cancelResp.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        // ZREM should have removed the member from enrolled
        Long enrolledCard = stringRedisTemplate.opsForZSet().zCard("enrolled:" + clazz.getId());
        assertThat(enrolledCard).isEqualTo(0L);
        // No promotion from waitlist
        Long waitlistCard = stringRedisTemplate.opsForZSet().zCard("waitlist:" + clazz.getId());
        assertThat(waitlistCard).isEqualTo(0L);
    }

    // ─── Scenario 2: CONFIRMED cancel (7일 이내) → CANCELLED + 다음 WAITLISTED 승격 ─
    @Test
    void confirmedCancel_withinWindow_promotesWaitlisted() {
        Class clazz = persistOpenClass(1);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());
        stringRedisTemplate.delete("waitlist:" + clazz.getId());

        // classmateId gets the seat
        EnrollmentResponse applyResp = enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        assertThat(applyResp.status()).isEqualTo(EnrollmentStatus.PENDING);

        // waitlisted user
        UUID waitlistUser = userRepository.save(User.register(UserRole.CLASSMATE, "WL", Instant.now())).getId();
        EnrollmentResponse wlResp = enrollmentApplicationService.apply(waitlistUser, clazz.getId(), Instant.now().plusMillis(1));
        assertThat(wlResp.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

        // Confirm payment
        Instant paidAt = Instant.now();
        enrollmentApplicationService.confirmPayment(applyResp.id(), classmateId, paidAt);

        // Cancel within 7 days
        Instant cancelAt = paidAt.plus(Duration.ofDays(3));
        EnrollmentResponse cancelResp = enrollmentApplicationService.cancel(applyResp.id(), classmateId, cancelAt);
        assertThat(cancelResp.status()).isEqualTo(EnrollmentStatus.CANCELLED);

        // Verify DB state of waitlisted user promoted to PENDING
        var promoted = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), waitlistUser);
        assertThat(promoted).isPresent();
        assertThat(promoted.get().getStatus()).isEqualTo(EnrollmentStatus.PENDING);

        // Verify ZSET: waitlistUser should now be in enrolled
        Double score = stringRedisTemplate.opsForZSet().score("enrolled:" + clazz.getId(), waitlistUser.toString());
        assertThat(score).isNotNull();
        Double waitScore = stringRedisTemplate.opsForZSet().score("waitlist:" + clazz.getId(), waitlistUser.toString());
        assertThat(waitScore).isNull();
    }

    // ─── Scenario 3: CONFIRMED + paidAt + 8일 cancel → 422 ──────────────────
    @Test
    void confirmedCancel_outsideWindow_throws422() {
        Class clazz = persistOpenClass(10);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());

        EnrollmentResponse applyResp = enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        Instant paidAt = Instant.now();
        enrollmentApplicationService.confirmPayment(applyResp.id(), classmateId, paidAt);

        Instant tooLate = paidAt.plus(Duration.ofDays(8));
        assertThatThrownBy(() ->
                enrollmentApplicationService.cancel(applyResp.id(), classmateId, tooLate))
                .isInstanceOf(OutsideCancellationWindowException.class);
    }

    // ─── Scenario 4: 이미 CANCELLED → 200 멱등 응답 ──────────────────────────
    @Test
    void alreadyCancelled_idempotent200() {
        Class clazz = persistOpenClass(10);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());

        EnrollmentResponse applyResp = enrollmentApplicationService.apply(classmateId, clazz.getId(), Instant.now());
        enrollmentApplicationService.cancel(applyResp.id(), classmateId, Instant.now());

        // Second cancel should be idempotent
        EnrollmentResponse second = enrollmentApplicationService.cancel(applyResp.id(), classmateId, Instant.now());
        assertThat(second.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    // ─── Scenario 5: reverseCancelPromote 직접 검증 — ZSET 복귀 + canceller score 보존 ─
    @Test
    void reverseCancelPromote_restoresZset() {
        Class clazz = persistOpenClass(10);
        UUID canceller = classmateId;
        UUID promoted = userRepository.save(User.register(UserRole.CLASSMATE, "Promo", Instant.now())).getId();

        stringRedisTemplate.opsForZSet().add("enrolled:" + clazz.getId(), canceller.toString(), 1000L);
        stringRedisTemplate.opsForZSet().add("waitlist:" + clazz.getId(), promoted.toString(), 2000L);

        mirrorServiceSpy.reverseCancelPromote(clazz.getId(), canceller, promoted, 1000.0, 2000L);

        Double cancellerScore = stringRedisTemplate.opsForZSet().score("enrolled:" + clazz.getId(), canceller.toString());
        assertThat(cancellerScore).isNotNull(); // canceller restored to enrolled
        assertThat(cancellerScore).isEqualTo(1000.0); // and at the ORIGINAL score, not a fresh nanoTime
        Double promotedWaitlistScore = stringRedisTemplate.opsForZSet().score("waitlist:" + clazz.getId(), promoted.toString());
        assertThat(promotedWaitlistScore).isNotNull(); // promoted restored to waitlist
        assertThat(promotedWaitlistScore).isEqualTo(2000.0);
    }
}
