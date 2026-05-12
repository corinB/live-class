# Creator 전용 수강생 목록 + Classmate my-enrollments 페이지네이션

- **Assignee:** The Logic Implementer
- **Dependencies:** 10_Logic_Implementer_Enrollment_Confirm_Cancel_And_Waitlist_Promotion.md
- **Definition of Done (DoD):**
  - `GET /api/classes/{id}/students`이 Creator 전용으로 CONFIRMED 상태 Enrollment 목록을 페이지네이션해 반환한다 (DOCS request_flow).
  - 다른 사용자가 호출 시 403, 강의 미존재 시 404.
  - `GET /api/enrollments/me`가 호출자 본인의 Enrollment 목록(상태 필터, 페이지네이션)을 반환한다.
  - 두 endpoint 모두 Spring `Pageable`을 받고 `Page<...>` 응답에 `content`, `totalElements`, `totalPages`, `number`, `size` 포함.
  - 응답 DTO에 강의 정보(`title`, `classId`)와 사용자 정보(`classmateId`)가 함께 포함되어 N+1 쿼리 없이 fetch join 또는 단일 join 쿼리로 조회된다.

## Action Items (Checklist)

- [x] `EnrollmentRepository`에 다음 메서드 추가.
  - `@Query("select e from Enrollment e where e.classId = :classId and e.status = com.example.liveclass.domain.enrollment.EnrollmentStatus.CONFIRMED order by e.paidAt asc") Page<Enrollment> findConfirmedByClassId(UUID classId, Pageable pageable);`
  - `Page<Enrollment> findByClassmateIdOrderByAppliedAtDesc(UUID classmateId, Pageable pageable);`
  - 상태 필터 지원: `Page<Enrollment> findByClassmateIdAndStatusInOrderByAppliedAtDesc(UUID classmateId, Collection<EnrollmentStatus>, Pageable);`
- [x] `application/enrollment/EnrollmentQueryService.java` — `@Service @Transactional(readOnly=true)`.
  - `Page<StudentResponse> listStudents(UUID classId, UUID requesterId, Pageable pageable)`.
    - `Class clazz = classRepository.findById(classId).orElseThrow(ClassNotFoundException);`
    - `if (!clazz.getCreatorId().equals(requesterId)) throw new AccessDeniedDomainException();`
    - `return enrollmentRepository.findConfirmedByClassId(classId, pageable).map(StudentResponse::from);`
  - `Page<EnrollmentResponse> listMyEnrollments(UUID classmateId, Set<EnrollmentStatus> statuses, Pageable pageable)`.
    - statuses 비어있으면 전체 상태 검색.
- [x] `web/enrollment/dto/StudentResponse.java` — `record(UUID enrollmentId, UUID classmateId, String classmateName, Instant paidAt)`. classmateName은 `User` 조회 또는 `UserRepository`로 batch 조회 (N+1 방지를 위해 `In(classmateIds)` 한 번에).
- [x] `web/clazz/ClassController.java`에 `GET /{id}/students` 추가.
  - `Pageable pageable` 파라미터, `@PageableDefault(size=20, sort="paidAt")`.
- [x] `web/enrollment/EnrollmentController.java`에 `GET /me` 추가.
  - 쿼리 파라미터 `status` (콤마 구분, 선택), `Pageable pageable`.
- [x] (Verify) `EnrollmentQueryServiceTest.java` — `@IntegrationTest`.
  - 다른 Creator가 students 호출 → 403.
  - CONFIRMED 30건 + WAITLISTED 5건 + CANCELLED 3건 있을 때 size=10 → CONFIRMED 10건만, totalElements=30, totalPages=3.
  - my-enrollments에서 status=PENDING,CONFIRMED 필터 적용 시 CANCELLED 제외 확인.
- [x] (Verify) `web/clazz/StudentsControllerSliceTest.java` — `@WebMvcTest`로 권한 분기와 page 응답 구조 검증.
- [x] (Verify) N+1 쿼리 방지 검증 — `Hibernate.Statistics` 또는 `Datasource-proxy`로 30건 응답 시 SQL 호출 횟수가 2 이하임을 확인 (Enrollment 페이지 1 + User batch 1).
