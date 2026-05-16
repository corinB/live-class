// 부팅 및 admin 요청 시 Redis ZSET 을 DB 활성 enrollment 로부터 재구성하는 서비스.
package com.example.liveclass.infrastructure;

import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ReconcileService {

    private static final List<EnrollmentStatus> ENROLLED_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);
    private static final List<EnrollmentStatus> WAITLIST_STATUSES =
            List.of(EnrollmentStatus.WAITLISTED);

    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StringRedisTemplate redisTemplate;
    private final ClassLockService classLockService;

    public ReconcileService(ClassRepository classRepository,
                            EnrollmentRepository enrollmentRepository,
                            StringRedisTemplate redisTemplate,
                            ClassLockService classLockService) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.redisTemplate = redisTemplate;
        this.classLockService = classLockService;
    }

    /**
     * DB 활성 enrollment 를 바탕으로 ZSET 과 class:status mirror 를 재구성한다.
     * DEL + 배치 ZADD 패턴으로 멱등성을 보장한다.
     * score = epochSec * 1_000_000_000L + nano (apply Lua 와 동일 공식)
     *
     * 동시성 보호 — classId 별 `lock:reconcile:{classId}` 분산락을 ClassLockService 를 통해 획득한 뒤에만
     * 본체를 수행한다. 같은 락을 apply / cancel 도 공유하므로, reconcile 가 진행 중이면 apply/cancel 은
     * ClassLockBusyException(=503) 으로 거부되고, 반대로 apply/cancel 진행 중이면 reconcile 은 false
     * 리턴 후 skip 한다.
     *
     * @return 락을 획득해서 실제로 재구성을 수행했는지 여부.
     */
    public boolean reconcileOne(UUID classId) {
        return classLockService.tryRun(classId, () -> doReconcileOne(classId));
    }

    private void doReconcileOne(UUID classId) {
        String enrolledKey = RedisKeyFactory.enrolled(classId);
        String waitlistKey = RedisKeyFactory.waitlist(classId);

        // DEL 으로 기존 ZSET 초기화 — 멱등성 보장
        redisTemplate.delete(List.of(enrolledKey, waitlistKey));

        // PENDING + CONFIRMED → enrolled ZSET
        List<Enrollment> enrolled = enrollmentRepository
                .findByClassIdAndStatusInOrderByAppliedAtAsc(classId, ENROLLED_STATUSES);

        if (!enrolled.isEmpty()) {
            Set<ZSetOperations.TypedTuple<String>> enrolledTuples = new HashSet<>();
            for (Enrollment e : enrolled) {
                long score = scoreOf(e.getAppliedAt().getEpochSecond(), e.getAppliedAt().getNano());
                enrolledTuples.add(ZSetOperations.TypedTuple.of(e.getClassmateId().toString(), (double) score));
            }
            redisTemplate.opsForZSet().add(enrolledKey, enrolledTuples);
        }

        // WAITLISTED → waitlist ZSET
        List<Enrollment> waitlisted = enrollmentRepository
                .findByClassIdAndStatusInOrderByAppliedAtAsc(classId, WAITLIST_STATUSES);

        if (!waitlisted.isEmpty()) {
            Set<ZSetOperations.TypedTuple<String>> waitlistTuples = new HashSet<>();
            for (Enrollment e : waitlisted) {
                long score = scoreOf(e.getAppliedAt().getEpochSecond(), e.getAppliedAt().getNano());
                waitlistTuples.add(ZSetOperations.TypedTuple.of(e.getClassmateId().toString(), (double) score));
            }
            redisTemplate.opsForZSet().add(waitlistKey, waitlistTuples);
        }

        // class:status mirror 재구성 (TTL=5분, Lua 첫 호출 mirror miss 방지)
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        redisTemplate.opsForValue().set(RedisKeyFactory.classStatus(classId), clazz.getStatus().name(), Duration.ofMinutes(5));

        log.info("reconciled classId={}, enrolled={}, waitlist={}", classId, enrolled.size(), waitlisted.size());
    }

    /**
     * apply Lua 와 동일한 score 공식.
     * Issue #55 에서 EnrollmentMirrorService.scoreOf(Instant) 로 추출 예정.
     * 그 전까지는 local helper 로 임시 운용.
     */
    static long scoreOf(long epochSec, int nano) {
        return epochSec * 1_000_000_000L + nano;
    }
}
