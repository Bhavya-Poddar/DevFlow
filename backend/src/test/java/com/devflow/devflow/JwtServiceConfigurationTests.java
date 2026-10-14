package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.devflow.devflow.security.JwtService;

class JwtServiceConfigurationTests {
    @Test
    void rejectsMissingOrWeakSigningSecretsImmediately() {
        assertThrows(IllegalStateException.class, () -> new JwtService(null));
        assertThrows(IllegalStateException.class, () -> new JwtService("short"));
    }

    @Test
    void acceptsASecretOfAtLeastThirtyTwoUtf8Bytes() {
        assertDoesNotThrow(() -> new JwtService("sufficient-test-signing-key-material-123"));
    }
}
