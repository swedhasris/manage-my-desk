package com.connectit.core.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs BEFORE any Spring beans (including Hibernate) are created.
 * <p>
 * Responsibilities:
 * 1. Convert Render's "postgres://..." DATABASE_URL to a valid JDBC URL.
 * 2. Set the correct Hibernate dialect so Hibernate never needs to connect
 *    to the database at startup just to detect the dialect.
 * 3. Set spring.datasource.url to the resolved JDBC URL.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "databaseEnvironmentPostProcessor";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new LinkedHashMap<>();

        // ── 1. Resolve DATABASE_URL ──────────────────────────────────────────
        String rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl != null && !rawUrl.isBlank()) {
            String jdbcUrl = DataSourceConfig.resolveJdbcUrl(rawUrl);
            props.put("spring.datasource.url", jdbcUrl);
        }

        // ── 2. Detect Hibernate dialect ──────────────────────────────────────
        // Re-read after potential override above
        String datasourceUrl = props.containsKey("spring.datasource.url")
                ? (String) props.get("spring.datasource.url")
                : environment.getProperty("spring.datasource.url", "");

        String dialect = detectDialect(datasourceUrl);
        // Only set if not already overridden by user via HIBERNATE_DIALECT env var
        String existingDialect = environment.getProperty("spring.jpa.database-platform");
        if (existingDialect == null || existingDialect.isBlank()) {
            props.put("spring.jpa.database-platform", dialect);
        }

        // ── 3. Prevent Hibernate from querying the DB for metadata at startup ─
        props.put("spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults", "false");

        if (!props.isEmpty()) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource(SOURCE_NAME, props));
        }
    }

    private String detectDialect(String url) {
        if (url != null && url.startsWith("jdbc:postgresql")) {
            return "org.hibernate.dialect.PostgreSQLDialect";
        }
        return "org.hibernate.dialect.MySQLDialect";
    }
}
