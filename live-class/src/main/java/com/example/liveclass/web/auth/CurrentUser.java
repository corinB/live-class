// 현재 인증된 사용자의 UUID를 담는 불변 레코드
package com.example.liveclass.web.auth;

import java.util.UUID;

public record CurrentUser(UUID userId) {
}
