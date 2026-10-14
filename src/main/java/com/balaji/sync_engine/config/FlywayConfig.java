package com.balaji.sync_engine.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        flyway.migrate();
        return flyway;
    }

    /**
     * Guarantees that Flyway runs migrations BEFORE Hibernate initializes
     * and performs schema validation.
     */
    @Bean
    public static BeanFactoryPostProcessor flywayDependsOnPostProcessor() {
        return beanFactory -> {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                if ("entityManagerFactory".equals(beanName)) {
                    beanFactory.getBeanDefinition(beanName).setDependsOn("flyway");
                }
            }
        };
    }
}