// 컨트롤러 메서드 파라미터에서 현재 사용자 UUID를 주입받는 커스텀 어노테이션
package com.example.liveclass.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
