package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.devflow.devflow.config.DevProfileSchemaGuard;

class DevProfileSchemaGuardTests {

    @Test
    void rejectsDevProfileWithoutExplicitLocalSchemaUpdateConfirmation() {
        StandardEnvironment environment = localMysqlEnvironment();

        assertThrows(IllegalStateException.class, () -> DevProfileSchemaGuard.validate(environment));
    }

    @Test
    void rejectsRemoteDatasourceEvenWhenConfirmationIsSet() {
        StandardEnvironment environment = environment(
                "jdbc:mysql://db.production.example:3306/devflow", "LOCAL_DATABASE_ONLY");

        assertThrows(IllegalStateException.class, () -> DevProfileSchemaGuard.validate(environment));
    }

    @Test
    void rejectsDevAndProdProfilesActivatedTogether() {
        StandardEnvironment environment = environment(
                "jdbc:mysql://localhost:3306/devflow", "LOCAL_DATABASE_ONLY");
        environment.setActiveProfiles("dev", "prod");

        assertThrows(IllegalStateException.class, () -> DevProfileSchemaGuard.validate(environment));
    }

    @Test
    void allowsConfirmedLoopbackDevelopmentDatasource() {
        StandardEnvironment environment = environment(
                "jdbc:mysql://localhost:3306/devflow", "LOCAL_DATABASE_ONLY");

        assertDoesNotThrow(() -> DevProfileSchemaGuard.validate(environment));
    }

    @Test
    void rejectsNonMysqlAndUnparseableDatasourceUrls() {
        StandardEnvironment h2 = environment("jdbc:h2:mem:devflow", "LOCAL_DATABASE_ONLY");
        StandardEnvironment malformed = environment("jdbc:mysql:loadbalance://localhost/db", "LOCAL_DATABASE_ONLY");

        assertThrows(IllegalStateException.class, () -> DevProfileSchemaGuard.validate(h2));
        assertThrows(IllegalStateException.class, () -> DevProfileSchemaGuard.validate(malformed));
    }

    private StandardEnvironment localMysqlEnvironment() {
        return environment("jdbc:mysql://localhost:3306/devflow", null);
    }

    private StandardEnvironment environment(String jdbcUrl, String confirmation) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbcUrl);
        if (confirmation != null) {
            properties.put("devflow.schema.dev-update-confirmation", confirmation);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

}
