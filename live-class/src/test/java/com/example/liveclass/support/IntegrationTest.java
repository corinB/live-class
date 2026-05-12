// @SpringBootTest + @ActiveProfiles("test") + @Testcontainers 를 하나로 묶는 통합 테스트 메타 어노테이션
package com.example.liveclass.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public @interface IntegrationTest {
}
