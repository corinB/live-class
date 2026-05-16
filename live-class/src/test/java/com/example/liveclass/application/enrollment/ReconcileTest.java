// flushDb 후 ReconcileService.reconcileOne 이 DB enrollment 로부터 enrolled/waitlist ZSET 을 정확히 재구성하는지 검증
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
import com.example.liveclass.infrastructure.ReconcileService;
import com.example.liveclass.infrastructure.RedisKeyFactory;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class ReconcileTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private EnrollmentApplicationService enrollmentApplicationService;

    @Autowired
    private ReconcileService reconcileService;

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
        creatorId = userRepository.save(User.register(UserRole.CREATOR, "RC-Creator", Instant.now())).getId();
    }

    private Class persistOpenClass(int capacity) {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Reconcile Class",
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
     * capacity=2 인 강의에 3명이 apply → 2 PENDING + 1 WAITLISTED.
     * (task 스펙의 "capacity=3 / PENDING 2 / WAITLISTED 1" 은 분포가 자연스럽게 나오는 capacity=2 로 해석.)
     *
     * flushDb() 로 모든 Redis 키 삭제 후 reconcileOne(classId) 호출.
     *
     * 검증:
     * - ZCARD enrolled == DB COUNT(status IN PENDING, CONFIRMED).
     * - ZCARD waitlist == DB COUNT(status = WAITLISTED).
     * - ZRANGE WITHSCORES 의 순서가 findByClassIdAndStatusInOrderByAppliedAtAsc 결과 순서와 일치.
     */
    @Test
    void flushDbThenReconcile_rebuildsZsetsToMatchDb() {
        Class clazz = persistOpenClass(2);
        UUID classId = clazz.getId();

        // 3명의 classmate 가 순차 apply → 2 PENDING + 1 WAITLISTED
        Instant base = Instant.parse("2026-05-01T00:00:00Z");
        List<UUID> classmateIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID cid = userRepository.save(User.register(UserRole.CLASSMATE, "RC-Mate-" + i, Instant.now())).getId();
            classmateIds.add(cid);
            enrollmentApplicationService.apply(cid, classId, base.plusMillis(i * 100L));
        }

        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.PENDING)).isEqualTo(2L);
        assertThat(enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.WAITLISTED)).isEqualTo(1L);

        // flushDb — Redis 전 keyspace 삭제 (mirror 손실 시나리오 시뮬레이션).
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        String enrolledKey = RedisKeyFactory.enrolled(classId);
        String waitlistKey = RedisKeyFactory.waitlist(classId);
        assertThat(stringRedisTemplate.opsForZSet().zCard(enrolledKey)).isZero();
        assertThat(stringRedisTemplate.opsForZSet().zCard(waitlistKey)).isZero();

        boolean reconciled = reconcileService.reconcileOne(classId);
        assertThat(reconciled).as("lock 획득 후 reconcile 본체 실행").isTrue();

        // ZCARD invariant — DB 상태와 정확히 일치.
        long dbEnrolled = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.PENDING)
                + enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.CONFIRMED);
        long dbWaitlisted = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.WAITLISTED);
        assertThat(stringRedisTemplate.opsForZSet().zCard(enrolledKey)).isEqualTo(dbEnrolled);
        assertThat(stringRedisTemplate.opsForZSet().zCard(waitlistKey)).isEqualTo(dbWaitlisted);

        // ZRANGE 순서가 DB appliedAt 오름차순과 일치.
        List<Enrollment> dbEnrolledOrdered = enrollmentRepository
                .findByClassIdAndStatusInOrderByAppliedAtAsc(classId,
                        List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED));
        Set<ZSetOperations.TypedTuple<String>> enrolledRange =
                stringRedisTemplate.opsForZSet().rangeWithScores(enrolledKey, 0, -1);
        assertThat(enrolledRange).isNotNull();
        List<String> enrolledMembers = enrolledRange.stream().map(ZSetOperations.TypedTuple::getValue).toList();
        List<String> dbEnrolledMembers = dbEnrolledOrdered.stream().map(e -> e.getClassmateId().toString()).toList();
        assertThat(enrolledMembers).as("enrolled ZSET 순서 == DB appliedAt asc")
                .containsExactlyElementsOf(dbEnrolledMembers);

        List<Enrollment> dbWaitlistedOrdered = enrollmentRepository
                .findByClassIdAndStatusInOrderByAppliedAtAsc(classId, List.of(EnrollmentStatus.WAITLISTED));
        Set<ZSetOperations.TypedTuple<String>> waitlistRange =
                stringRedisTemplate.opsForZSet().rangeWithScores(waitlistKey, 0, -1);
        assertThat(waitlistRange).isNotNull();
        List<String> waitlistMembers = waitlistRange.stream().map(ZSetOperations.TypedTuple::getValue).toList();
        List<String> dbWaitlistMembers = dbWaitlistedOrdered.stream().map(e -> e.getClassmateId().toString()).toList();
        assertThat(waitlistMembers).as("waitlist ZSET 순서 == DB appliedAt asc")
                .containsExactlyElementsOf(dbWaitlistMembers);
    }
}