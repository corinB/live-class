// RedisTemplate, CacheManager, Lua 스크립트 3개 빈의 컨텍스트 로드를 검증하는 통합 테스트
package com.example.liveclass.config;

import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisConfigTest {

    @RegisterExtension
    static RedisContainerExtension redisContainer = new RedisContainerExtension();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisContainerExtension::getHost);
        registry.add("spring.data.redis.port", RedisContainerExtension::getPort);
    }

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    @Autowired
    private DefaultRedisScript<String> enrollmentApplyScript;

    @Autowired
    @SuppressWarnings("rawtypes")
    private DefaultRedisScript<List> enrollmentCancelPromoteScript;

    @Autowired
    private DefaultRedisScript<Long> enrollmentCompensateScript;

    @Test
    void allBeansArePresent() {
        assertThat(stringRedisTemplate).isNotNull();
        assertThat(enrollmentApplyScript).isNotNull();
        assertThat(enrollmentCancelPromoteScript).isNotNull();
        assertThat(enrollmentCompensateScript).isNotNull();
    }

    @Test
    void allLuaScriptBeansLoaded() {
        assertThat(enrollmentApplyScript.getScriptAsString()).contains("enrollment_apply.lua");
        assertThat(enrollmentCancelPromoteScript.getScriptAsString()).contains("enrollment_cancel_promote.lua");
        assertThat(enrollmentCompensateScript.getScriptAsString()).contains("enrollment_compensate.lua");
    }

    @Test
    void noRedissonBeanPresent() {
        assertThat(ctx.getBeansOfType(Object.class).keySet())
                .noneMatch(name -> name.toLowerCase().contains("redisson"));
    }

    @Test
    void noCustomCacheManagerBean() {
        // Pre-flight 5 결정: Spring Cache 추상화는 사용하지 않는다. RedisCacheManager 빈을 정의하지 않으며,
        // Spring Boot 의 auto-config 도 `spring.cache.type` 미설정으로 활성화되지 않는다.
        assertThatThrownBy(() -> ctx.getBean("liveClassCacheManager"))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
