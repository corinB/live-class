// 동일 enrollment 에 동시 cancel 2건 — outer-wrap classId 분산락이 1 success + 1 ClassLockBusy 결정
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
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class CancelDoubleClickConcurrencyTest {

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
        creatorId = userRepository.save(User.register(UserRole.CREATOR, "DC-Creator", Instant.now())).getId();
        classmateId = userRepository.save(User.register(UserRole.CLASSMATE, "DC-Classmate", Instant.now())).getId();
    }

    private Class persistOpenClass() {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Double-Click Class",
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
     * 같은 CONFIRMED enrollment 에 2 thread 가 동시 cancel.
     *
     * PR #92 outer-wrap 효과:
     * - 1 thread 만 classId 락 통과 → 정상 cancel (200) → DB row CANCELLED
     * - 1 thread 는 즉시 `ClassLockBusyException` (503) → DB 변동 없음
     *
     * ZSET enrolled 에서 정확히 1회 ZREM (lock 통과한 thread 만 Lua 호출).
     */
    @RepeatedTest(10)
    void confirmed_doubleClickCancel_oneSucceeds_oneRejected() {
        Class clazz = persistOpenClass();
        UUID classId = clazz.getId();
        stringRedisTemplate.delete(RedisKeyFactory.enrolled(classId));

        // apply + confirm — CONFIRMED 상태 enrollment 만들기
        EnrollmentResponse applied = enrollmentApplicationService.apply(classmateId, classId, Instant.now());
        Instant paidAt = Instant.now();
        enrollmentApplicationService.confirmPayment(applied.id(), classmateId, paidAt);
        assertThat(stringRedisTemplate.opsForZSet().zCard(RedisKeyFactory.enrolled(classId))).isEqualTo(1L);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger lockBusyCount = new AtomicInteger();
        Instant cancelAt = paidAt.plus(Duration.ofDays(1));

        ConcurrencyTestSupport.runConcurrently(2, idx -> {
            try {
                enrollmentApplicationService.cancel(applied.id(), classmateId, cancelAt);
                successCount.incrementAndGet();
            } catch (ClassLockBusyException e) {
                lockBusyCount.incrementAndGet();
            }
        });

        // 둘 중 정확히 한쪽만 락 acquire — 둘 다 통과 X.
        assertThat(successCount.get() + lockBusyCount.get()).isEqualTo(2);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(lockBusyCount.get()).isEqualTo(1);

        // DB: row 가 정확히 CANCELLED.
        long cancelled = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CANCELLED);
        long confirmed = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CONFIRMED);
        assertThat(cancelled).isEqualTo(1L);
        assertThat(confirmed).isZero();

        // ZSET: enrolled 에서 ZREM 1회 (락 통과 thread). 다른 thread 는 Lua 미호출.
        assertThat(stringRedisTemplate.opsForZSet().zCard(RedisKeyFactory.enrolled(classId))).isZero();
    }
}
