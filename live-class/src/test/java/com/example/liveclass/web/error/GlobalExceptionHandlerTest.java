// GlobalExceptionHandler 가 DomainException 과 Redis 다운 예외를 ProblemDetail 형식으로 응답하는지 검증하는 슬라이스 테스트
package com.example.liveclass.web.error;

import com.example.liveclass.domain.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    static class SampleDomainException extends DomainException {
        SampleDomainException() {
            super("SAMPLE_ERROR", HttpStatus.UNPROCESSABLE_ENTITY, "Sample domain error occurred");
        }
    }

    @RestController
    @RequestMapping("/api/test-error")
    static class DummyController {
        @GetMapping("/domain")
        public String domainFail() {
            throw new SampleDomainException();
        }

        @GetMapping("/redis-timeout")
        public String redisTimeoutFail() {
            throw new QueryTimeoutException("simulated redis command timeout");
        }

        @GetMapping("/redis-conn")
        public String redisConnFail() {
            throw new RedisConnectionFailureException("simulated redis connection failure");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void domainException_returnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/test-error/domain"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Sample domain error occurred"))
                .andExpect(jsonPath("$.errorCode").value("SAMPLE_ERROR"));
    }

    @Test
    void redisQueryTimeout_returns503MirrorUnavailable() throws Exception {
        mockMvc.perform(get("/api/test-error/redis-timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.errorCode").value("MIRROR_UNAVAILABLE"));
    }

    @Test
    void redisConnectionFailure_returns503MirrorUnavailable() throws Exception {
        mockMvc.perform(get("/api/test-error/redis-conn"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.errorCode").value("MIRROR_UNAVAILABLE"));
    }
}
