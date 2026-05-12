// task 02 에서 등록된 RedisScript 3개를 호출하는 thin wrapper. apply / cancelAndMaybePromote / compensateApply / primeClassStatusMirror 메서드 제공.
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.ClassStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Thin wrapper around the three Lua RedisScripts registered in LuaScriptConfig (task 02).
 * Method bodies for tryApply, compensateApply, and cancelAndMaybePromote are stubs —
 * full implementations are added in task 09/10.
 */
@Service
public class EnrollmentMirrorService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> enrollmentApplyScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enrollmentCancelPromoteScript;
    private final RedisScript<Long> enrollmentCompensateScript;

    @SuppressWarnings("rawtypes")
    public EnrollmentMirrorService(
            StringRedisTemplate redisTemplate,
            RedisScript<String> enrollmentApplyScript,
            RedisScript<List> enrollmentCancelPromoteScript,
            RedisScript<Long> enrollmentCompensateScript) {
        this.redisTemplate = redisTemplate;
        this.enrollmentApplyScript = enrollmentApplyScript;
        this.enrollmentCancelPromoteScript = enrollmentCancelPromoteScript;
        this.enrollmentCompensateScript = enrollmentCompensateScript;
    }

    /**
     * Calls enrollment_apply.lua — returns "PENDING", "WAITLISTED", "DUPLICATE_ACTIVE",
     * "CLASS_NOT_FOUND_IN_MIRROR", or "CLASS_NOT_OPEN".
     * Full implementation in task 09.
     */
    public String tryApply(UUID classId, UUID classmateId, long appliedAtNanos, int capacity) {
        throw new UnsupportedOperationException("implemented in task 09");
    }

    /**
     * Calls enrollment_compensate.lua to undo a ZSET entry after a failed DB INSERT.
     * Full implementation in task 09.
     */
    public void compensateApply(UUID classId, UUID classmateId) {
        throw new UnsupportedOperationException("implemented in task 09");
    }

    /**
     * Calls enrollment_cancel_promote.lua — atomically removes the cancelled member and
     * promotes the oldest WAITLISTED member.
     * Returns null if no promotion occurred, or a two-element list [promotedClassmateId, scoreNanos].
     * Full implementation in task 10.
     */
    @SuppressWarnings("rawtypes")
    public List<String> cancelAndMaybePromote(UUID classId, UUID classmateId, boolean wasConfirmed) {
        throw new UnsupportedOperationException("implemented in task 10");
    }

    /**
     * Primes the class:status mirror key so that enrollment_apply.lua can read it.
     * TTL matches ARCHITECTURE §4.4 key convention (300s = 5 minutes).
     */
    public void primeClassStatusMirror(UUID classId, ClassStatus status) {
        redisTemplate.opsForValue().set("class:status:" + classId, status.name(), Duration.ofMinutes(5));
    }
}
