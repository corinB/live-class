// User Aggregate 의 불변식과 도메인 의도 메서드를 순수 JUnit 으로 검증한다.
package com.example.liveclass.domain.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private final Instant now = Instant.now();

    @Test
    void register_validCreator_returnsUserWithCorrectState() {
        User user = User.register(UserRole.CREATOR, "alice", now);

        assertNotNull(user.getId());
        assertEquals(UserRole.CREATOR, user.getRole());
        assertEquals("alice", user.getName());
        assertEquals(now, user.getCreatedAt());
        assertTrue(user.isCreator());
        assertFalse(user.isClassmate());
    }

    @Test
    void register_validClassmate_isClassmateTrue() {
        User user = User.register(UserRole.CLASSMATE, "bob", now);

        assertTrue(user.isClassmate());
        assertFalse(user.isCreator());
    }

    @Test
    void register_nullRole_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> User.register(null, "alice", now));
    }

    @Test
    void register_emptyName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> User.register(UserRole.CREATOR, "", now));
    }

    @Test
    void register_blankName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> User.register(UserRole.CREATOR, "  ", now));
    }

    @Test
    void register_nameTooLong_throwsIllegalArgumentException() {
        String longName = "a".repeat(51);
        assertThrows(IllegalArgumentException.class, () -> User.register(UserRole.CREATOR, longName, now));
    }

    @Test
    void register_nameExactly50Chars_succeeds() {
        String name = "a".repeat(50);
        User user = User.register(UserRole.CREATOR, name, now);
        assertEquals(name, user.getName());
    }
}
