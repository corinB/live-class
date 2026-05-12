// UserApplicationService 의 register/getById 동작과 UserNotFoundException 을 @DataJpaTest 슬라이스로 검증한다.
package com.example.liveclass.application.user;

import com.example.liveclass.domain.user.UserNotFoundException;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserApplicationServiceTest {

    @Autowired
    private UserRepository userRepository;

    private UserApplicationService userApplicationService;

    @BeforeEach
    void setUp() {
        userApplicationService = new UserApplicationService(userRepository);
    }

    @Test
    void register_validInput_returnsUserWithNonNullId() {
        var user = userApplicationService.register(UserRole.CREATOR, "alice");

        assertNotNull(user.getId());
        assertEquals(UserRole.CREATOR, user.getRole());
        assertEquals("alice", user.getName());
    }

    @Test
    void getById_savedUser_returnsSameUser() {
        var saved = userApplicationService.register(UserRole.CREATOR, "alice");

        var found = userApplicationService.getById(saved.getId());

        assertEquals(saved.getId(), found.getId());
        assertEquals("alice", found.getName());
    }

    @Test
    void getById_unknownId_throwsUserNotFoundException() {
        UUID randomId = UUID.randomUUID();

        assertThrows(UserNotFoundException.class, () -> userApplicationService.getById(randomId));
    }
}
