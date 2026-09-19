package com.utfpr.finder;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean(initMethod = "start", destroyMethod = "stop")
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18").withInitScript("db/test-roles.sql");
    }

    @Bean
    DynamicPropertyRegistrar propriedades(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "finder_test");
            registry.add("spring.datasource.password", () -> "test-only-password");
            registry.add("spring.flyway.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
        };
    }
}
