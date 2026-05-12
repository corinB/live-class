// X-User-Id 헤더에서 UUID를 파싱해 currentUserId attribute 로 주입하는 OncePerRequestFilter
package com.example.liveclass.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class MockUserFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-User-Id";
    private static final String ATTRIBUTE_NAME = "currentUserId";
    private static final List<String> WHITELIST_PATTERNS = List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**"
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // Whitelist check — pass through swagger/actuator paths without authentication
        for (String pattern : WHITELIST_PATTERNS) {
            if (pathMatcher.match(pattern, uri)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String headerValue = request.getHeader(HEADER_NAME);

        if (headerValue == null || headerValue.isBlank()) {
            writeProblemDetail(response, HttpStatus.UNAUTHORIZED, "Missing X-User-Id header");
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(headerValue);
        } catch (IllegalArgumentException e) {
            writeProblemDetail(response, HttpStatus.BAD_REQUEST, "Invalid UUID format in X-User-Id header");
            return;
        }

        request.setAttribute(ATTRIBUTE_NAME, userId);
        filterChain.doFilter(request, response);
    }

    private void writeProblemDetail(HttpServletResponse response,
                                    HttpStatus status,
                                    String detail) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(problemDetail));
    }
}
