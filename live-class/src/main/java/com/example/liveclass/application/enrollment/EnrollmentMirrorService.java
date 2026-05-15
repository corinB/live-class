// task 02 에서 등록된 RedisScript 4개를 호출하는 thin wrapper. apply / cancelAndMaybePromote / compensateApply / reverseCancelPromote / primeClassStatusMirror 메서드 제공.
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.ClassStatus;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Thin wrapper around the four Lua RedisScripts registered in LuaScriptConfig.
 * reverseCancelPromote uses a single atomic Lua script to restore both ZSET entries
 * in one Redis round-trip, eliminating the partial-failure window that existed when
 * two separate ZADD calls were used.
 */
@Service
public class EnrollmentMirrorService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> enrollmentApplyScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enrollmentCancelPromoteScript;
    private final RedisScript<Long> enrollmentCompensateScript;
    private final RedisScript<Long> enrollmentReverseCancelPromoteScript;

    @SuppressWarnings("rawtypes")
    public EnrollmentMirrorService(
            StringRedisTemplate redisTemplate,
            RedisScript<String> enrollmentApplyScript,
            RedisScript<List> enrollmentCancelPromoteScript,
            RedisScript<Long> enrollmentCompensateScript,
            RedisScript<Long> enrollmentReverseCancelPromoteScript) {
        this.redisTemplate = redisTemplate;
        this.enrollmentApplyScript = enrollmentApplyScript;
        this.enrollmentCancelPromoteScript = enrollmentCancelPromoteScript;
        this.enrollmentCompensateScript = enrollmentCompensateScript;
        this.enrollmentReverseCancelPromoteScript = enrollmentReverseCancelPromoteScript;
    }

    /**
     * Calls enrollment_apply.lua — returns "PENDING", "WAITLISTED", "DUPLICATE_ACTIVE",
     * "CLASS_NOT_FOUND", or "CLASS_NOT_OPEN".
     * Throws MirrorUnavailableException if Redis is unreachable.
     */
    public String tryApply(UUID classId, UUID classmateId, long appliedAtNanos, int capacity) {
        try {
            return redisTemplate.execute(
                    enrollmentApplyScript,
                    List.of("enrolled:" + classId, "waitlist:" + classId, "class:status:" + classId),
                    String.valueOf(capacity),
                    classmateId.toString(),
                    String.valueOf(appliedAtNanos));
        } catch (RedisConnectionFailureException | QueryTimeoutException ex) {
            throw new MirrorUnavailableException("Redis unavailable during enrollment apply", ex);
        }
    }

    /**
     * Calls enrollment_compensate.lua to undo a ZSET entry after a failed DB INSERT.
     * Throws MirrorUnavailableException if Redis is unreachable.
     */
    public void compensateApply(UUID classId, UUID classmateId) {
        try {
            redisTemplate.execute(
                    enrollmentCompensateScript,
                    List.of("enrolled:" + classId, "waitlist:" + classId),
                    classmateId.toString());
        } catch (RedisConnectionFailureException | QueryTimeoutException ex) {
            throw new MirrorUnavailableException("Redis unavailable during enrollment compensate", ex);
        }
    }

    /**
     * Calls enrollment_cancel_promote.lua — atomically removes the cancelled member and
     * promotes the oldest WAITLISTED member.
     * Returns null if no promotion occurred, or a two-element list [promotedClassmateId, scoreNanos].
     * Throws MirrorUnavailableException if Redis is unreachable.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<String> cancelAndMaybePromote(UUID classId, UUID classmateId, boolean wasConfirmed) {
        try {
            return (List<String>) redisTemplate.execute(
                    enrollmentCancelPromoteScript,
                    List.of("enrolled:" + classId, "waitlist:" + classId),
                    classmateId.toString(),
                    wasConfirmed ? "1" : "0");
        } catch (RedisConnectionFailureException | QueryTimeoutException ex) {
            throw new MirrorUnavailableException("Redis unavailable during cancel promote", ex);
        }
    }

    /**
     * Reverse compensation after a failed promoted-enrollment DB UPDATE.
     * Single atomic Lua call: restores canceller to enrolled ZSET and (if promoted is non-null)
     * removes promoted from enrolled and restores it to waitlist — all in one Redis round-trip.
     */
    public void reverseCancelPromote(UUID classId, UUID canceller, UUID promoted, long promotedScore) {
        try {
            long cancellerScore = System.nanoTime();
            String promotedArg = promoted != null ? promoted.toString() : "";
            redisTemplate.execute(
                    enrollmentReverseCancelPromoteScript,
                    List.of("enrolled:" + classId, "waitlist:" + classId),
                    canceller.toString(),
                    String.valueOf(cancellerScore),
                    promotedArg,
                    String.valueOf(promotedScore),
                    "1");
        } catch (RedisConnectionFailureException | QueryTimeoutException ex) {
            throw new MirrorUnavailableException("Redis unavailable during reverse cancel promote", ex);
        }
    }

    /**
     * Primes the class:status mirror key so that enrollment_apply.lua can read it.
     * TTL matches ARCHITECTURE §4.4 key convention (300s = 5 minutes).
     */
    public void primeClassStatusMirror(UUID classId, ClassStatus status) {
        redisTemplate.opsForValue().set("class:status:" + classId, status.name(), Duration.ofMinutes(5));
    }
}
