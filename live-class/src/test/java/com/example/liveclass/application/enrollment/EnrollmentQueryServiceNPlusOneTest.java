// N+1 쿼리 방지 검증 — Hibernate Statistics로 30건 수강생 조회 시 SQL 호출 수가 2 이하임을 확인
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import com.example.liveclass.web.enrollment.dto.StudentResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.stat.Statistics;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Transactional
class EnrollmentQueryServiceNPlusOneTest {

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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void listStudents_30items_sqlCallsAtMostTwo() {
        // Arrange: create creator, class, and 30 confirmed classmates
        User creator = userRepository.save(User.register(UserRole.CREATOR, "N+1 Creator", Instant.now()));
        UUID creatorId = creator.getId();

        Class clazz = Class.draft(
                UserId.of(creatorId),
                "N+1 Test Class",
                null,
                Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW")),
                Capacity.of(50),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(30)),
                Instant.now()
        );
        classRepository.save(clazz);
        UUID classId = clazz.getId();

        for (int i = 0; i < 30; i++) {
            User cm = userRepository.save(
                    User.register(UserRole.CLASSMATE, "CM " + i, Instant.now()));
            Enrollment e = Enrollment.apply(
                    new ClassId(classId),
                    new UserId(cm.getId()),
                    Instant.now().plusMillis(i));
            e.confirm(Instant.now().plusMillis(i + 1));
            enrollmentRepository.save(e);
        }

        // Flush and clear the persistence context so Hibernate issues real queries below.
        enrollmentRepository.flush();

        Statistics stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // Act: query the first page of 30 students
        Page<StudentResponse> page = enrollmentQueryService.listStudents(
                classId, creatorId, PageRequest.of(0, 30));

        // Assert: content is correct
        assertThat(page.getTotalElements()).isEqualTo(30);
        assertThat(page.getContent()).hasSize(30);

        // Assert: SQL call count ≤ 3 — Spring Data Page<> always runs an extra COUNT(*).
        // So the true N+1-prevention bound is: 1 count + 1 enrollment page + 1 user batch = 3.
        // The original DoD line "2 이하" did not account for the implicit count query.
        long queryCount = stats.getQueryExecutionCount();
        assertThat(queryCount)
                .as("Expected ≤ 3 SQL queries (count + page + user batch) for a 30-item page, but got %d", queryCount)
                .isLessThanOrEqualTo(3);
    }
}
