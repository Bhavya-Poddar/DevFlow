package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devflow.devflow.config.GitHubAppProperties;
import com.devflow.devflow.service.GitHubHttpClient;
import com.devflow.devflow.service.GitHubInstallationTokenProvider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@ExtendWith(MockitoExtension.class)
class GitHubInstallationTokenProviderTests {
    @Mock private GitHubHttpClient httpClient;

    @Test
    void generatedAppJwtHasValidSignatureAndExpectedClaimsAndTokenIsReused() throws Exception {
        KeyPair keyPair = newRsaKeyPair();
        Instant beforeRequest = Instant.now();
        GitHubAppProperties properties = properties(keyPair);
        AtomicReference<String> assertionUsed = new AtomicReference<>();
        when(httpClient.post(eq("/app/installations/67890/access_tokens"),
                argThat(assertion -> assertion != null && assertion.split("\\.").length == 3), anyMap()))
            .thenAnswer(call -> {
                assertionUsed.set(call.getArgument(1));
                return tokenResponse("temporary-test-token", Instant.now().plusSeconds(3600));
            });

        GitHubInstallationTokenProvider provider = new GitHubInstallationTokenProvider(properties, httpClient);
        assertEquals("temporary-test-token", provider.getToken());
        assertEquals("temporary-test-token", provider.getToken());

        Claims claims = Jwts.parser().verifyWith(keyPair.getPublic()).build()
            .parseSignedClaims(assertionUsed.get()).getPayload();
        Instant afterRequest = Instant.now();
        assertEquals("12345", claims.getIssuer());
        assertBetween(claims.getIssuedAt().toInstant(), beforeRequest.minusSeconds(61), afterRequest.minusSeconds(59));
        assertBetween(claims.getExpiration().toInstant(), beforeRequest.plusSeconds(479), afterRequest.plusSeconds(481));
        verify(httpClient, times(1)).post(eq("/app/installations/67890/access_tokens"),
            argThat(assertion -> assertion != null && assertion.split("\\.").length == 3), anyMap());
    }

    @Test
    void cachedInstallationTokenIsRefreshedWhenWithinSixtySecondsOfExpiry() throws Exception {
        KeyPair keyPair = newRsaKeyPair();
        MutableClock clock = new MutableClock(Instant.parse("2026-10-23T10:00:00Z"));
        GitHubInstallationTokenProvider provider = new GitHubInstallationTokenProvider(properties(keyPair), httpClient, clock);
        when(httpClient.post(eq("/app/installations/67890/access_tokens"),
                argThat(assertion -> assertion != null), anyMap()))
            .thenReturn(tokenResponse("first-token", clock.instant().plusSeconds(300)))
            .thenReturn(tokenResponse("refreshed-token", clock.instant().plusSeconds(3600)));

        assertEquals("first-token", provider.getToken());
        assertEquals("first-token", provider.getToken());
        clock.advanceSeconds(241); // The cached token now has 59 seconds remaining.
        assertEquals("refreshed-token", provider.getToken());

        verify(httpClient, times(2)).post(eq("/app/installations/67890/access_tokens"),
            argThat(assertion -> assertion != null), anyMap());
    }

    @Test
    void malformedAndNearlyExpiredTokenResponsesAreRejectedWithoutCaching() throws Exception {
        KeyPair keyPair = newRsaKeyPair();
        MutableClock clock = new MutableClock(Instant.parse("2026-10-23T10:00:00Z"));
        GitHubInstallationTokenProvider provider = new GitHubInstallationTokenProvider(properties(keyPair), httpClient, clock);
        when(httpClient.post(eq("/app/installations/67890/access_tokens"),
                argThat(assertion -> assertion != null), anyMap()))
            .thenReturn(Map.of("token", "malformed-expiry-token", "expires_at", "not-an-instant"))
            .thenReturn(tokenResponse("expired-token", clock.instant().plusSeconds(60)));

        IllegalStateException malformed = assertThrows(IllegalStateException.class, provider::getToken);
        assertEquals("Unable to obtain a GitHub App installation token", malformed.getMessage());
        IllegalStateException nearlyExpired = assertThrows(IllegalStateException.class, provider::getToken);
        assertEquals("Unable to obtain a GitHub App installation token", nearlyExpired.getMessage());
        verify(httpClient, times(2)).post(eq("/app/installations/67890/access_tokens"),
            argThat(assertion -> assertion != null), anyMap());
    }

    @Test
    void missingAppCredentialsFailClosedWithoutCallingGitHub() {
        GitHubInstallationTokenProvider provider = new GitHubInstallationTokenProvider(
            new GitHubAppProperties(), httpClient);
        assertThrows(IllegalStateException.class, provider::getToken);
        verifyNoInteractions(httpClient);
    }

    private KeyPair newRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private GitHubAppProperties properties(KeyPair keyPair) {
        GitHubAppProperties properties = new GitHubAppProperties();
        properties.setAppId("12345");
        properties.setInstallationId("67890");
        properties.setPrivateKey("-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----");
        return properties;
    }

    private Map<String, Object> tokenResponse(String token, Instant expiresAt) {
        return Map.of("token", token, "expires_at", expiresAt.toString());
    }

    private void assertBetween(Instant actual, Instant earliest, Instant latest) {
        org.junit.jupiter.api.Assertions.assertFalse(actual.isBefore(earliest), actual.toString());
        org.junit.jupiter.api.Assertions.assertFalse(actual.isAfter(latest), actual.toString());
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
        private void advanceSeconds(long seconds) { instant.updateAndGet(value -> value.plusSeconds(seconds)); }
    }
}
