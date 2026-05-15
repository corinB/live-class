// WAITLISTED 다건 존재 시 FIFO 순서로 정확히 1건만 승격되는지 검증하는 통합 테스트
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
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
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
    private UUID confirmedClassmateId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        User creator = userRepository.save(User.register(UserRole.CREATOR, "Creator", Instant.now()));
        User confirmed = userRepository.save(User.register(UserRole.CLASSMATE, "Confirmed", Instant.now()));
        creatorId = creator.getId();
        confirmedClassmateId = confirmed.getId();
    }

    private Class persistOpenClass(int capacity) {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "FIFO Test Class",
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

    // ─── WAITLISTED 3건(appliedAt 다름) → CONFIRMED 1건 cancel → 가장 오래된 1건만 PENDING ─
    @Test
    void fifoPromotion_oldestWaitlistedBecomePending() {
        Class clazz = persistOpenClass(1);
        stringRedisTemplate.delete("enrolled:" + clazz.getId());
        stringRedisTemplate.delete("waitlist:" + clazz.getId());

        Instant base = Instant.now();

        // confirmedClassmateId gets the seat at t=0
        EnrollmentResponse confirmedResp = enrollmentApplicationService.apply(
                confirmedClassmateId, clazz.getId(), base);
        assertThat(confirmedResp.status()).isEqualTo(EnrollmentStatus.PENDING);

        // 3 waitlisted users, applied in order wl1 < wl2 < wl3
        UUID wl1 = userRepository.save(User.register(UserRole.CLASSMATE, "WL1", Instant.now())).getId();
        UUID wl2 = userRepository.save(User.register(UserRole.CLASSMATE, "WL2", Instant.now())).getId();
        UUID wl3 = userRepository.save(User.register(UserRole.CLASSMATE, "WL3", Instant.now())).getId();

        EnrollmentResponse wl1Resp = enrollmentApplicationService.apply(wl1, clazz.getId(), base.plusMillis(1));
        EnrollmentResponse wl2Resp = enrollmentApplicationService.apply(wl2, clazz.getId(), base.plusMillis(2));
        EnrollmentResponse wl3Resp = enrollmentApplicationService.apply(wl3, clazz.getId(), base.plusMillis(3));

        assertThat(wl1Resp.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(wl2Resp.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(wl3Resp.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

        // Confirm payment for the seat holder
        Instant paidAt = Instant.now();
        enrollmentApplicationService.confirmPayment(confirmedResp.id(), confirmedClassmateId, paidAt);

        // Cancel within 7 days
        Instant cancelAt = paidAt.plus(Duration.ofDays(2));
        EnrollmentResponse cancelResp = enrollmentApplicationService.cancel(
                confirmedResp.id(), confirmedClassmateId, cancelAt);
        assertThat(cancelResp.status()).isEqualTo(EnrollmentStatus.CANCELLED);

        // Only wl1 (oldest) should be promoted to PENDING
        var wl1State = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), wl1);
        assertThat(wl1State).isPresent();
        assertThat(wl1State.get().getStatus()).isEqualTo(EnrollmentStatus.PENDING);

        // wl2 and wl3 remain WAITLISTED
        var wl2State = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), wl2);
        assertThat(wl2State).isPresent();
        assertThat(wl2State.get().getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        var wl3State = enrollmentRepository.findActiveByClassAndClassmate(clazz.getId(), wl3);
        assertThat(wl3State).isPresent();
        assertThat(wl3State.get().getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        // ZSET: wl1 should be in enrolled, wl2 and wl3 still in waitlist
        Double wl1EnrolledScore = stringRedisTemplate.opsForZSet().score("enrolled:" + clazz.getId(), wl1.toString());
        assertThat(wl1EnrolledScore).isNotNull();

        Double wl2WaitlistScore = stringRedisTemplate.opsForZSet().score("waitlist:" + clazz.getId(), wl2.toString());
        Double wl3WaitlistScore = stringRedisTemplate.opsForZSet().score("waitlist:" + clazz.getId(), wl3.toString());
        assertThat(wl2WaitlistScore).isNotNull();
        assertThat(wl3WaitlistScore).isNotNull();

        // wl2 score < wl3 score (FIFO preserved in waitlist)
        assertThat(wl2WaitlistScore).isLessThan(wl3WaitlistScore);
    }
}
