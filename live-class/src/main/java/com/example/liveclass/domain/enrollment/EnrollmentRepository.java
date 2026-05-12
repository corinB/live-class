// 수강 신청 Aggregate 의 JPA Repository — race-non-critical 쿼리 + 페이징 + reconcile 지원
package com.example.liveclass.domain.enrollment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /**
     * Active duplicate check — status IN (PENDING, CONFIRMED, WAITLISTED).
     * ARCHITECTURE §4: FOR UPDATE is NOT used; Lua ZSET mirror is the race-critical gate.
     */
    @Query("select e from Enrollment e " +
           "where e.classId = :classId " +
           "and e.classmateId = :classmateId " +
           "and e.status in " +
           "(com.example.liveclass.domain.enrollment.EnrollmentStatus.PENDING, " +
           "com.example.liveclass.domain.enrollment.EnrollmentStatus.CONFIRMED, " +
           "com.example.liveclass.domain.enrollment.EnrollmentStatus.WAITLISTED)")
    Optional<Enrollment> findActiveByClassAndClassmate(
            @Param("classId") UUID classId,
            @Param("classmateId") UUID classmateId);

    /**
     * Count PENDING + CONFIRMED seats — used for validation and statistics.
     */
    @Query("select count(e) from Enrollment e " +
           "where e.classId = :classId " +
           "and e.status in " +
           "(com.example.liveclass.domain.enrollment.EnrollmentStatus.PENDING, " +
           "com.example.liveclass.domain.enrollment.EnrollmentStatus.CONFIRMED)")
    long countActiveSeatsByClassId(@Param("classId") UUID classId);

    /**
     * Count by classId and a specific status — used for reconcile verification.
     */
    @Query("select count(e) from Enrollment e " +
           "where e.classId = :classId " +
           "and e.status = :status")
    long countByClassIdAndStatus(
            @Param("classId") UUID classId,
            @Param("status") EnrollmentStatus status);

    /**
     * My-enrollments page — filtered by classmateId and multiple statuses.
     */
    Page<Enrollment> findByClassmateIdAndStatusIn(
            UUID classmateId,
            Collection<EnrollmentStatus> statuses,
            Pageable pageable);

    /**
     * Creator's student list — filtered by classId and a single status.
     */
    Page<Enrollment> findByClassIdAndStatus(
            UUID classId,
            EnrollmentStatus status,
            Pageable pageable);

    /**
     * Reconcile query — returns enrollments ordered by appliedAt ascending (FIFO).
     */
    List<Enrollment> findByClassIdAndStatusInOrderByAppliedAtAsc(
            UUID classId,
            Collection<EnrollmentStatus> statuses);
}
