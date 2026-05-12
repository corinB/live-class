// 요청된 사용자 ID 가 DB 에 존재하지 않을 때 던지는 도메인 예외 (404).
package com.example.liveclass.domain.user;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException(UUID id) {
        super("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "User not found: " + id);
    }
}
