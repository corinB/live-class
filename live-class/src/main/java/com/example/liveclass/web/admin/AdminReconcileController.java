// 운영자가 단일 강의의 Redis ZSET mirror 를 강제 재구성하는 admin REST 컨트롤러.
package com.example.liveclass.web.admin;

import com.example.liveclass.infrastructure.ReconcileService;
import com.example.liveclass.web.admin.dto.ReconcileResponse;
import com.example.liveclass.web.auth.CurrentUserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminReconcileController {

    private final ReconcileService reconcileService;

    public AdminReconcileController(ReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    @PostMapping("/reconcile/{classId}")
    public ReconcileResponse reconcile(@CurrentUserId UUID caller,
                                       @PathVariable UUID classId) {
        Instant now = Instant.now();
        log.info("manual reconcile triggered classId={}, caller={}", classId, caller);
        boolean ran = reconcileService.reconcileOne(classId);
        if (!ran) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "reconcile in progress for classId=" + classId);
        }
        return new ReconcileResponse(classId, now);
    }
}
