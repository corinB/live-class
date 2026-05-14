// PongController 의 GET /api/pong 엔드포인트를 MockMvc standaloneSetup 으로 검증하는 슬라이스 테스트.
package com.example.liveclass.web.pong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PongControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PongController())
                .build();
    }

    @Test
    void pong_returns200WithPingTrue_withoutAuthHeader() throws Exception {
        mockMvc.perform(get("/api/pong"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ping").value(true));
    }
}
