// Live Class API의 OpenAPI(Swagger) 메타데이터 및 보안 스킴을 정의하는 설정 클래스
package com.example.liveclass.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_NAME = "X-User-Id";
    private static final String SECURITY_SCHEME_NAME = "MockUser";

    @Value("${spring.application.name:live-class}")
    private String applicationName;

    @Value("${spring.boot.build-info.version:${project.version:0.0.1-SNAPSHOT}}")
    private String projectVersion;

    @Bean
    public OpenAPI liveClassOpenAPI() {
        SecurityScheme mockUserScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(API_KEY_NAME)
                .description("Mock 인증 헤더. Creator 또는 Classmate의 사용자 ID 값을 그대로 전달.");

        return new OpenAPI()
                .info(new Info()
                        .title("Live Class API")
                        .description("라이브 강의 수강신청 REST API")
                        .version(projectVersion))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, mockUserScheme))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
