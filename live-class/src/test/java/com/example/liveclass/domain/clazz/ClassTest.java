// Class Aggregate 상태 전이 및 불변식을 순수 JUnit5 로 검증한다.
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassTest {

    private Instant now;
    private UserId creator;
    private UserId otherUser;
    private Money price;
    private Capacity capacity;
    private ClassPeriod period;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        creator = UserId.of(UUID.randomUUID());
        otherUser = UserId.of(UUID.randomUUID());
        price = Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW"));
        capacity = Capacity.of(30);
        period = ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(7));
    }

    private Class createDraft() {
        return Class.draft(creator, "Test Class", "A description", price, capacity, period, now);
    }

    @Test
    void draft_createsClassWithDraftStatus() {
        Class clazz = createDraft();

        assertNotNull(clazz.getId());
        assertEquals(ClassStatus.DRAFT, clazz.getStatus());
        assertEquals("Test Class", clazz.getTitle());
        assertEquals(creator.value(), clazz.getCreatorId());
        assertEquals(now, clazz.getCreatedAt());
        assertEquals(now, clazz.getUpdatedAt());
    }

    @Test
    void open_fromDraft_statusBecomesOpen() {
        Class clazz = createDraft();
        clazz.open(creator, now);

        assertEquals(ClassStatus.OPEN, clazz.getStatus());
    }

    @Test
    void open_byOtherUser_throwsAccessDeniedDomainException() {
        Class clazz = createDraft();

        assertThrows(AccessDeniedDomainException.class, () -> clazz.open(otherUser, now));
    }

    @Test
    void open_calledTwice_secondCallThrowsIllegalStateTransitionException() {
        Class clazz = createDraft();
        clazz.open(creator, now);

        assertThrows(IllegalStateTransitionException.class, () -> clazz.open(creator, now));
    }

    @Test
    void close_fromDraft_throwsIllegalStateTransitionException() {
        Class clazz = createDraft();

        assertThrows(IllegalStateTransitionException.class, () -> clazz.close(creator, now));
    }

    @Test
    void changeCapacity_fromOpen_throwsIllegalStateTransitionException() {
        Class clazz = createDraft();
        clazz.open(creator, now);

        assertThrows(IllegalStateTransitionException.class,
                () -> clazz.changeCapacity(Capacity.of(50), creator, now));
    }

    @Test
    void isOpenForEnrollment_statusDependentResult() {
        Class draft = createDraft();
        assertFalse(draft.isOpenForEnrollment(now));

        draft.open(creator, now);
        assertTrue(draft.isOpenForEnrollment(now));

        draft.close(creator, now);
        assertFalse(draft.isOpenForEnrollment(now));
    }
}
