package com.devflow.devflow.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.devflow.devflow.config.GitHubAppProperties;

import io.jsonwebtoken.Jwts;

@Service
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubInstallationTokenProvider {
    private final GitHubAppProperties properties;
    private final GitHubHttpClient httpClient;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    @Autowired
    public GitHubInstallationTokenProvider(GitHubAppProperties properties, GitHubHttpClient httpClient) {
        this(properties, httpClient, Clock.systemUTC());
    }

    /** Clock injection keeps token expiry behavior deterministic in offline tests. */
    public GitHubInstallationTokenProvider(GitHubAppProperties properties, GitHubHttpClient httpClient, Clock clock) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    public String getToken() {
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(Instant.now(clock).plusSeconds(60))) return current.value();
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(Instant.now(clock).plusSeconds(60))) return current.value();
            long appId = positiveLong(properties.getAppId(), "GitHub App ID");
            long installationId = positiveLong(properties.getInstallationId(), "GitHub App installation ID");
            if (properties.getPrivateKey() == null || properties.getPrivateKey().isBlank()) {
                throw new IllegalStateException("GitHub App credentials are not configured");
            }
            try {
                Instant now = Instant.now(clock);
                String assertion = Jwts.builder().issuer(Long.toString(appId))
                    .issuedAt(java.util.Date.from(now.minusSeconds(60)))
                    .expiration(java.util.Date.from(now.plusSeconds(8 * 60)))
                    .signWith(readPrivateKey(properties.getPrivateKey()), Jwts.SIG.RS256).compact();
                Map<String, Object> response = httpClient.post(
                    "/app/installations/" + installationId + "/access_tokens", assertion, Map.of());
                Object token = response == null ? null : response.get("token");
                Object expiry = response == null ? null : response.get("expires_at");
                if (!(token instanceof String value) || value.isBlank() || !(expiry instanceof String expiryText)) {
                    throw new IllegalStateException("GitHub App token response was incomplete");
                }
                Instant expiresAt = Instant.parse(expiryText);
                if (!expiresAt.isAfter(Instant.now(clock).plusSeconds(60))) {
                    throw new IllegalStateException("GitHub App token response was already expired");
                }
                cachedToken = new CachedToken(value, expiresAt);
                return value;
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to obtain a GitHub App installation token");
            }
        }
    }

    private long positiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (RuntimeException ignored) { }
        throw new IllegalStateException(label + " is not configured correctly");
    }

    private PrivateKey readPrivateKey(String configuredKey) throws Exception {
        String normalized = configuredKey.replace("\\n", "\n").trim();
        String base64 = normalized.replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        if (normalized.contains("BEGIN RSA PRIVATE KEY")) keyBytes = wrapPkcs1(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private byte[] wrapPkcs1(byte[] pkcs1) throws IOException {
        byte[] version = { 0x02, 0x01, 0x00 };
        byte[] algorithm = { 0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00 };
        byte[] octetHeader = derHeader(0x04, pkcs1.length);
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.write(version); content.write(algorithm); content.write(octetHeader); content.write(pkcs1);
        byte[] encoded = content.toByteArray();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(derHeader(0x30, encoded.length)); result.write(encoded);
        return result.toByteArray();
    }

    private byte[] derHeader(int tag, int length) {
        if (length < 128) return new byte[] { (byte) tag, (byte) length };
        int bytes = 0; int remaining = length;
        while (remaining > 0) { bytes++; remaining >>>= 8; }
        byte[] header = new byte[2 + bytes]; header[0] = (byte) tag; header[1] = (byte) (0x80 | bytes);
        for (int index = bytes - 1; index >= 0; index--) { header[2 + index] = (byte) length; length >>>= 8; }
        return header;
    }

    private record CachedToken(String value, Instant expiresAt) {}
}
