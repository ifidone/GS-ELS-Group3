package com.els.backend.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    Flyway flyway(DataSource dataSource,
                  @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
                  @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
                  @Value("${spring.flyway.validate-on-migrate:true}") boolean validateOnMigrate) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .validateOnMigrate(validateOnMigrate)
                .load();
    }
}
