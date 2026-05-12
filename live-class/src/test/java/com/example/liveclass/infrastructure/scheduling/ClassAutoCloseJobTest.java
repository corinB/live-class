// ClassAutoCloseJob 통합 테스트 — endDate 경과 여부에 따른 OPEN→CLOSED 자동 전이와 조건 미달 케이스를 검증한다.
package com.example.liveclass.infrastructure.scheduling;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.ClassStatus;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ClassAutoCloseJob.
 * Clock is fixed to 2026-05-13T00:05:00+09:00 (KST) so that classes with endDate=2026-05-12 are expired.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("task16")
@Testcontainers
class ClassAutoCloseJobTest {

    // Fixed clock: 2026-05-13 00:05 KST → instant in UTC
    static final Instant FIXED_INSTANT = Instant.parse("2026-05-12T15:05:00Z"); // 2026-05-13T00:05:00+09:00
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @RegisterExtension
    static RedisContainerExtension redis = new RedisContainerExtension();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
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
    Scheduler scheduler;

    @BeforeEach
    void cleanDb() {
        classRepository.deleteAll();
    }

    private Class buildOpenClass(LocalDate startDate, LocalDate endDate) {
        UserId creator = UserId.of(UUID.randomUUID());
        Money price = Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW"));
        Capacity cap = Capacity.of(30);
        ClassPeriod period = ClassPeriod.of(startDate, endDate);
        Class clazz = Class.draft(creator, "Test", null, price, cap, period, FIXED_INSTANT.minusSeconds(100));
        clazz.open(creator, FIXED_INSTANT.minusSeconds(50));
        return classRepository.save(clazz);
    }

    @Test
    void scenario1_expiredOpenClass_becomesClosedAfterJobTrigger() throws Exception {
        // endDate=2026-05-12 → todayKst(2026-05-13) 기준으로 경과
        Class target = buildOpenClass(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 12));

        scheduler.triggerJob(JobKey.jobKey("classAutoCloseJob"));
        // Give the job a moment to execute synchronously in the Quartz thread pool
        Thread.sleep(500);

        Class reloaded = classRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClassStatus.CLOSED);
    }

    @Test
    void scenario2_futurEndDate_remainsOpen() throws Exception {
        // endDate=2026-05-15 → 아직 경과 안 됨
        Class target = buildOpenClass(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15));

        scheduler.triggerJob(JobKey.jobKey("classAutoCloseJob"));
        Thread.sleep(500);

        Class reloaded = classRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClassStatus.OPEN);
    }

    @Test
    void scenario3_alreadyClosedClass_notAffected() throws Exception {
        // CLOSED 상태 Class → autoClose 쿼리 조건(OPEN)에서 제외됨
        UserId creator = UserId.of(UUID.randomUUID());
        Money price = Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW"));
        Capacity cap = Capacity.of(30);
        ClassPeriod period = ClassPeriod.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 12));
        Class clazz = Class.draft(creator, "ClosedTest", null, price, cap, period,
                FIXED_INSTANT.minusSeconds(200));
        clazz.open(creator, FIXED_INSTANT.minusSeconds(150));
        clazz.close(creator, FIXED_INSTANT.minusSeconds(100));
        Class saved = classRepository.save(clazz);

        scheduler.triggerJob(JobKey.jobKey("classAutoCloseJob"));
        Thread.sleep(500);

        Class reloaded = classRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClassStatus.CLOSED);
        // version should not change (not touched by the job)
        assertThat(reloaded.getVersion()).isEqualTo(saved.getVersion());
    }
}
