// GET /{id}/students 슬라이스 테스트 — 권한 분기(403), 404, 정상 페이지 응답 구조 검증
package com.example.liveclass.web.clazz;

import com.example.liveclass.application.clazz.ClassApplicationService;
import com.example.liveclass.application.enrollment.EnrollmentQueryService;
import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
import com.example.liveclass.domain.clazz.ClassNotFoundException;
import com.example.liveclass.web.auth.CurrentUserArgumentResolver;
import com.example.liveclass.web.enrollment.dto.StudentResponse;
import com.example.liveclass.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StudentsControllerSliceTest {

    private MockMvc mockMvc;

    @Mock
    private ClassApplicationService classApplicationService;

    @Mock
    private EnrollmentQueryService enrollmentQueryService;

    private static final UUID CREATOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OTHER_USER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID CLASS_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClassController(classApplicationService, enrollmentQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new CurrentUserArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    // ─── Scenario 1: 비 Creator 요청 → 403 ────────────────────────────────────

    @Test
    void nonCreator_getStudents_returns403() throws Exception {
        when(enrollmentQueryService.listStudents(eq(CLASS_ID), eq(OTHER_USER_ID), any()))
                .thenThrow(new AccessDeniedDomainException("Only the class creator can view the student list"));

        mockMvc.perform(get("/api/classes/" + CLASS_ID + "/students")
                        .requestAttr("currentUserId", OTHER_USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ─── Scenario 2: 강의 없음 → 404 ─────────────────────────────────────────

    @Test
    void unknownClass_getStudents_returns404() throws Exception {
        when(enrollmentQueryService.listStudents(eq(CLASS_ID), eq(CREATOR_ID), any()))
                .thenThrow(new ClassNotFoundException(CLASS_ID));

        mockMvc.perform(get("/api/classes/" + CLASS_ID + "/students")
                        .requestAttr("currentUserId", CREATOR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CLASS_NOT_FOUND"));
    }

    // ─── Scenario 3: 정상 응답 — content/totalElements/totalPages/page/size 포함 ─

    @Test
    void creator_getStudents_returnsPageStructure() throws Exception {
        UUID classmateId = UUID.randomUUID();
        StudentResponse student = new StudentResponse(
                UUID.randomUUID(), classmateId, "Student A", Instant.now());

        when(enrollmentQueryService.listStudents(eq(CLASS_ID), eq(CREATOR_ID), any()))
                .thenReturn(new PageImpl<>(List.of(student), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/classes/" + CLASS_ID + "/students")
                        .requestAttr("currentUserId", CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].classmateId").value(classmateId.toString()))
                .andExpect(jsonPath("$.content[0].classmateName").value("Student A"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }
}
