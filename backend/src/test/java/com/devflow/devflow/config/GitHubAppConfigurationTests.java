package com.devflow.devflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import java.util.List;

import org.junit.jupiter.api.Test;

class GitHubAppConfigurationTests {
    private final GitHubAppConfiguration configuration = new GitHubAppConfiguration();

    @Test
    void officialGitHubApiHostIsAcceptedByDefault() {
        GitHubAppProperties properties = new GitHubAppProperties();
        assertNotNull(configuration.githubRestClient(properties));
    }

    @Test
    void explicitlyAllowlistedEnterpriseApiHostIsAccepted() {
        GitHubAppProperties properties = new GitHubAppProperties();
        properties.setApiBaseUrl("https://github.example.test/api/v3");
        properties.setAllowedEnterpriseHosts(List.of("github.example.test"));

        assertNotNull(configuration.githubRestClient(properties));
    }

    @Test
    void arbitraryHttpsHostIsRejected() {
        assertRejected("https://attacker.example.test", List.of());
    }

    @Test
    void httpUrlIsRejected() {
        assertRejected("http://api.github.com", List.of());
    }

    @Test
    void credentialsEmbeddedInUrlAreRejected() {
        assertRejected("https://user:password@api.github.com", List.of());
    }

    @Test
    void enterpriseHostMustBeExplicitlyAllowlisted() {
        assertRejected("https://github.example.test/api/v3", List.of());
    }

    @Test
    void unsupportedPathQueryFragmentAndPortAreRejected() {
        assertRejected("https://api.github.com/custom", List.of());
        assertRejected("https://api.github.com?redirect=elsewhere", List.of());
        assertRejected("https://api.github.com/#fragment", List.of());
        assertRejected("https://github.example.test:8443/api/v3", List.of("github.example.test"));
        assertRejected("https://github.example.test/api/v4", List.of("github.example.test"));
    }

    @Test
    void authenticatedHttpClientDoesNotFollowRedirects() {
        assertEquals(HttpClient.Redirect.NEVER, GitHubAppConfiguration.createHttpClient().followRedirects());
    }

    private void assertRejected(String url, List<String> allowedHosts) {
        GitHubAppProperties properties = new GitHubAppProperties();
        properties.setApiBaseUrl(url);
        properties.setAllowedEnterpriseHosts(allowedHosts);
        assertThrows(IllegalArgumentException.class, () -> configuration.githubRestClient(properties));
    }
}
