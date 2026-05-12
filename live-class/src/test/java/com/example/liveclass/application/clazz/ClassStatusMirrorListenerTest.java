// ClassStatusMirrorListener 단위 테스트 — Testcontainers Redis 위에 listener 인스턴스 직접 호출 (Spring transaction 매커니즘 우회)
package com.example.liveclass.application.clazz;

import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.event.ClassClosedEvent;
import com.example.liveclass.domain.clazz.event.ClassOpenedEvent;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(RedisContainerExtension.class)
class ClassStatusMirrorListenerTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private ClassStatusMirrorListener listener;

    @BeforeAll
    static void startInfrastructure() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                RedisContainerExtension.getHost(),
                RedisContainerExtension.getPort()
        );
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopInfrastructure() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        listener = new ClassStatusMirrorListener(redisTemplate);
    }

    @Test
    void onOpened_setsRedisMirrorToOpen() {
        UUID classId = UUID.randomUUID();
        String key = "class:status:" + classId;
        redisTemplate.delete(key);

        // @TransactionalEventListener 의 transaction 매커니즘은 Spring 의 검증된 기능이므로 우회.
        // listener 의 핵심 책임(Redis SET)만 직접 검증.
        listener.onOpened(new ClassOpenedEvent(
                ClassId.of(classId), UserId.of(UUID.randomUUID()), Instant.now()));

        assertEquals("OPEN", redisTemplate.opsForValue().get(key));
    }

    @Test
    void onClosed_setsRedisMirrorToClosed() {
        UUID classId = UUID.randomUUID();
        String key = "class:status:" + classId;
        redisTemplate.delete(key);

        listener.onClosed(new ClassClosedEvent(
                ClassId.of(classId), UserId.of(UUID.randomUUID()), Instant.now()));

        assertEquals("CLOSED", redisTemplate.opsForValue().get(key));
    }
}
