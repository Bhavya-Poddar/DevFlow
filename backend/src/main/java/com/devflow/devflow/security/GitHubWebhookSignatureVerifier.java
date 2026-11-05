package com.devflow.devflow.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubWebhookSignatureVerifier {
    private final String secret;

    public GitHubWebhookSignatureVerifier(@Value("${devflow.github.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(byte[] rawBody, String signatureHeader) {
        if (secret == null || secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "GitHub webhook processing is not configured");
        }
        if (signatureHeader == null || !signatureHeader.matches("sha256=[0-9a-fA-F]{64}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid webhook signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody);
            byte[] supplied = HexFormat.of().parseHex(signatureHeader.substring("sha256=".length()));
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid webhook signature");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify GitHub webhook signature", exception);
        }
    }
}
