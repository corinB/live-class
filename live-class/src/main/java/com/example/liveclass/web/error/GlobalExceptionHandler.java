// 도메인 예외·검증 오류·낙관적 잠금 실패를 RFC 7807 ProblemDetail 로 매핑하는 전역 예외 핸들러
package com.example.liveclass.web.error;

import com.example.liveclass.application.enrollment.MirrorUnavailableException;
import com.example.liveclass.domain.shared.DomainException;
import com.example.liveclass.infrastructure.ClassLockBusyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        detail.setProperty("errorCode", ex.getErrorCode());
        return ResponseEntity.status(ex.getStatus()).body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setProperty("errorCode", "VALIDATION_FAILED");
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Resource was modified by another request. Please retry.");
        detail.setProperty("errorCode", "OPTIMISTIC_LOCK_FAILURE");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(MirrorUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleMirrorUnavailable(MirrorUnavailableException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Enrollment service temporarily unavailable. Please retry.");
        detail.setProperty("errorCode", "MIRROR_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
    }

    /**
     * Redis 연결 실패 / 명령 타임아웃을 503 fail-closed 로 매핑한다.
     *
     * <p>EnrollmentMirrorService 는 자체 catch 로 MirrorUnavailableException 으로 변환하지만,
     * ClassLockService.executeWithLock 의 setIfAbsent 같은 apply 흐름 첫 Redis 호출은 변환 없이
     * Spring Data Redis 의 DataAccessException 하위 예외를 그대로 던진다. catch-all handleGeneric
     * 으로 떨어지면 500 이 되어 정책(Redis 다운 = 503) 과 어긋나므로 별도 핸들러를 둔다.
     *
     * <p>DataAccessException 전체를 503 으로 매핑하지 않는 이유 — DB 장애(Postgres 다운, JPA
     * exception 등) 까지 같은 503 으로 흡수되면 운영 진단이 모호해진다. Redis 한정 예외(상위
     * RedisConnectionFailureException, 공통 QueryTimeoutException) 두 가지만 좁게 매핑한다.
     */
    @ExceptionHandler({QueryTimeoutException.class, RedisConnectionFailureException.class})
    public ResponseEntity<ProblemDetail> handleRedisUnavailable(Exception ex) {
        log.error("Redis unavailable — mapping to 503", ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Enrollment service temporarily unavailable. Please retry.");
        detail.setProperty("errorCode", "MIRROR_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        // JSON parse 실패 — invalid syntax / UTF-8 byte 깨짐 / 빈 body 등. 500 이 아니라 400 으로 명확히 안내.
        log.warn("Invalid request body rejected: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid request body — JSON parse 실패. Content-Type charset (UTF-8) 과 JSON 문법 확인 필요.");
        detail.setProperty("errorCode", "MALFORMED_JSON");
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(ClassLockBusyException.class)
    public ResponseEntity<ProblemDetail> handleClassLockBusy(ClassLockBusyException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Another request is updating this class right now. Please retry shortly.");
        detail.setProperty("errorCode", "CLASS_LOCK_BUSY");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        // 분류되지 않은 예외는 운영 진단을 위해 stack trace 와 함께 ERROR 로깅한다.
        // 기존엔 swallow 되어 한글 RequestBody 500 같은 케이스에서 원인 추적이 불가능했다.
        log.error("Unhandled exception reached GlobalExceptionHandler", ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        detail.setProperty("errorCode", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
    }
}
