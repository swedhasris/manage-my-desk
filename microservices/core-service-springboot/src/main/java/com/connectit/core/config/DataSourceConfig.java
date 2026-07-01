package com.connectit.core.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Auto-detect driver from the JDBC URL so this works for both
        // MySQL (local dev) and PostgreSQL (Render cloud).
        String detectedDriver;
        if (dbUrl != null && dbUrl.startsWith("jdbc:postgresql")) {
            detectedDriver = "org.postgresql.Driver";
        } else {
            detectedDriver = "com.mysql.cj.jdbc.Driver";
        }

        log.info("[DataSourceConfig] Initializing HikariCP pool — url={}, driver={}", dbUrl, detectedDriver);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(detectedDriver);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(30000);
        config.setMaxLifetime(1200000);
        config.setLeakDetectionThreshold(60000);
        config.setConnectionTestQuery("SELECT 1");
        // Allow HikariCP to retry connection on startup instead of failing immediately
        config.setInitializationFailTimeout(-1);

        return new HikariDataSource(config);
    }
}

