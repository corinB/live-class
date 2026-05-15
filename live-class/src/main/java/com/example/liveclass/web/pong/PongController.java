// 자동화 파이프라인 스모크용 pong 엔드포인트 컨트롤러
package com.example.liveclass.web.pong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PongController {

    private record Ping(boolean ping) {}

    @GetMapping("/api/pong")
    public Ping pong() {
        return new Ping(true);
    }
}
