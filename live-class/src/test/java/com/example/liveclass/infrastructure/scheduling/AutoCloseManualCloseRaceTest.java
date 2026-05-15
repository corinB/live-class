// Creator 수동 close와 Quartz auto-close 동시 호출 시 정확히 한 건만 성공함을 검증하는 경쟁 테스트.
package com.example.liveclass.infrastructure.scheduling;

import com.example.liveclass.application.clazz.ClassApplicationService;
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
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.example.liveclass.support.PostgresTestContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Race condition test: manual close (by Creator) vs Quartz auto-close running concurrently.
 * Exactly one must succeed; the other must catch an exception.
 * Final status must be CLOSED.
 * Repeated 10 times for determinism on Linux CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("task16")
class AutoCloseManualCloseRaceTest {

    static final Instant FIXED_INSTANT = Instant.parse("2026-05-12T15:05:00Z");
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @RegisterExtension
    static RedisContainerExtension redis = new RedisContainerExtension();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        registry.add("spring.data.redis.host", RedisContainerExtension::getHost);
        registry.add("spring.data.redis.port", RedisContainerExtension::getPort);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedKstClock() {
            return Clock.fixed(FIXED_INSTANT, KST);
        }
    }

    @Autowired
    ClassRepository classRepository;

    @Autowired
    ClassApplicationService classApplicationService;

    @Autowired
    UserApplicationService userApplicationService;

    private UUID classId;
    private UUID creatorId;

    @BeforeEach
    void setUp() {
        classRepository.deleteAll();

        // Register a Creator user in the DB so transitionStatus can find them
        User creator = userApplicationService.register(UserRole.CREATOR, "RaceTestCreator");
        creatorId = creator.getId();
        UserId creatorUserId = UserId.of(creatorId);

        Money price = Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW"));
        Capacity cap = Capacity.of(30);
        // endDate in the past so autoClose query picks it up if needed
        ClassPeriod period = ClassPeriod.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 12));
        Class clazz = Class.draft(creatorUserId, "RaceTest", null, price, cap, period,
                FIXED_INSTANT.minusSeconds(200));
        clazz.open(creatorUserId, FIXED_INSTANT.minusSeconds(100));
        classId = classRepository.save(clazz).getId();
    }

    @RepeatedTest(10)
    void exactlyOneCloseSucceeds_otherThrows() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        final UUID capturedClassId = classId;
        final UUID capturedCreatorId = creatorId;

        Future<Boolean> manualFuture = pool.submit(() -> {
            ready.countDown();
            go.await();
            try {
                classApplicationService.transitionStatus(capturedClassId, capturedCreatorId,
                        ClassStatus.CLOSED);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });

        Future<Boolean> autoFuture = pool.submit(() -> {
            ready.countDown();
            go.await();
            try {
                classApplicationService.autoClose(capturedClassId, FIXED_INSTANT);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });

        ready.await();
        go.countDown();

        boolean manualResult = manualFuture.get();
        boolean autoResult = autoFuture.get();
        pool.shutdown();

        // Exactly one succeeds
        assertThat(manualResult ^ autoResult)
                .as("Exactly one of manual/auto close must succeed")
                .isTrue();

        // Final status is always CLOSED
        Class reloaded = classRepository.findById(capturedClassId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClassStatus.CLOSED);
    }
}
