// apply 중 DB INSERT 강제 throw — Lua compensate 스크립트가 ZSET 변경을 원자적으로 되돌리는지 검증
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.infrastructure.RedisKeyFactory;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@IntegrationTest
@ExtendWith(RedisContainerExtension.class)
class LuaCompensationConcurrencyTest {

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

    @MockitoSpyBean
    private EnrollmentRepository enrollmentRepositorySpy;

    private UUID creatorId;
    private UUID classmateId;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        Mockito.reset(enrollmentRepositorySpy);
        creatorId = userRepository.save(User.register(UserRole.CREATOR, "LC-Creator", Instant.now())).getId();
        classmateId = userRepository.save(User.register(UserRole.CLASSMATE, "LC-Classmate", Instant.now())).getId();
    }

    private Class persistOpenClass() {
        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Compensation Class",
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
     * apply 흐름: tryApply Lua → ZADD enrolled → enrollmentRepository.save → 정상 종료.
     *
     * `enrollmentRepository.save` 가 `DataIntegrityViolationException` 으로 throw 하도록
     * `@MockitoSpyBean` 으로 설정. apply 가 catch 블록에서 `compensateApply` Lua 를 호출,
     * 방금 ZADD 한 멤버를 ZREM 으로 되돌리는지 검증.
     *
     * 검증: apply 호출 후 ZCARD enrolled == 0 (보상 성공) + DB 에 enrollment row 미생성.
     */
    @Test
    void applyDbFailure_compensateLuaRemovesZsetMember() {
        Class clazz = persistOpenClass();
        UUID classId = clazz.getId();
        String enrolledKey = RedisKeyFactory.enrolled(classId);
        String waitlistKey = RedisKeyFactory.waitlist(classId);
        stringRedisTemplate.delete(enrolledKey);
        stringRedisTemplate.delete(waitlistKey);

        long preEnrolledCard = stringRedisTemplate.opsForZSet().zCard(enrolledKey);
        assertThat(preEnrolledCard).isZero();

        // save 가 DataIntegrityViolationException throw — applyInTx catch 가 compensateApply 호출
        doThrow(new DataIntegrityViolationException("Simulated DB INSERT failure"))
                .when(enrollmentRepositorySpy)
                .save(any(Enrollment.class));

        // apply 가 RuntimeException 으로 외부 전파 (mapDbException 으로 DuplicateEnrollmentException 매핑 가능)
        assertThatThrownBy(() ->
                enrollmentApplicationService.apply(classmateId, classId, Instant.now()))
                .isInstanceOf(RuntimeException.class);

        // 보상 성공 검증: ZCARD enrolled 가 호출 전 값(0)으로 복귀.
        Long postEnrolledCard = stringRedisTemplate.opsForZSet().zCard(enrolledKey);
        assertThat(postEnrolledCard)
                .as("compensate Lua should ZREM the just-added member")
                .isEqualTo(preEnrolledCard);

        // waitlist 도 변동 없음.
        assertThat(stringRedisTemplate.opsForZSet().zCard(waitlistKey)).isZero();

        // DB 에 row 미생성 — save 가 throw 했고 spy 가 실제 호출 안 함.
        assertThat(enrollmentRepository.findAll()).isEmpty();
    }
}
