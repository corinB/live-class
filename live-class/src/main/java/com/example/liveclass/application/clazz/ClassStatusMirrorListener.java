// Class 상태 전이가 커밋된 직후 Redis class:status:{id} 미러를 갱신하는 트랜잭션 이벤트 리스너.
package com.example.liveclass.application.clazz;

import com.example.liveclass.domain.clazz.ClassStatus;
import com.example.liveclass.domain.clazz.event.ClassClosedEvent;
import com.example.liveclass.domain.clazz.event.ClassOpenedEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

@Component
public class ClassStatusMirrorListener {

    private static final String KEY_PREFIX = "class:status:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public ClassStatusMirrorListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOpened(ClassOpenedEvent event) {
        write(event.classId().value().toString(), ClassStatus.OPEN);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClosed(ClassClosedEvent event) {
        write(event.classId().value().toString(), ClassStatus.CLOSED);
    }

    private void write(String classId, ClassStatus status) {
        redisTemplate.opsForValue().set(KEY_PREFIX + classId, status.name(), TTL);
    }
}
