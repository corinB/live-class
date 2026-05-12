// EnrollmentController 슬라이스 테스트 — 인증 헤더, body 검증, 201/202 분기 확인 (service 는 MockBean)
package com.example.liveclass.web.enrollment;

import com.example.liveclass.application.enrollment.CreatorCannotEnrollException;
import com.example.liveclass.application.enrollment.EnrollmentApplicationService;
import com.example.liveclass.application.enrollment.EnrollmentQueryService;
import com.example.liveclass.application.enrollment.MirrorUnavailableException;
import com.example.liveclass.domain.enrollment.ClassNotOpenException;
import com.example.liveclass.domain.enrollment.DuplicateEnrollmentException;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.web.auth.CurrentUserArgumentResolver;
import com.example.liveclass.web.auth.MockUserFilter;
import com.example.liveclass.web.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import com.example.liveclass.web.error.GlobalExceptionHandler;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerSliceTest {

    private MockMvc mockMvc;

    @Mock
    private EnrollmentApplicationService enrollmentApplicationService;

    @Mock
    private EnrollmentQueryService enrollmentQueryService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID CLASSMATE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CLASS_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EnrollmentController(enrollmentApplicationService, enrollmentQueryService))
                .addFilters(new MockUserFilter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    // ─── Scenario 1: 헤더 누락 → 401 ─────────────────────────────────────────
    @Test
    void missingHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(CLASS_ID))))
                .andExpect(status().isUnauthorized());
    }

    // ─── Scenario 2: body 누락 (classId null) → 400 ──────────────────────────
    @Test
    void nullClassId_returns400() throws Exception {
        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ─── Scenario 3: 정상 신청 → 201 PENDING ─────────────────────────────────
    @Test
    void validRequest_pendingResult_returns201() throws Exception {
        EnrollmentResponse mockResp = new EnrollmentResponse(
                UUID.randomUUID(), CLASS_ID, CLASSMATE_ID, EnrollmentStatus.PENDING, Instant.now(), null, null);
        when(enrollmentApplicationService.apply(eq(CLASSMATE_ID), eq(CLASS_ID), any(Instant.class)))
                .thenReturn(mockResp);

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(CLASS_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ─── Scenario 4: WAITLISTED 결과 → 202 ───────────────────────────────────
    @Test
    void validRequest_waitlistedResult_returns202() throws Exception {
        EnrollmentResponse mockResp = new EnrollmentResponse(
                UUID.randomUUID(), CLASS_ID, CLASSMATE_ID, EnrollmentStatus.WAITLISTED, Instant.now(), null, null);
        when(enrollmentApplicationService.apply(eq(CLASSMATE_ID), eq(CLASS_ID), any(Instant.class)))
                .thenReturn(mockResp);

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(CLASS_ID))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("WAITLISTED"));
    }

    // ─── Scenario 5: 중복 신청 → 409 ─────────────────────────────────────────
    @Test
    void duplicate_returns409() throws Exception {
        when(enrollmentApplicationService.apply(any(), any(), any()))
                .thenThrow(new DuplicateEnrollmentException());

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(CLASS_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ENROLLMENT"));
    }

    // ─── Scenario 6: DRAFT 강의 → 409 ────────────────────────────────────────
    @Test
    void classNotOpen_returns409() throws Exception {
        when(enrollmentApplicationService.apply(any(), any(), any()))
                .thenThrow(new ClassNotOpenException());

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(CLASS_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLASS_NOT_OPEN"));
    }

    // ─── Scenario 7: Creator 본인 신청 → 403 ─────────────────────────────────
    @Test
    void creatorSelf_returns403() throws Exception {
        when(enrollmentApplicationService.apply(any(), any(), any()))
                .thenThrow(new CreatorCannotEnrollException());

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(CLASS_ID))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CREATOR_CANNOT_ENROLL"));
    }

    // ─── Scenario 8: Redis 연결 실패 → 503 ───────────────────────────────────
    @Test
    void mirrorUnavailable_returns503() throws Exception {
        when(enrollmentApplicationService.apply(any(), any(), any()))
                .thenThrow(new MirrorUnavailableException("Redis down", new RuntimeException()));

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", CLASSMATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(CLASS_ID))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MIRROR_UNAVAILABLE"));
    }
}
