// MockUserFilter 등록 및 CurrentUserArgumentResolver를 WebMvc에 연결하는 설정 클래스
package com.example.liveclass.config;

import com.example.liveclass.web.auth.CurrentUserArgumentResolver;
import com.example.liveclass.web.auth.MockUserFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public WebMvcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public FilterRegistrationBean<MockUserFilter> mockUserFilterRegistration() {
        FilterRegistrationBean<MockUserFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MockUserFilter(objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
