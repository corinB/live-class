// UserController 의 POST /api/users 엔드포인트를 MockMvc standaloneSetup 으로 검증하는 슬라이스 테스트.
package com.example.liveclass.web.user;

import com.example.liveclass.application.user.UserApplicationService;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.web.error.GlobalExceptionHandler;
import com.example.liveclass.web.user.dto.RegisterUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserApplicationService userApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void postUsers_validRequest_returns201WithUserResponse() throws Exception {
        User savedUser = User.register(UserRole.CREATOR, "alice", Instant.now());
        when(userApplicationService.register(eq(UserRole.CREATOR), eq("alice")))
                .thenReturn(savedUser);

        RegisterUserRequest req = new RegisterUserRequest("alice", UserRole.CREATOR);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.role").value("CREATOR"))
                .andExpect(jsonPath("$.name").value("alice"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void postUsers_emptyBody_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
