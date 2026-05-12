// 수강 신청 Aggregate Root — PENDING/CONFIRMED/CANCELLED/WAITLISTED 라이프사이클과 7일 취소창 캡슐화.
package com.example.liveclass.domain.enrollment;

import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.user.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(name = "classmate_id", nullable = false, updatable = false)
    private UUID classmateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Enrollment() {
    }

    private Enrollment(UUID classId, UUID classmateId, EnrollmentStatus status, Instant appliedAt) {
        this.id = UUID.randomUUID();
        this.classId = classId;
        this.classmateId = classmateId;
        this.status = status;
        this.appliedAt = appliedAt;
    }

    public static Enrollment apply(ClassId classId, UserId userId, Instant appliedAt) {
        return new Enrollment(classId.value(), userId.value(), EnrollmentStatus.PENDING, appliedAt);
    }

    public static Enrollment waitlist(ClassId classId, UserId userId, Instant appliedAt) {
        return new Enrollment(classId.value(), userId.value(), EnrollmentStatus.WAITLISTED, appliedAt);
    }

    public void confirm(Instant now) {
        if (this.status != EnrollmentStatus.PENDING) {
            throw new IllegalStateTransitionException(
                    "confirm() requires PENDING status, but current status is " + this.status);
        }
        this.paidAt = now;
        this.status = EnrollmentStatus.CONFIRMED;
    }

    public void cancel(Instant now) {
        if (this.status == EnrollmentStatus.CANCELLED) {
            throw new AlreadyCancelledException();
        }
        if (this.status == EnrollmentStatus.CONFIRMED
                && !CancellationWindow.SEVEN_DAYS.isWithin(this.paidAt, now)) {
            throw new OutsideCancellationWindowException();
        }
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void promoteFromWaitlist(Instant now) {
        if (this.status != EnrollmentStatus.WAITLISTED) {
            throw new IllegalStateTransitionException(
                    "promoteFromWaitlist() requires WAITLISTED status, but current status is " + this.status);
        }
        this.status = EnrollmentStatus.PENDING;
        // appliedAt is intentionally preserved per DOCS
    }

    public boolean isWithinCancellationWindow(Instant now) {
        if (this.paidAt == null) {
            return false;
        }
        return CancellationWindow.SEVEN_DAYS.isWithin(this.paidAt, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassId() {
        return classId;
    }

    public UUID getClassmateId() {
        return classmateId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Long getVersion() {
        return version;
    }
}
