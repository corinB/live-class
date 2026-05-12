// ClassApplicationService 상태 전이 통합 테스트 — DRAFT→OPEN→CLOSED 정상 흐름과 역방향 전이 실패 검증
package com.example.liveclass.application.clazz;

import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassStatus;
import com.example.liveclass.domain.clazz.IllegalStateTransitionException;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import com.example.liveclass.web.clazz.dto.CreateClassRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class ClassApplicationServiceTransitionTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private ClassApplicationService classApplicationService;

    @Autowired
    private UserRepository userRepository;

    private UUID persistCreator() {
        User creator = User.register(UserRole.CREATOR, "Test Creator", Instant.now());
        return userRepository.save(creator).getId();
    }

    private CreateClassRequest validCreateRequest() {
        return new CreateClassRequest(
                "Transition Test Class",
                "desc",
                BigDecimal.valueOf(5000),
                "KRW",
                20,
                LocalDate.now(),
                LocalDate.now().plusDays(10)
        );
    }

    @Test
    void draftToOpenToClosed_happyPath() {
        UUID creatorId = persistCreator();

        // DRAFT
        Class draft = classApplicationService.createDraft(creatorId, validCreateRequest());
        assertThat(draft.getStatus()).isEqualTo(ClassStatus.DRAFT);

        UUID classId = draft.getId();

        // DRAFT → OPEN
        Class opened = classApplicationService.transitionStatus(classId, creatorId, ClassStatus.OPEN);
        assertThat(opened.getStatus()).isEqualTo(ClassStatus.OPEN);

        // OPEN → CLOSED
        Class closed = classApplicationService.transitionStatus(classId, creatorId, ClassStatus.CLOSED);
        assertThat(closed.getStatus()).isEqualTo(ClassStatus.CLOSED);
    }

    @Test
    void closedToOpen_throwsIllegalStateTransition_and_returns409() {
        UUID creatorId = persistCreator();

        Class draft = classApplicationService.createDraft(creatorId, validCreateRequest());
        UUID classId = draft.getId();

        classApplicationService.transitionStatus(classId, creatorId, ClassStatus.OPEN);
        classApplicationService.transitionStatus(classId, creatorId, ClassStatus.CLOSED);

        // CLOSED → OPEN must throw IllegalStateTransitionException
        assertThatThrownBy(() ->
                classApplicationService.transitionStatus(classId, creatorId, ClassStatus.OPEN))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("CLOSED");
    }
}
