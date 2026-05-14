// 대기열 FIFO 승격 통합 테스트 — CONFIRMED 취소 시 가장 오래된 WAITLISTED 1건만 PENDING 승격 검증
package com.example.liveclass.application.enrollment;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class WaitlistPromotionFifoTest {

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
        User creator = userRepository.save(User.register(UserRole.CREATOR, "Creator", Instant.now()));
        User classmate = userRepository.save(User.register(UserRole.CLASSMATE, "Classmate", Instant.now()));
        creatorId = creator.getId();
        classmateId = classmate.getId();
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

    private void clearZsets(UUID classId) {
        stringRedisTemplate.delete("enrolled:" + classId);
        stringRedisTemplate.delete("waitlist:" + classId);
    }

    /**
     * WAITLISTED 3건(appliedAt 다름)이 있을 때 CONFIRMED 1건 cancel →
     * 가장 오래된 1건만 PENDING 이 되는지 검증.
     * ZSET 의 score 순서(appliedAt asc)가 FIFO 를 보장하는지도 확인한다.
     */
    @Test
    void threeWaitlisted_cancelConfirmed_onlyOldestPromoted() throws InterruptedException {
        Class clazz = persistOpenClass(1);
        clearZsets(clazz.getId());

        Instant t0 = Instant.now();

        // Fill the 1 seat with classmateId
        enrollmentApplicationService.apply(classmateId, clazz.getId(), t0);
        Enrollment confirmed = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), classmateId).orElseThrow();
        enrollmentApplicationService.confirmPayment(confirmed.getId(), classmateId, t0);

        // Add 3 waiters in order: w1 (oldest), w2, w3 (newest)
        User w1 = userRepository.save(User.register(UserRole.CLASSMATE, "W1", Instant.now()));
        User w2 = userRepository.save(User.register(UserRole.CLASSMATE, "W2", Instant.now()));
        User w3 = userRepository.save(User.register(UserRole.CLASSMATE, "W3", Instant.now()));

        Instant tw1 = t0.plusMillis(100);
        Instant tw2 = t0.plusMillis(200);
        Instant tw3 = t0.plusMillis(300);

        enrollmentApplicationService.apply(w1.getId(), clazz.getId(), tw1);
        enrollmentApplicationService.apply(w2.getId(), clazz.getId(), tw2);
        enrollmentApplicationService.apply(w3.getId(), clazz.getId(), tw3);

        // Verify all three are WAITLISTED
        assertThat(enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), w1.getId())
                .map(Enrollment::getStatus).orElse(null)).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), w2.getId())
                .map(Enrollment::getStatus).orElse(null)).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), w3.getId())
                .map(Enrollment::getStatus).orElse(null)).isEqualTo(EnrollmentStatus.WAITLISTED);

        // Verify ZSET waitlist has 3 members in FIFO order
        String waitlistKey = "waitlist:" + clazz.getId();
        assertThat(stringRedisTemplate.opsForZSet().zCard(waitlistKey)).isEqualTo(3L);

        // Cancel the CONFIRMED enrollment within 7-day window
        Enrollment confirmedReloaded = enrollmentRepository.findById(confirmed.getId()).orElseThrow();
        Instant cancelTime = confirmedReloaded.getPaidAt().plus(Duration.ofDays(1));
        enrollmentApplicationService.cancel(confirmedReloaded.getId(), classmateId, cancelTime);

        // Only w1 (oldest) must be promoted to PENDING
        Enrollment w1Enrollment = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), w1.getId()).orElseThrow();
        Enrollment w2Enrollment = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), w2.getId()).orElseThrow();
        Enrollment w3Enrollment = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), w3.getId()).orElseThrow();

        assertThat(w1Enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(w2Enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(w3Enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        // ZSET: enrolled has w1, waitlist has w2 and w3
        String enrolledKey = "enrolled:" + clazz.getId();
        assertThat(stringRedisTemplate.opsForZSet().zCard(enrolledKey)).isEqualTo(1L);
        assertThat(stringRedisTemplate.opsForZSet().zCard(waitlistKey)).isEqualTo(2L);

        // Verify FIFO: w1 is in enrolled, w2 before w3 in waitlist
        assertThat(stringRedisTemplate.opsForZSet().score(enrolledKey, w1.getId().toString())).isNotNull();
        Double w2Score = stringRedisTemplate.opsForZSet().score(waitlistKey, w2.getId().toString());
        Double w3Score = stringRedisTemplate.opsForZSet().score(waitlistKey, w3.getId().toString());
        assertThat(w2Score).isLessThan(w3Score);
    }
}
