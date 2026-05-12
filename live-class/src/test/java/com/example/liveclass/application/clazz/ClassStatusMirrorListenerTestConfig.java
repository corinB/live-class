// ClassStatusMirrorListenerTest 전용 경량 Spring Boot 컨텍스트 — Redis + 임베디드 H2 DataSource + DataSourceTransactionManager.
package com.example.liveclass.application.clazz;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@SpringBootConfiguration
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
public class ClassStatusMirrorListenerTestConfig {

    @Bean
    public ClassStatusMirrorListener classStatusMirrorListener(
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        return new ClassStatusMirrorListener(redisTemplate);
    }

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("mirror-listener-test;DB_CLOSE_DELAY=-1")
                .build();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
