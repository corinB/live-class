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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ReconcileService {

    private static final List<EnrollmentStatus> ENROLLED_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);
    private static final List<EnrollmentStatus> WAITLIST_STATUSES =
            List.of(EnrollmentStatus.WAITLISTED);

    /** classId당 한 번에 하나의 reconcile만 돌아가도록 보호하는 분산락의 TTL. */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    /** SET NX 후 토큰 일치 확인 후에만 DEL하는 안전 해제 Lua. */
    private static final RedisScript<Long> SAFE_UNLOCK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StringRedisTemplate redisTemplate;

    public ReconcileService(ClassRepository classRepository,
                            EnrollmentRepository enrollmentRepository,
                            StringRedisTemplate redisTemplate) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * DB 활성 enrollment 를 바탕으로 ZSET 과 class:status mirror 를 재구성한다.
     * DEL + 배치 ZADD 패턴으로 멱등성을 보장한다.
     * score = epochSec * 1_000_000_000L + nano (apply Lua 와 동일 공식)
     *
     * 동시성 보호 — classId 별 `lock:reconcile:{classId}` 분산락(SET NX PX, 토큰 기반 안전 해제)을
     * 획득한 뒤에만 본체를 수행한다. 두 reconcile가 동시에 같은 classId에 들어와도 한쪽만 진행하고
     * 다른 쪽은 즉시 false 리턴 + 경고 로그. 락 획득 실패는 상위 호출자(ApplicationRunner / Admin
     * 컨트롤러)가 판단해서 재시도 또는 503 응답으로 처리한다.
     *
     * 잔존 위험 (P1 부분 해소). 이 락은 reconcile 끼리만 직렬화한다. apply Lua → DB COMMIT 사이의
     * 윈도우에 reconcile이 들어오면 ZSET 신규 멤버가 손실되는 race는 여전하다. 완전 해소는 apply/
     * cancel 측 outer-wrap 락 도입이 필요하며 별도 follow-up 으로 분리한다.
     *
     * @return 락을 획득해서 실제로 재구성을 수행했는지 여부.
     */
    public boolean reconcileOne(UUID classId) {
        String lockKey = "lock:reconcile:" + classId;
        String token = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("reconcile lock busy classId={} — skipping", classId);
            return false;
        }

        try {
            doReconcileOne(classId);
            return true;
        } finally {
            try {
                redisTemplate.execute(SAFE_UNLOCK, Collections.singletonList(lockKey), token);
            } catch (Exception ex) {
                log.warn("reconcile lock safe-unlock failed classId={}: {}", classId, ex.getMessage());
            }
        }
    }

    private void doReconcileOne(UUID classId) {
        String enrolledKey = "enrolled:" + classId;
        String waitlistKey = "waitlist:" + classId;

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
        redisTemplate.opsForValue().set("class:status:" + classId, clazz.getStatus().name(), Duration.ofMinutes(5));

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
