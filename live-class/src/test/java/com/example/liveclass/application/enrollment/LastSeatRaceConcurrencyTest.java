// 마지막 자리 race — capacity=1 강의에 50 thread 가 동시 apply 해도 정확히 1 PENDING + 49 WAITLISTED 가 보장되는지 검증하는 통합 테스트
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
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
class LastSeatRaceConcurrencyTest {

    private static final int THREAD_COUNT = 50;

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
    private List<UUID> classmateIds;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        creatorId = userRepository.save(User.register(UserRole.CREATOR, "RaceCreator", Instant.now())).getId();
        classmateIds = new ArrayList<>(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            classmateIds.add(userRepository.save(User.register(UserRole.CLASSMATE, "Racer-" + i, Instant.now())).getId());
        }
    }

    private Class persistOpenClass(int capacity) {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Race Class",
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
     * capacity=1 강의에 50 thread 가 동시에 apply.
     *
     * PR #92 (outer-wrap classId 분산락) 적용 후 의미는:
     * - classId 별 분산락(`lock:reconcile:{classId}`) 을 setIfAbsent NX 로 잡으므로
     *   정확히 1 thread 만 통과해 PENDING 으로 응답.
     * - 나머지 49 thread 는 즉시 `ClassLockBusyException` (HTTP 503) 으로 reject.
     *   DB row 도 ZSET 도 변동 없음.
     *
     * 핸드오프(PR #92 이전 가정)의 "PENDING=1 + WAITLISTED=49" 결정론은 outer-wrap 락
     * 도입 후 "PENDING=1 + reject=49" 로 의미가 옮겨졌다. PR #97 의
     * `classLock_contention_realRace_oneWinsOneRejected` 가 2 thread 케이스로 동일 패턴 검증.
     */
    @RepeatedTest(10)
    void capacity1_50threads_outerLockEnforces_onePending_49Rejected() {
        Class clazz = persistOpenClass(1);
        UUID classId = clazz.getId();
        stringRedisTemplate.delete(RedisKeyFactory.enrolled(classId));
        stringRedisTemplate.delete(RedisKeyFactory.waitlist(classId));

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger lockBusyCount = new AtomicInteger();
        AtomicInteger otherErrorCount = new AtomicInteger();

        ConcurrencyTestSupport.runConcurrently(THREAD_COUNT, idx -> {
            try {
                enrollmentApplicationService.apply(classmateIds.get(idx), classId, Instant.now().plusNanos(idx));
                successCount.incrementAndGet();
            } catch (ClassLockBusyException e) {
                lockBusyCount.incrementAndGet();
            } catch (RuntimeException e) {
                otherErrorCount.incrementAndGet();
            }
        });

        long pending = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.PENDING);
        long waitlisted = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.WAITLISTED);
        long cancelled = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CANCELLED);

        // 정확히 1 PENDING — capacity=1 게이트 + outer-wrap 락이 single-winner 보장.
        assertThat(pending).as("exactly one PENDING").isEqualTo(1L);
        assertThat(cancelled).as("no spurious CANCELLED").isZero();
        // 락 acquire 실패한 thread 는 DB row 를 만들지 못함.
        assertThat(pending + waitlisted).as("DB row count == successCount").isEqualTo(successCount.get());
        // 합산이 정확히 50 (모든 thread 가 success / lockBusy 둘 중 하나).
        assertThat(otherErrorCount.get()).as("only ClassLockBusy is expected, no other errors").isZero();
        assertThat(successCount.get() + lockBusyCount.get()).isEqualTo(THREAD_COUNT);
        // 첫 thread 가 락 잡고 처리하는 동안 나머지는 모두 setIfAbsent NX 에서 즉시 fail.
        // race 가 진짜이면 successCount == 1, lockBusyCount == 49 결정론적.
        assertThat(successCount.get()).as("only the lock winner reaches DB").isEqualTo(1);
        assertThat(lockBusyCount.get()).as("49 threads rejected by lock contention").isEqualTo(THREAD_COUNT - 1);

        Long enrolledZcard = stringRedisTemplate.opsForZSet().zCard(RedisKeyFactory.enrolled(classId));
        Long waitlistZcard = stringRedisTemplate.opsForZSet().zCard(RedisKeyFactory.waitlist(classId));
        assertThat(enrolledZcard).as("ZCARD enrolled == 1").isEqualTo(1L);
        assertThat(waitlistZcard).as("ZCARD waitlist == 0 (no thread reached Lua promotion path)").isZero();
    }
}
