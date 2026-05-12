// 사용자 Aggregate Root — 역할(role)과 이름을 캡슐화하며 register 정적 팩토리로만 생성된다.
package com.example.liveclass.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 20)
    private UserRole role;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public static User register(UserRole role, String name, Instant now) {
        if (role == null) {
            throw new IllegalArgumentException("UserRole must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("User name must not be blank");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("User name must not exceed 50 characters");
        }
        User user = new User();
        user.id = UUID.randomUUID();
        user.role = role;
        user.name = name;
        user.createdAt = now;
        return user;
    }

    public boolean isCreator() {
        return role == UserRole.CREATOR;
    }

    public boolean isClassmate() {
        return role == UserRole.CLASSMATE;
    }
}
