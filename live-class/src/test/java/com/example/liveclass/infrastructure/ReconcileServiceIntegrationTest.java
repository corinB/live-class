// ReconcileService 통합 테스트 — ZSET 재구성 정확성 + 멱등성 시나리오
package com.example.liveclass.infrastructure;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class ReconcileServiceIntegrationTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private ReconcileService reconcileService;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID classId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        classRepository.deleteAll();
        userRepository.deleteAll();

        // capacity=10 OPEN class 생성
        User creator = userRepository.save(User.register(UserRole.CREATOR, "Creator", Instant.now()));
        Class clazz = Class.draft(
                UserId.of(creator.getId()),
                "Test Class",
                "desc",
                Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW")),
                Capacity.of(10),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(7)),
                Instant.now()
        );
        clazz.open(UserId.of(creator.getId()), Instant.now());
        classId = classRepository.save(clazz).getId();

        // DB 에 직접 enrollment 레코드 삽입
        // PENDING 3건
        for (int i = 0; i < 3; i++) {
            UUID cm = userRepository.save(User.register(UserRole.CLASSMATE, "CM-P" + i, Instant.now())).getId();
            enrollmentRepository.save(
                    Enrollment.apply(ClassId.of(classId), UserId.of(cm), Instant.now().plusMillis(i)));
        }
        // CONFIRMED 2건
        for (int i = 0; i < 2; i++) {
            UUID cm = userRepository.save(User.register(UserRole.CLASSMATE, "CM-C" + i, Instant.now())).getId();
            Enrollment e = Enrollment.apply(ClassId.of(classId), UserId.of(cm), Instant.now().plusMillis(10 + i));
            e.confirm(Instant.now());
            enrollmentRepository.save(e);
        }
        // WAITLISTED 4건
        for (int i = 0; i < 4; i++) {
            UUID cm = userRepository.save(User.register(UserRole.CLASSMATE, "CM-W" + i, Instant.now())).getId();
            enrollmentRepository.save(
                    Enrollment.waitlist(ClassId.of(classId), UserId.of(cm), Instant.now().plusMillis(20 + i)));
        }
        // CANCELLED 1건
        UUID cmCancel = userRepository.save(User.register(UserRole.CLASSMATE, "CM-CANCEL", Instant.now())).getId();
        Enrollment cancelled = Enrollment.apply(ClassId.of(classId), UserId.of(cmCancel), Instant.now().plusMillis(30));
        cancelled.cancel(Instant.now());
        enrollmentRepository.save(cancelled);

        // Redis ZSET 초기화 (reconcile 전 빈 상태)
        redisTemplate.delete("enrolled:" + classId);
        redisTemplate.delete("waitlist:" + classId);
        redisTemplate.delete("class:status:" + classId);
    }

    @Test
    void reconcileOne_rebuildsZsets_correctly() {
        reconcileService.reconcileOne(classId);

        Long enrolledCard = redisTemplate.opsForZSet().zCard("enrolled:" + classId);
        Long waitlistCard = redisTemplate.opsForZSet().zCard("waitlist:" + classId);

        // PENDING(3) + CONFIRMED(2) = 5
        assertThat(enrolledCard).isEqualTo(5L);
        // WAITLISTED(4)
        assertThat(waitlistCard).isEqualTo(4L);
    }

    @Test
    void reconcileOne_isIdempotent() {
        reconcileService.reconcileOne(classId);
        reconcileService.reconcileOne(classId);

        Long enrolledCard = redisTemplate.opsForZSet().zCard("enrolled:" + classId);
        Long waitlistCard = redisTemplate.opsForZSet().zCard("waitlist:" + classId);

        assertThat(enrolledCard).isEqualTo(5L);
        assertThat(waitlistCard).isEqualTo(4L);
    }

    @Test
    void reconcileOne_rebuildsClassStatusMirror() {
        reconcileService.reconcileOne(classId);

        String statusMirror = redisTemplate.opsForValue().get("class:status:" + classId);
        assertThat(statusMirror).isEqualTo("OPEN");
    }

    @Test
    void reconcileOne_scores_reflect_appliedAt_order() {
        reconcileService.reconcileOne(classId);

        // enrolled ZSET 의 score 들이 단조 증가해야 한다 (FIFO 순서)
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().rangeWithScores("enrolled:" + classId, 0, -1);
        assertThat(tuples).isNotEmpty();

        double prev = Double.NEGATIVE_INFINITY;
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> t : tuples) {
            assertThat(t.getScore()).isGreaterThanOrEqualTo(prev);
            prev = t.getScore();
        }
    }

    /**
     * P1 동시성 보호 — 같은 classId에 대해 외부 클라이언트가 이미 락(`lock:reconcile:{classId}`)을 잡고
     * 있는 동안 reconcileOne이 호출되면 본체를 건너뛰고 false를 리턴해야 한다 (분산락 정합성).
     */
    @Test
    void reconcileOne_skipsWhenLockHeldElsewhere() {
        String lockKey = "lock:reconcile:" + classId;
        // 외부 클라이언트가 락을 선점한 상황을 시뮬레이션. TTL을 짧게 잡아 테스트가 빨리 끝나도록.
        redisTemplate.opsForValue().set(lockKey, "external-holder",
                java.time.Duration.ofSeconds(5));
        try {
            boolean ran = reconcileService.reconcileOne(classId);
            assertThat(ran).isFalse();

            // 본체가 안 돌았으니 ZSET은 빈 상태 그대로 (setUp에서 비워둠).
            Long enrolledCard = redisTemplate.opsForZSet().zCard("enrolled:" + classId);
            Long waitlistCard = redisTemplate.opsForZSet().zCard("waitlist:" + classId);
            assertThat(enrolledCard).isZero();
            assertThat(waitlistCard).isZero();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 락 해제 안전성 — 본인이 안 잡은 락은 해제하지 않는다. 외부 락이 살아있는 동안 reconcileOne을
     * 호출해도 외부 락의 값은 변하지 않아야 한다 (토큰 기반 safe-unlock Lua 보장).
     */
    @Test
    void reconcileOne_doesNotReleaseForeignLock() {
        String lockKey = "lock:reconcile:" + classId;
        redisTemplate.opsForValue().set(lockKey, "external-holder",
                java.time.Duration.ofSeconds(5));
        try {
            reconcileService.reconcileOne(classId);

            String afterValue = redisTemplate.opsForValue().get(lockKey);
            assertThat(afterValue).isEqualTo("external-holder");
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
