// Redis 키 네이밍을 한 곳에 모아 prefix 변경 누락을 방지하는 정적 팩토리
package com.example.liveclass.infrastructure;

import java.util.UUID;

/**
 * Central registry of every Redis key used by live-class.
 * Keep all `"prefix:" + UUID` string construction here so a future prefix
 * change touches a single file and CAN be searched by call site.
 */
public final class RedisKeyFactory {

    private RedisKeyFactory() {
        // static factory
    }

    public static String enrolled(UUID classId) {
        return "enrolled:" + classId;
    }

    public static String waitlist(UUID classId) {
        return "waitlist:" + classId;
    }

    public static String classStatus(UUID classId) {
        return "class:status:" + classId;
    }

    /** classId 단위 reconcile/apply/cancel 직렬화 락 키. */
    public static String classReconcileLock(UUID classId) {
        return "lock:reconcile:" + classId;
    }
}
