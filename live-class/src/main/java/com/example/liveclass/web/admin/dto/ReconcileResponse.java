// admin reconcile 엔드포인트 응답 DTO.
package com.example.liveclass.web.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record ReconcileResponse(UUID classId, Instant reconciledAt) {
}
