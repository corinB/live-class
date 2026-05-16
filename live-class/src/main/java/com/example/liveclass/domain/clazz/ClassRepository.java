// Class JPA 리포지토리 — 상태별 페이지 조회와 자동 마감 후보 조회를 제공한다.
package com.example.liveclass.domain.clazz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ClassRepository extends JpaRepository<Class, UUID> {

    Page<Class> findByStatusOrderByCreatedAtDesc(ClassStatus status, Pageable pageable);

    @Query("select c from Class c where c.status = :status and c.period.endDate < :cutoff")
    List<Class> findByStatusAndPeriodEndDateBefore(@Param("status") ClassStatus status,
                                                   @Param("cutoff") LocalDate cutoff);

    List<Class> findByStatus(ClassStatus status);
}
