// Redis 컨테이너를 apply 직전 stop() 시켜 fail-closed (예외 전파 + DB row 변동 0) 동작을 검증
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.infrastructure.RedisKeyFactory;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공유 RedisContainerExtension 대신 전용 RedisContainer 를 띄워 apply 직전 stop() 시킨다.
 *
 * <p>공유 컨테이너는 다른 테스트 클래스가 그대로 재사용하므로 절대 중단시키지 않는다.
 * @DirtiesContext(AFTER_CLASS) 로 Spring 컨텍스트를 격리해 stop 된 Redis 연결을 다음 테스트에 누출하지 않는다.
 *
 * <p>fail-closed 계약 — apply 호출 시 Redis 가 죽어 있으면 예외가 전파되고 DB row 가 생성되지 않는다.
 *
 * <p>실측 — 현재 코드 경로에서는 ClassLockService.executeWithLock 의 setIfAbsent 가 첫 Redis 호출이라
 * Spring 의 PassThroughExceptionTranslationStrategy 가 QueryTimeoutException (DataAccessException 하위)
 * 으로 변환한 채 전파된다. EnrollmentMirrorService.tryApply 안으로 들어가지 못해 MirrorUnavailableException
 * 매핑을 받지 못한다. GlobalExceptionHandler 가 QueryTimeoutException 을 명시 처리하지 않아 최종 HTTP 코드는
 * 503 이 아닌 500 으로 떨어지는 production 갭이 있다 — follow-up 으로 ClassLockService 도
 * MirrorUnavailableException 으로 매핑하거나 핸들러를 확장하는 작업이 필요하다.
 *
 * <p>본 테스트는 "어떤 예외라도 전파되고 DB row 가 생성되지 않는다" 라는 fail-closed 의 핵심 invariant 만 strict 하게 검증한다.
 */
@IntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RedisDisconnectFailClosedTest {

    private static final RedisContainer DEDICATED_REDIS =
            new RedisContainer(DockerImageName.parse("redis:7"));

    static {
        DEDICATED_REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        registry.add("spring.data.redis.host", DEDICATED_REDIS::getHost);
        registry.add("spring.data.redis.port", () -> DEDICATED_REDIS.getFirstMappedPort().toString());
        registry.add("spring.data.redis.password", () -> "");
    }

    @AfterAll
    static void tearDown() {
        if (DEDICATED_REDIS.isRunning()) {
            DEDICATED_REDIS.stop();
        }
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
        if (!DEDICATED_REDIS.isRunning()) {
            DEDICATED_REDIS.start();
        }
        enrollmentRepository.deleteAll();
        creatorId = userRepository.save(User.register(UserRole.CREATOR, "RD-Creator", Instant.now())).getId();
        classmateId = userRepository.save(User.register(UserRole.CLASSMATE, "RD-Classmate", Instant.now())).getId();
    }

    private Class persistOpenClass() {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Disconnect Class",
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
     * apply 호출 직전 Redis 컨테이너 stop() → 첫 Redis 호출 (lock 획득 setIfAbsent) 단계에서 즉시 실패.
     *
     * 검증 (fail-closed invariant).
     * - apply 호출이 예외로 종료 (DataAccessException 또는 MirrorUnavailableException — production 갭으로 후자가 아닐 수 있음).
     * - DB enrollment row 0 변동 — Lua 실패가 DB INSERT 이전 단계라 transaction 진입조차 안 함.
     */
    @Test
    void redisStop_apply_failsClosed_noDbInsert() {
        Class clazz = persistOpenClass();
        UUID classId = clazz.getId();
        long preEnrollmentCount = enrollmentRepository.count();

        // Redis container stop — 다음 Redis 호출 시 연결 실패 유도.
        DEDICATED_REDIS.stop();

        assertThatThrownBy(() -> enrollmentApplicationService.apply(classmateId, classId, Instant.now()))
                .as("Redis 가 죽어 있으면 apply 가 예외로 종료해야 한다 (DataAccess* 또는 MirrorUnavailable)")
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(MirrorUnavailableException.class),
                        ex -> assertThat(ex).isInstanceOf(DataAccessException.class));

        long postEnrollmentCount = enrollmentRepository.count();
        assertThat(postEnrollmentCount).as("no enrollment row should be inserted on fail-closed")
                .isEqualTo(preEnrollmentCount);
    }
}