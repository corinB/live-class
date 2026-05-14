// 헬스/스모크용 ping 엔드포인트 컨트롤러
package com.example.liveclass.web.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    private record Pong(boolean pong) {}

    @GetMapping("/api/ping")
    public Pong ping() {
        return new Pong(true);
    }
}
