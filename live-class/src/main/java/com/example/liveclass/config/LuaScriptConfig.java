// Lua atomic script 3개를 classpath:lua/*.lua 에서 로드해 RedisScript 빈으로 등록
package com.example.liveclass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class LuaScriptConfig {

    @Bean
    public DefaultRedisScript<String> enrollmentApplyScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/enrollment_apply.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public DefaultRedisScript<List> enrollmentCancelPromoteScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/enrollment_cancel_promote.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> enrollmentCompensateScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/enrollment_compensate.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> enrollmentReverseCancelPromoteScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/enrollment_reverse_cancel_promote.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
