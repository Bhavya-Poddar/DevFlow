package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.security.GitHubWebhookSignatureVerifier;

class GitHubWebhookSignatureVerifierTests {
    private static final String SECRET = "test-webhook-secret";

    @Test
    void acceptsSignatureForExactRawBodyAndRejectsTamperingOrMissingSignature() throws Exception {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        new GitHubWebhookSignatureVerifier(SECRET).verify(body, signature(body));

        ResponseStatusException tampered = assertThrows(ResponseStatusException.class,
            () -> new GitHubWebhookSignatureVerifier(SECRET).verify("{ \"a\":1}".getBytes(StandardCharsets.UTF_8), signature(body)));
        assertEquals(HttpStatus.UNAUTHORIZED, tampered.getStatusCode());

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
            () -> new GitHubWebhookSignatureVerifier(SECRET).verify(body, null));
        assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());
    }

    @Test
    void missingSecretFailsClosed() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> new GitHubWebhookSignatureVerifier(" ").verify(new byte[0], "sha256=" + "0".repeat(64)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    private String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
