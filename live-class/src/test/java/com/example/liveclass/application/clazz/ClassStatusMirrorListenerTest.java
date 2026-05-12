// ClassStatusMirrorListener 가 AFTER_COMMIT 단계에서 class:status:{id} Redis 미러를 갱신하는지 검증한다.
package com.example.liveclass.application.clazz;

import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.event.ClassClosedEvent;
import com.example.liveclass.domain.clazz.event.ClassOpenedEvent;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(RedisContainerExtension.class)
@SpringBootTest(classes = ClassStatusMirrorListenerTestConfig.class)
class ClassStatusMirrorListenerTest {

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisContainerExtension::getHost);
        registry.add("spring.data.redis.port", RedisContainerExtension::getPort);
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void onOpened_afterCommit_setsRedisMirrorToOpen() {
        UUID classId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        String key = "class:status:" + classId;
        redisTemplate.delete(key);

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new ClassOpenedEvent(
                        ClassId.of(classId), UserId.of(creatorId), Instant.now())));

        assertEquals("OPEN", redisTemplate.opsForValue().get(key));
    }

    @Test
    void onClosed_afterCommit_setsRedisMirrorToClosed() {
        UUID classId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        String key = "class:status:" + classId;
        redisTemplate.delete(key);

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new ClassClosedEvent(
                        ClassId.of(classId), UserId.of(creatorId), Instant.now())));

        assertEquals("CLOSED", redisTemplate.opsForValue().get(key));
    }
}
