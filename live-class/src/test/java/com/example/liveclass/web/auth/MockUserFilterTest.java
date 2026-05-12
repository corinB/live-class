// MockUserFilter 의 401/400/200 경로를 검증하는 슬라이스 테스트
package com.example.liveclass.web.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MockUserFilterTest {

    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/api/test")
    static class DummyController {
        @GetMapping
        public String hello() {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyController())
                .addFilters(new MockUserFilter())
                .build();
    }

    @Test
    void missingHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/test").header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void validUuid_returns200() throws Exception {
        mockMvc.perform(get("/api/test").header("X-User-Id", "123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(status().isOk());
    }
}
