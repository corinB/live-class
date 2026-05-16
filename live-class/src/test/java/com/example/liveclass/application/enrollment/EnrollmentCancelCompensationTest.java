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
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

    @Autowired
    private EnrollmentMirrorService mirrorService;

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

    /**
     * FIFO 보장 시나리오 (Issue #55 핵심 회귀 방지):
     * 1. ZSET 직접 시드 — enrolled: A(score=100), waitlist: B(200)/C(300)/D(400)
     * 2. cancelAndMaybePromote(A, wasConfirmed=true) → A 제거 + B promote
     *    → return {100, B, 200} 검증
     * 3. reverseCancelPromote(A, B, cancellerScore=100, promotedScore=200) → 보상 호출
     * 4. 최종 ZSET 상태:
     *    - enrolled WITHSCORES = [(A, 100)]   ← A score 가 nanoTime 으로 새로 매겨지면 실패
     *    - waitlist WITHSCORES = [(B, 200), (C, 300), (D, 400)]
     *    → B 가 waitlist 맨 앞 (FIFO 1순위) 유지되어야 함
     */
    @Test
    void compensation_preservesCancellerOriginalScore_andWaitlistFifo() {
        Class clazz = persistOpenClass(1);
        UUID classId = clazz.getId();
        String enrolledKey = "enrolled:" + classId;
        String waitlistKey = "waitlist:" + classId;
        stringRedisTemplate.delete(enrolledKey);
        stringRedisTemplate.delete(waitlistKey);

        UUID a = classmateId;
        UUID b = waitlistUserId;
        UUID c = userRepository.save(User.register(UserRole.CLASSMATE, "C", Instant.now())).getId();
        UUID d = userRepository.save(User.register(UserRole.CLASSMATE, "D", Instant.now())).getId();

        stringRedisTemplate.opsForZSet().add(enrolledKey, a.toString(), 100.0);
        stringRedisTemplate.opsForZSet().add(waitlistKey, b.toString(), 200.0);
        stringRedisTemplate.opsForZSet().add(waitlistKey, c.toString(), 300.0);
        stringRedisTemplate.opsForZSet().add(waitlistKey, d.toString(), 400.0);

        // Step 1: cancel_promote — A 제거 + B promote, return shape {100, B, 200}
        List<String> luaResult = mirrorService.cancelAndMaybePromote(classId, a, true);

        assertThat(luaResult).hasSize(3);
        double cancellerScore = Double.parseDouble(luaResult.get(0));
        UUID promotedId = UUID.fromString(luaResult.get(1));
        long promotedScore = Long.parseLong(luaResult.get(2));

        assertThat(cancellerScore)
                .as("Lua should return canceller's original score, not a fresh value")
                .isEqualTo(100.0);
        assertThat(promotedId).isEqualTo(b);
        assertThat(promotedScore).isEqualTo(200L);

        // Step 2: reverseCancelPromote — A enrolled 복귀 (score=100), B waitlist 복귀 (score=200)
        mirrorService.reverseCancelPromote(classId, a, b, cancellerScore, promotedScore);

        // Step 3: enrolled = [(A, 100)] only
        Set<TypedTuple<String>> enrolled = stringRedisTemplate.opsForZSet()
                .rangeWithScores(enrolledKey, 0, -1);
        assertThat(enrolled).hasSize(1);
        TypedTuple<String> aEntry = enrolled.iterator().next();
        assertThat(aEntry.getValue()).isEqualTo(a.toString());
        assertThat(aEntry.getScore())
                .as("canceller A must be restored at the original score 100, not nanoTime")
                .isEqualTo(100.0);

        // Step 4: waitlist FIFO = B(200), C(300), D(400) 순서
        Set<TypedTuple<String>> waitlist = stringRedisTemplate.opsForZSet()
                .rangeWithScores(waitlistKey, 0, -1);
        assertThat(waitlist).hasSize(3);
        Iterator<TypedTuple<String>> it = waitlist.iterator();
        TypedTuple<String> bEntry = it.next();
        TypedTuple<String> cEntry = it.next();
        TypedTuple<String> dEntry = it.next();
        assertThat(bEntry.getValue()).isEqualTo(b.toString());
        assertThat(bEntry.getScore()).isEqualTo(200.0);
        assertThat(cEntry.getValue()).isEqualTo(c.toString());
        assertThat(cEntry.getScore()).isEqualTo(300.0);
        assertThat(dEntry.getValue()).isEqualTo(d.toString());
        assertThat(dEntry.getScore()).isEqualTo(400.0);
    }
}
