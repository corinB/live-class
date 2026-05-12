// 강의 Aggregate Root — 상태(DRAFT/OPEN/CLOSED) 전이와 정원/가격/기간 invariant 캡슐화.
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.user.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classes")
@Getter
public class Class {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Embedded
    private Money price;

    @Embedded
    private Capacity capacity;

    @Embedded
    private ClassPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClassStatus status;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Class() {
    }

    public static Class draft(UserId creator, String title, String description,
                              Money price, Capacity capacity, ClassPeriod period, Instant now) {
        if (creator == null) {
            throw new IllegalArgumentException("Creator must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Class title must not be blank");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("Class title must not exceed 200 characters");
        }
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("Class description must not exceed 2000 characters");
        }
        if (price == null) {
            throw new IllegalArgumentException("Class price must not be null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("Class capacity must not be null");
        }
        if (period == null) {
            throw new IllegalArgumentException("Class period must not be null");
        }

        Class clazz = new Class();
        clazz.id = UUID.randomUUID();
        clazz.title = title;
        clazz.description = description;
        clazz.price = price;
        clazz.capacity = capacity;
        clazz.period = period;
        clazz.status = ClassStatus.DRAFT;
        clazz.creatorId = creator.value();
        clazz.createdAt = now;
        clazz.updatedAt = now;
        // version stays null until Hibernate assigns 0L on @PrePersist.
        // Spring Data JPA's save() uses null @Version as the isNew() signal — setting it
        // explicitly to 0L makes save() take the merge() path on first call, which
        // issues UPDATE (0 rows) and throws StaleObjectStateException.
        return clazz;
    }

    public void open(UserId requester, Instant now) {
        checkCreator(requester);
        if (this.status != ClassStatus.DRAFT) {
            throw new IllegalStateTransitionException(
                    "Class can only open from DRAFT, but was " + this.status);
        }
        this.status = ClassStatus.OPEN;
        this.updatedAt = now;
    }

    public void close(UserId requester, Instant now) {
        checkCreator(requester);
        if (this.status != ClassStatus.OPEN) {
            throw new IllegalStateTransitionException(
                    "Class can only close from OPEN, but was " + this.status);
        }
        this.status = ClassStatus.CLOSED;
        this.updatedAt = now;
    }

    public void changeCapacity(Capacity newCapacity, UserId requester, Instant now) {
        checkCreator(requester);
        if (this.status != ClassStatus.DRAFT) {
            throw new IllegalStateTransitionException(
                    "Capacity can only be changed in DRAFT status, but was " + this.status);
        }
        this.capacity = newCapacity;
        this.updatedAt = now;
    }

    public boolean isOpenForEnrollment(Instant now) {
        return this.status == ClassStatus.OPEN;
    }

    private void checkCreator(UserId requester) {
        if (requester == null || !requester.value().equals(this.creatorId)) {
            throw new AccessDeniedDomainException(
                    "Only the creator can perform this operation");
        }
    }
}
