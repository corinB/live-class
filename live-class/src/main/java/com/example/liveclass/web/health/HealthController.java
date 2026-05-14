// 헬스 체크 엔드포인트를 제공하는 REST 컨트롤러.
package com.example.liveclass.web.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private record Health(String status) {}

    @GetMapping("/health")
    public Health health() {
        return new Health("ok");
    }
}
