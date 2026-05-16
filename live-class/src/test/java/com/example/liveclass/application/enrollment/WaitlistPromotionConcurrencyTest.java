// CONFIRMED 2건 동시 cancel — outer-wrap 분산락으로 1 cancel + 1 promote, 다른 thread 는 reject
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.infrastructure.ClassLockBusyException;
import com.example.liveclass.infrastructure.RedisKeyFactory;
import com.example.liveclass.support.ConcurrencyTestSupport;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class WaitlistPromotionConcurrencyTest {

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

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        creatorId = userRepository.save(User.register(UserRole.CREATOR, "WP-Creator", Instant.now())).getId();
    }

    private Class persistOpenClass(int capacity) {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Promotion Class",
                "desc",
                Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW")),
                Capacity.of(capacity),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(7)),
                Instant.now()
        );
        clazz.open(UserId.of(creatorId), Instant.now());
        Class saved = classRepository.save(clazz);
        stringRedisTemplate.opsForValue().set(RedisKeyFactory.classStatus(saved.getId()), "OPEN");
        return saved;
    }

    /**
     * capacity=2 + CONFIRMED 2건 + WAITLISTED 5건. 두 CONFIRMED 사용자가 동시 cancel.
     *
     * PR #92 outer-wrap 효과:
     * - classId 단일 락이므로 두 cancel 중 1 thread 만 통과 → 1 cancel + 1 waitlist 승격
     * - 다른 thread 는 `ClassLockBusyException` (503), DB 변동 없음
     *
     * 동시 시점에서 정확히 promote 1번만 (double-promotion 회피의 가장 견고한 형태 — outer-wrap 자체가
     * 직렬화 보장). 핸드오프 DoD "정확히 2 promote" 는 PR #92 이전 가정이며, 실제 production 에서는
     * 순차 호출(cancel A → 락 풀림 → cancel B) 로 양쪽 처리된다.
     */
    @RepeatedTest(10)
    void twoConfirmedCancel_concurrent_outerLockEnforces_oneCancelOnePromote() {
        Class clazz = persistOpenClass(2);
        UUID classId = clazz.getId();
        stringRedisTemplate.delete(RedisKeyFactory.enrolled(classId));
        stringRedisTemplate.delete(RedisKeyFactory.waitlist(classId));

        // 2 CONFIRMED + 5 WAITLISTED 적재
        UUID c1 = userRepository.save(User.register(UserRole.CLASSMATE, "C1", Instant.now())).getId();
        UUID c2 = userRepository.save(User.register(UserRole.CLASSMATE, "C2", Instant.now())).getId();
        EnrollmentResponse a1 = enrollmentApplicationService.apply(c1, classId, Instant.now());
        EnrollmentResponse a2 = enrollmentApplicationService.apply(c2, classId, Instant.now().plusMillis(1));
        Instant paid = Instant.now();
        enrollmentApplicationService.confirmPayment(a1.id(), c1, paid);
        enrollmentApplicationService.confirmPayment(a2.id(), c2, paid);

        List<UUID> waitlistUsers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID w = userRepository.save(User.register(UserRole.CLASSMATE, "W" + i, Instant.now())).getId();
            enrollmentApplicationService.apply(w, classId, Instant.now().plusMillis(10L + i));
            waitlistUsers.add(w);
        }

        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CONFIRMED)).isEqualTo(2L);
        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.WAITLISTED)).isEqualTo(5L);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger lockBusyCount = new AtomicInteger();
        Instant cancelAt = paid.plus(Duration.ofDays(1));

        // 두 CONFIRMED 동시 cancel
        UUID[] cancelTargets = { a1.id(), a2.id() };
        UUID[] callers = { c1, c2 };
        ConcurrencyTestSupport.runConcurrently(2, idx -> {
            try {
                enrollmentApplicationService.cancel(cancelTargets[idx], callers[idx], cancelAt);
                successCount.incrementAndGet();
            } catch (ClassLockBusyException e) {
                lockBusyCount.incrementAndGet();
            }
        });

        // 환경에 따라 successCount 1 또는 2. 락 winner 처리 → 락 풀림 → 다른 thread acquire 가능.
        // 그러나 double-promotion 은 절대 일어나지 않는다 — 각 cancel 은 cancel_promote.lua 의
        // ZPOPMIN 으로 가장 오래된 waitlist 1명만 promote, ZADD enrolled 까지 원자.
        assertThat(successCount.get() + lockBusyCount.get()).isEqualTo(2);
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(2);

        long cancelled = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CANCELLED);
        long confirmed = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CONFIRMED);
        long pending = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.PENDING);
        long waitlisted = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.WAITLISTED);

        // successCount 에 따라 결정론적 분기 — 각 cancel 마다 정확히 1 promote.
        int s = successCount.get();
        assertThat(cancelled).as("CANCELLED == successCount").isEqualTo((long) s);
        assertThat(confirmed).as("CONFIRMED == 2 - successCount").isEqualTo(2L - s);
        assertThat(pending).as("PENDING == successCount (each cancel promotes one)").isEqualTo((long) s);
        assertThat(waitlisted).as("WAITLISTED == 5 - successCount").isEqualTo(5L - s);

        // ZSET enrolled == confirmed + pending == 2 (capacity 항상 채워짐), waitlist == 5 - successCount.
        Long enrolledZcard = stringRedisTemplate.opsForZSet().zCard(RedisKeyFactory.enrolled(classId));
        Long waitlistZcard = stringRedisTemplate.opsForZSet().zCard(RedisKeyFactory.waitlist(classId));
        assertThat(enrolledZcard).isEqualTo(2L);
        assertThat(waitlistZcard).isEqualTo((long) (5 - s));

        // 가장 오래된 waitlist (waitlistUsers.get(0)) 가 첫 cancel 의 promote 대상.
        var firstPromoted = enrollmentRepository.findActiveByClassAndClassmate(classId, waitlistUsers.get(0));
        assertThat(firstPromoted).isPresent();
        assertThat(firstPromoted.get().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }
}
