// ClassController 슬라이스 테스트 — 인증 헤더, 권한(Creator만), 입력 검증 3축 시나리오 검증
package com.example.liveclass.web.clazz;

import com.example.liveclass.application.clazz.ClassApplicationService;
import com.example.liveclass.application.enrollment.EnrollmentQueryService;
import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.web.auth.CurrentUserArgumentResolver;
import com.example.liveclass.web.auth.MockUserFilter;
import com.example.liveclass.web.clazz.dto.CreateClassRequest;
import com.example.liveclass.web.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClassControllerSliceTest {

    private MockMvc mockMvc;

    @Mock
    private ClassApplicationService classApplicationService;

    @Mock
    private EnrollmentQueryService enrollmentQueryService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID CREATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CLASSMATE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClassController(classApplicationService, enrollmentQueryService))
                .addFilters(new MockUserFilter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new CurrentUserArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private Class draftClass() {
        return Class.draft(
                UserId.of(CREATOR_ID),
                "Valid Title",
                "desc",
                Money.of(BigDecimal.valueOf(20000), Currency.getInstance("KRW")),
                Capacity.of(10),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(7)),
                Instant.now()
        );
    }

    private CreateClassRequest validRequest() {
        return new CreateClassRequest(
                "Valid Title",
                "desc",
                BigDecimal.valueOf(20000),
                "KRW",
                10,
                LocalDate.now(),
                LocalDate.now().plusDays(7)
        );
    }

    // ─── Scenario 1: 헤더 누락 → 401 ──────────────────────────────────────────

    @Test
    void missingHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    // ─── Scenario 2: CLASSMATE 역할 → 403 ────────────────────────────────────

    @Test
    void classmateRole_postClasses_returns403() throws Exception {
        when(classApplicationService.createDraft(eq(CLASSMATE_ID), any(CreateClassRequest.class)))
                .thenThrow(new AccessDeniedDomainException("Only users with CREATOR role can perform this operation"));

        mockMvc.perform(post("/api/classes")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ─── Scenario 3: 잘못된 body (price 음수, capacity 0) → 400 ProblemDetail ─

    @Test
    void negativePrice_returns400() throws Exception {
        CreateClassRequest badReq = new CreateClassRequest(
                "Title",
                null,
                BigDecimal.valueOf(-1),   // price 음수
                "KRW",
                10,
                LocalDate.now(),
                LocalDate.now().plusDays(1)
        );

        mockMvc.perform(post("/api/classes")
                        .header("X-User-Id", CREATOR_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void zeroCapacity_returns400() throws Exception {
        CreateClassRequest badReq = new CreateClassRequest(
                "Title",
                null,
                BigDecimal.valueOf(1000),
                "KRW",
                0,                        // capacity 0 (Min(1) violation)
                LocalDate.now(),
                LocalDate.now().plusDays(1)
        );

        mockMvc.perform(post("/api/classes")
                        .header("X-User-Id", CREATOR_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ─── Scenario 4: 정상 body + CREATOR 헤더 → 201 + Location ───────────────

    @Test
    void validRequest_creatorHeader_returns201WithLocation() throws Exception {
        Class draft = draftClass();
        when(classApplicationService.createDraft(eq(CREATOR_ID), any(CreateClassRequest.class)))
                .thenReturn(draft);

        mockMvc.perform(post("/api/classes")
                        .header("X-User-Id", CREATOR_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(draft.getId().toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }
}
