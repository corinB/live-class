// 사용자 JPA 리포지토리 — 기본 CRUD 와 역할 기반 조회를 제공한다.
package com.example.liveclass.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndRole(UUID id, UserRole role);
}
