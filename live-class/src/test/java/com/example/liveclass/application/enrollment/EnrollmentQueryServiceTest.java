// EnrollmentQueryService 통합 테스트 — Creator 수강생 목록·권한 분기·my-enrollments 상태 필터 검증
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import com.example.liveclass.web.enrollment.dto.StudentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class EnrollmentQueryServiceTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private EnrollmentQueryService enrollmentQueryService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID creatorId;
    private UUID otherCreatorId;
    private UUID classmateId;
    private UUID classId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.register(UserRole.CREATOR, "Creator A", Instant.now()));
        creatorId = creator.getId();

        User otherCreator = userRepository.save(User.register(UserRole.CREATOR, "Creator B", Instant.now()));
        otherCreatorId = otherCreator.getId();

        User classmate = userRepository.save(User.register(UserRole.CLASSMATE, "Student A", Instant.now()));
        classmateId = classmate.getId();

        Class clazz = Class.draft(
                UserId.of(creatorId),
                "Test Class",
                "desc",
                Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW")),
                Capacity.of(50),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(30)),
                Instant.now()
        );
        classRepository.save(clazz);
        classId = clazz.getId();
    }

    // ─── listStudents ────────────────────────────────────────────────────────────

    @Test
    void listStudents_nonCreatorRequester_throws403() {
        assertThatThrownBy(() ->
                enrollmentQueryService.listStudents(classId, otherCreatorId, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void listStudents_confirmed30_waitlisted5_cancelled3_returnsConfirmedOnly() {
        // Insert 30 CONFIRMED enrollments
        for (int i = 0; i < 30; i++) {
            User cm = userRepository.save(
                    User.register(UserRole.CLASSMATE, "Classmate " + i, Instant.now()));
            Enrollment e = Enrollment.apply(
                    new ClassId(classId),
                    new UserId(cm.getId()),
                    Instant.now().plusMillis(i));
            e.confirm(Instant.now().plusMillis(i + 1));
            enrollmentRepository.save(e);
        }

        // Insert 5 WAITLISTED enrollments
        for (int i = 0; i < 5; i++) {
            User cm = userRepository.save(
                    User.register(UserRole.CLASSMATE, "Waitlist " + i, Instant.now()));
            enrollmentRepository.save(Enrollment.waitlist(
                    new ClassId(classId),
                    new UserId(cm.getId()),
                    Instant.now().plusMillis(1000 + i)));
        }

        // Insert 3 CANCELLED enrollments
        for (int i = 0; i < 3; i++) {
            User cm = userRepository.save(
                    User.register(UserRole.CLASSMATE, "Cancelled " + i, Instant.now()));
            Enrollment e = Enrollment.apply(
                    new ClassId(classId),
                    new UserId(cm.getId()),
                    Instant.now().plusMillis(2000 + i));
            e.cancel(Instant.now().plusMillis(2001 + i));
            enrollmentRepository.save(e);
        }

        Page<StudentResponse> page = enrollmentQueryService.listStudents(
                classId, creatorId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(30);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(10);
    }

    // ─── listMyEnrollments ────────────────────────────────────────────────────────

    @Test
    void listMyEnrollments_statusFilter_excludesCancelled() {
        // Insert CONFIRMED enrollment
        Enrollment confirmed = Enrollment.apply(
                new ClassId(classId),
                new UserId(classmateId),
                Instant.now());
        confirmed.confirm(Instant.now().plusMillis(1));
        enrollmentRepository.save(confirmed);

        // Insert CANCELLED enrollment for same classmate in a different class
        User anotherCreator = userRepository.save(
                User.register(UserRole.CREATOR, "Another Creator", Instant.now()));
        Class anotherClass = Class.draft(
                UserId.of(anotherCreator.getId()),
                "Another Class",
                null,
                Money.of(BigDecimal.valueOf(5000), Currency.getInstance("KRW")),
                Capacity.of(10),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(10)),
                Instant.now()
        );
        classRepository.save(anotherClass);

        Enrollment cancelled = Enrollment.apply(
                new ClassId(anotherClass.getId()),
                new UserId(classmateId),
                Instant.now().plusMillis(100));
        cancelled.cancel(Instant.now().plusMillis(200));
        enrollmentRepository.save(cancelled);

        // Filter: PENDING + CONFIRMED only
        Page<EnrollmentResponse> page = enrollmentQueryService.listMyEnrollments(
                classmateId,
                Set.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    void listMyEnrollments_noFilter_returnsAll() {
        // CONFIRMED
        Enrollment confirmed = Enrollment.apply(
                new ClassId(classId),
                new UserId(classmateId),
                Instant.now());
        confirmed.confirm(Instant.now().plusMillis(1));
        enrollmentRepository.save(confirmed);

        // Separate class for CANCELLED (same classId would violate partial unique index with CONFIRMED)
        User anotherCreator = userRepository.save(
                User.register(UserRole.CREATOR, "Creator X", Instant.now()));
        Class anotherClass = Class.draft(
                UserId.of(anotherCreator.getId()),
                "Class X",
                null,
                Money.of(BigDecimal.valueOf(5000), Currency.getInstance("KRW")),
                Capacity.of(10),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(10)),
                Instant.now()
        );
        classRepository.save(anotherClass);

        Enrollment cancelled = Enrollment.apply(
                new ClassId(anotherClass.getId()),
                new UserId(classmateId),
                Instant.now().plusMillis(100));
        cancelled.cancel(Instant.now().plusMillis(200));
        enrollmentRepository.save(cancelled);

        Page<EnrollmentResponse> page = enrollmentQueryService.listMyEnrollments(
                classmateId, Set.of(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
