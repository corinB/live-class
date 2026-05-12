// 사용자 등록·조회 REST 컨트롤러.
package com.example.liveclass.web.user;

import com.example.liveclass.application.user.UserApplicationService;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.web.auth.CurrentUserId;
import com.example.liveclass.web.user.dto.RegisterUserRequest;
import com.example.liveclass.web.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userApplicationService;

    public UserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest req) {
        User user = userApplicationService.register(req.role(), req.name());
        return ResponseEntity.status(201).body(UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse getMe(@CurrentUserId UUID userId) {
        User user = userApplicationService.getById(userId);
        return UserResponse.from(user);
    }
}
