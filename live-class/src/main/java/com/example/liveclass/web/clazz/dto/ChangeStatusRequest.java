// 강의 상태 전이 요청 DTO — target 으로 OPEN 또는 CLOSED 만 허용한다.
package com.example.liveclass.web.clazz.dto;

import com.example.liveclass.domain.clazz.ClassStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull ClassStatus target
) {
}
