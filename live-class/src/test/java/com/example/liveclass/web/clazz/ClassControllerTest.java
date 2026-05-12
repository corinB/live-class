// ClassController 의 4 개 엔드포인트를 MockMvc standaloneSetup 으로 검증하는 슬라이스 테스트.
package com.example.liveclass.web.clazz;

import com.example.liveclass.application.clazz.ClassApplicationService;
import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassNotFoundException;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassStatus;
import com.example.liveclass.domain.clazz.ConcurrentClassUpdateException;
import com.example.liveclass.domain.clazz.IllegalStateTransitionException;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.web.auth.CurrentUserArgumentResolver;
import com.example.liveclass.web.clazz.dto.ChangeStatusRequest;
import com.example.liveclass.web.clazz.dto.CreateClassRequest;
import com.example.liveclass.web.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClassControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassApplicationService classApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID CREATOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClassController(classApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    private Class draftClass() {
        return Class.draft(
                UserId.of(CREATOR_ID),
                "Spring Boot 4 마스터",
                "라이브 강의",
                Money.of(BigDecimal.valueOf(50000), Currency.getInstance("KRW")),
                Capacity.of(30),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(14)),
                Instant.now()
        );
    }

    @Test
    void postClasses_validRequest_returns201() throws Exception {
        when(classApplicationService.createDraft(eq(CREATOR_ID), any(CreateClassRequest.class)))
                .thenReturn(draftClass());

        CreateClassRequest req = new CreateClassRequest(
                "Spring Boot 4 마스터",
                "라이브 강의",
                BigDecimal.valueOf(50000),
                "KRW",
                30,
                LocalDate.now(),
                LocalDate.now().plusDays(14)
        );

        mockMvc.perform(post("/api/classes")
                        .requestAttr("currentUserId", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Spring Boot 4 마스터"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.capacity").value(30));
    }

    @Test
    void postClasses_blankTitle_returns400() throws Exception {
        CreateClassRequest req = new CreateClassRequest(
                "",
                "desc",
                BigDecimal.valueOf(10000),
                "KRW",
                10,
                LocalDate.now(),
                LocalDate.now().plusDays(1)
        );

        mockMvc.perform(post("/api/classes")
                        .requestAttr("currentUserId", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchStatus_open_returns200() throws Exception {
        UUID classId = UUID.randomUUID();
        Class opened = draftClass();
        opened.open(UserId.of(CREATOR_ID), Instant.now());

        when(classApplicationService.transitionStatus(eq(classId), eq(CREATOR_ID), eq(ClassStatus.OPEN)))
                .thenReturn(opened);

        ChangeStatusRequest req = new ChangeStatusRequest(ClassStatus.OPEN);

        mockMvc.perform(patch("/api/classes/" + classId + "/status")
                        .requestAttr("currentUserId", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void patchStatus_illegalTransition_returns409() throws Exception {
        UUID classId = UUID.randomUUID();
        when(classApplicationService.transitionStatus(eq(classId), eq(CREATOR_ID), eq(ClassStatus.CLOSED)))
                .thenThrow(new IllegalStateTransitionException("Class can only close from OPEN, but was DRAFT"));

        ChangeStatusRequest req = new ChangeStatusRequest(ClassStatus.CLOSED);

        mockMvc.perform(patch("/api/classes/" + classId + "/status")
                        .requestAttr("currentUserId", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void patchStatus_concurrentUpdate_returns409() throws Exception {
        UUID classId = UUID.randomUUID();
        when(classApplicationService.transitionStatus(eq(classId), eq(CREATOR_ID), eq(ClassStatus.OPEN)))
                .thenThrow(new ConcurrentClassUpdateException());

        ChangeStatusRequest req = new ChangeStatusRequest(ClassStatus.OPEN);

        mockMvc.perform(patch("/api/classes/" + classId + "/status")
                        .requestAttr("currentUserId", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_CLASS_UPDATE"));
    }

    @Test
    void getById_returns200() throws Exception {
        UUID classId = UUID.randomUUID();
        when(classApplicationService.getById(classId)).thenReturn(draftClass());

        mockMvc.perform(get("/api/classes/" + classId)
                        .requestAttr("currentUserId", CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot 4 마스터"));
    }

    @Test
    void getById_unknown_returns404() throws Exception {
        UUID classId = UUID.randomUUID();
        when(classApplicationService.getById(classId)).thenThrow(new ClassNotFoundException(classId));

        mockMvc.perform(get("/api/classes/" + classId)
                        .requestAttr("currentUserId", CREATOR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CLASS_NOT_FOUND"));
    }

    @Test
    void list_returns200() throws Exception {
        when(classApplicationService.listOpenClasses(any()))
                .thenReturn(new PageImpl<>(List.of(draftClass()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/classes")
                        .requestAttr("currentUserId", CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot 4 마스터"));
    }
}
