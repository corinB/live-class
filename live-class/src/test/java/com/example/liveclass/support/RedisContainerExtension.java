// Testcontainers Redis 컨테이너를 JUnit5 확장으로 제공하고 spring.data.redis 속성을 동적으로 주입하는 확장
package com.example.liveclass.support;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

public class RedisContainerExtension implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static final RedisContainer REDIS_CONTAINER =
            new RedisContainer(DockerImageName.parse("redis:7"));

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!REDIS_CONTAINER.isRunning()) {
            REDIS_CONTAINER.start();
        }
    }

    @Override
    public void close() {
        if (REDIS_CONTAINER.isRunning()) {
            REDIS_CONTAINER.stop();
        }
    }

    public static String getHost() {
        return REDIS_CONTAINER.getHost();
    }

    public static int getPort() {
        return REDIS_CONTAINER.getFirstMappedPort();
    }
}
