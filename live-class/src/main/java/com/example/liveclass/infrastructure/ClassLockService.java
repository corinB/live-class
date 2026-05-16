// classId 단위 Redis 분산락을 제공하는 공용 서비스 — reconcile·apply·cancel 간 race 방지
package com.example.liveclass.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * classId 별 `lock:reconcile:{classId}` 분산락(SET NX PX, 토큰 기반 안전 해제)을 통해
 * Redis ZSET 정합성을 깰 수 있는 임계 구간을 직렬화한다.
 *
 * <p>적용 지점.
 * <ul>
 *   <li>ReconcileService.reconcileOne — DEL + DB 조회 + ZADD 시퀀스 전체.</li>
 *   <li>EnrollmentApplicationService.apply / cancel — Lua ZADD/ZREM + DB COMMIT 전체.</li>
 * </ul>
 *
 * <p>같은 키를 공유하므로 apply 가 진행 중이면 reconcile 는 즉시 false 리턴, reconcile 가 잡고 있으면
 * apply 는 ClassLockBusyException(=MirrorUnavailableException) 으로 503 매핑된다.
 */
@Slf4j
@Service
public class ClassLockService {

    /** TTL — apply/cancel/reconcile 어느 쪽이든 30초 내에 끝나야 한다 (DB timeout 2초보다 훨씬 크게). */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** SET NX 후 토큰 일치 확인 후에만 DEL 하는 안전 해제 Lua. 다른 holder 의 락은 절대 해제하지 않는다. */
    private static final RedisScript<Long> SAFE_UNLOCK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public ClassLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 락을 즉시 획득해 본체를 실행하고, 끝나면 안전 해제한다.
     * 락 충돌 시 즉시 false 리턴 (호출자가 재시도 또는 skip 결정). reconcile 측에서 사용.
     */
    public boolean tryRun(UUID classId, Runnable body) {
        String key = keyFor(classId);
        String token = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("class lock busy classId={} (tryRun skipped)", classId);
            return false;
        }
        try {
            body.run();
            return true;
        } finally {
            safeUnlock(key, token);
        }
    }

    /**
     * 락을 즉시 획득해 본체를 실행하고 결과를 리턴, 끝나면 안전 해제한다.
     * 락 충돌 시 ClassLockBusyException 던진다 (호출자가 적절한 HTTP 코드로 매핑). apply/cancel 측에서 사용.
     */
    public <T> T executeWithLock(UUID classId, Supplier<T> body) {
        String key = keyFor(classId);
        String token = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ClassLockBusyException(classId);
        }
        try {
            return body.get();
        } finally {
            safeUnlock(key, token);
        }
    }

    private void safeUnlock(String key, String token) {
        try {
            redisTemplate.execute(SAFE_UNLOCK, Collections.singletonList(key), token);
        } catch (Exception ex) {
            log.warn("class lock safe-unlock failed key={}: {}", key, ex.getMessage());
        }
    }

    private static String keyFor(UUID classId) {
        return "lock:reconcile:" + classId;
    }
}
