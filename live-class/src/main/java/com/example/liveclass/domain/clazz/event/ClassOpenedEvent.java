// 강의가 DRAFT → OPEN 으로 전이된 직후 발행되는 도메인 이벤트.
package com.example.liveclass.domain.clazz.event;

import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.user.UserId;

import java.time.Instant;

public record ClassOpenedEvent(ClassId classId, UserId creatorId, Instant occurredAt) {
}
