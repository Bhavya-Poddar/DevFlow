package com.devflow.devflow.config;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Stops the schema-updating dev profile before datasource/JPA beans are created
 * unless the datasource is loopback and a local-only confirmation was supplied.
 */
@Component
@Profile("dev")
public final class DevProfileSchemaGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String CONFIRMATION_PROPERTY = "devflow.schema.dev-update-confirmation";
    static final String REQUIRED_CONFIRMATION = "LOCAL_DATABASE_ONLY";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        validate(environment);
    }

    /** Public for focused tests; this runs as a bean-factory post-processor in the dev profile. */
    public static void validate(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "The dev and prod profiles cannot be active together.");
        }

        String confirmation = environment.getProperty(CONFIRMATION_PROPERTY);
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalStateException(
                    "The dev profile can update schemas only when "
                    + "DEVFLOW_DEV_DB_CONFIRMATION=LOCAL_DATABASE_ONLY is explicitly set.");
        }

        String jdbcUrl;
        try {
            jdbcUrl = environment.getProperty("spring.datasource.url");
        } catch (IllegalArgumentException exception) {
            jdbcUrl = null;
        }
        if (!isLoopbackMySqlUrl(jdbcUrl)) {
            throw new IllegalStateException(
                    "The dev profile permits schema updates only for a loopback MySQL datasource. "
                    + "Use a local database, or activate the prod profile for production.");
        }
    }

    private static boolean isLoopbackMySqlUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return false;
        }

        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String host = uri.getHost();
            if (host == null) return false;
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
                normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
            }
            return LOOPBACK_HOSTS.contains(normalizedHost);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
