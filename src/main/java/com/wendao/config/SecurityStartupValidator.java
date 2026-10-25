package com.wendao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail fast when a production deployment still uses placeholder secrets.
 * Local/demo profiles are intentionally left untouched.
 */
@Component
public class SecurityStartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);

    private final Environment environment;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    public SecurityStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (activeProfile == null || !activeProfile.toLowerCase().contains("prod")) {
            return;
        }

        String admin = environment.getProperty("paypro.token.value", "");
        String openapi = environment.getProperty("paypro.openapi.secret", "");
        String dbPassword = environment.getProperty("spring.datasource.password", "");
        String site = environment.getProperty("paypro.site", "");

        requireStrongSecret("ADMIN_TOKEN", admin);
        requireStrongSecret("OPENAPI_SECRET", openapi);

        if (admin.equals(openapi)) {
            throw new IllegalStateException("ADMIN_TOKEN and OPENAPI_SECRET must be different");
        }

        if (dbPassword.trim().isEmpty()
                || dbPassword.toLowerCase().contains("change_me")
                || dbPassword.toLowerCase().contains("change-me")) {
            throw new IllegalStateException("DB_PASSWORD must be set to a non-placeholder value");
        }

        if (!site.startsWith("https://") && !site.contains("127.0.0.1") && !site.contains("localhost")) {
            log.warn("SITE_URL is not HTTPS: {}. Use HTTPS in production.", site);
        }
    }

    private void requireStrongSecret(String name, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 32
                || normalized.toLowerCase().contains("change-me")
                || normalized.toLowerCase().contains("change_me")
                || "default_secret_key".equals(normalized)) {
            throw new IllegalStateException(name + " must be a random value with at least 32 characters");
        }
    }
}
