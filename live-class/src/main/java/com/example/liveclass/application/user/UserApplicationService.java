// 사용자 등록과 조회를 담당하는 application service.
package com.example.liveclass.application.user;

import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserNotFoundException;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.domain.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class UserApplicationService {

    private final UserRepository userRepository;

    public UserApplicationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(UserRole role, String name) {
        User user = User.register(role, name, Instant.now());
        return userRepository.save(user);
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
