// transitionStatus 가 OL 단독 + retry 루프로 동시 호출을 안전하게 처리하는지 검증하는 race 테스트.
package com.example.liveclass.application.clazz;

import com.example.liveclass.application.user.UserApplicationService;
import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.ClassStatus;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 본 사이클(2026-05-16)에서 ClassApplicationService.transitionStatus 가 비관락
 * (findByIdForUpdate) 을 제거하고 @Version 낙관락 + retry 루프 단독으로 동작하도록 변경됐다.
 * 두 스레드가 같은 transition 을 동시 시도할 때 다음 invariant 가 유지되는지 검증한다.
 *
 * - 정확히 한 쪽만 성공한다 (XOR).
 * - 다른 한 쪽은 retry 후 fresh load 시 이미 transition 된 상태를 보고
 *   IllegalStateTransitionException(=409) 으로 안전 실패한다 (또는 2회 retry 모두 OL 실패 시
 *   ConcurrentClassUpdateException(=409)).
 * - 최종 status 는 target 으로 수렴한다.
 *
 * RepeatedTest 5회 — 동시성 race 결정성 확보.
 */
@IntegrationTest
class ClassApplicationServiceConcurrencyTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private ClassApplicationService classApplicationService;

    @Autowired
    private UserApplicationService userApplicationService;

    private UUID creatorId;

    @BeforeEach
    void setUp() {
        classRepository.deleteAll();
        User creator = userApplicationService.register(UserRole.CREATOR, "ConcurrencyTestCreator");
        creatorId = creator.getId();
    }

    private UUID persistDraftClass() {
        UserId creatorUserId = UserId.of(creatorId);
        Money price = Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW"));
        Capacity cap = Capacity.of(20);
        ClassPeriod period = ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(10));
        Class draft = Class.draft(creatorUserId, "ConcurrencyTest", null, price, cap, period, Instant.now());
        return classRepository.save(draft).getId();
    }

    private UUID persistOpenedClass() {
        UserId creatorUserId = UserId.of(creatorId);
        Money price = Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW"));
        Capacity cap = Capacity.of(20);
        ClassPeriod period = ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(10));
        Class clazz = Class.draft(creatorUserId, "ConcurrencyTest", null, price, cap, period, Instant.now());
        clazz.open(creatorUserId, Instant.now());
        return classRepository.save(clazz).getId();
    }

    @RepeatedTest(5)
    void concurrentDraftToOpen_exactlyOneSucceeds() throws Exception {
        UUID classId = persistDraftClass();

        boolean[] results = runTwoThreads(() ->
                classApplicationService.transitionStatus(classId, creatorId, ClassStatus.OPEN));

        assertThat(results[0] ^ results[1])
                .as("Exactly one of the two concurrent DRAFT→OPEN calls must succeed")
                .isTrue();

        Class reloaded = classRepository.findById(classId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClassStatus.OPEN);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @RepeatedTest(5)
    void concurrentOpenToClosed_exactlyOneSucceeds() throws Exception {
        UUID classId = persistOpenedClass();

        boolean[] results = runTwoThreads(() ->
                classApplicationService.transitionStatus(classId, creatorId, ClassStatus.CLOSED));

        assertThat(results[0] ^ results[1])
                .as("Exactly one of the two concurrent OPEN→CLOSED calls must succeed")
                .isTrue();

        Class reloaded = classRepository.findById(classId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClassStatus.CLOSED);
    }

    private boolean[] runTwoThreads(Runnable action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<Boolean> futureA = pool.submit(() -> {
            ready.countDown();
            go.await();
            try {
                action.run();
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });

        Future<Boolean> futureB = pool.submit(() -> {
            ready.countDown();
            go.await();
            try {
                action.run();
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });

        ready.await();
        go.countDown();

        boolean a = futureA.get();
        boolean b = futureB.get();
        pool.shutdown();
        return new boolean[]{a, b};
    }
}
