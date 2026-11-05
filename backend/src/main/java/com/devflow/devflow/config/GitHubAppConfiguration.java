package com.devflow.devflow.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GitHubAppProperties.class)
public class GitHubAppConfiguration {
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    @Bean
    RestClient githubRestClient(GitHubAppProperties properties) {
        String baseUrl = validateApiBaseUrl(properties);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(createHttpClient());
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(requestFactory)
            .baseUrl(baseUrl).build();
    }

    static HttpClient createHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static String validateApiBaseUrl(GitHubAppProperties properties) {
        URI base;
        try {
            String configured = properties.getApiBaseUrl();
            if (configured == null || configured.isBlank()) throw new IllegalArgumentException();
            base = URI.create(configured.trim());
        } catch (RuntimeException exception) {
            throw invalidBaseUrl();
        }

        String host = base.getHost();
        if (!"https".equalsIgnoreCase(base.getScheme()) || host == null || base.isOpaque()
                || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
                || (base.getPort() != -1 && base.getPort() != 443)) {
            throw invalidBaseUrl();
        }

        host = host.toLowerCase(Locale.ROOT);
        String path = base.getRawPath() == null ? "" : base.getRawPath();
        if ("api.github.com".equals(host)) {
            if (!path.isEmpty() && !"/".equals(path)) throw invalidBaseUrl();
            return "https://api.github.com";
        }

        Set<String> allowedHosts = normalizedAllowedHosts(properties.getAllowedEnterpriseHosts());
        if (!allowedHosts.contains(host) || !"/api/v3".equals(path)) throw invalidBaseUrl();
        return "https://" + host + "/api/v3";
    }

    private static Set<String> normalizedAllowedHosts(Iterable<String> configuredHosts) {
        java.util.Set<String> hosts = new java.util.HashSet<>();
        if (configuredHosts == null) return hosts;
        for (String configuredHost : configuredHosts) {
            if (configuredHost == null) throw invalidBaseUrl();
            String host = configuredHost.trim().toLowerCase(Locale.ROOT);
            if (!isValidDnsHost(host)) throw invalidBaseUrl();
            hosts.add(host);
        }
        return Set.copyOf(hosts);
    }

    private static boolean isValidDnsHost(String host) {
        if (host.isEmpty() || host.length() > 253 || !host.contains(".")) return false;
        for (String label : host.split("\\.", -1)) {
            if (!DNS_LABEL.matcher(label).matches()) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalidBaseUrl() {
        return new IllegalArgumentException(
            "GitHub API URL must be https://api.github.com or an allowlisted GitHub Enterprise https://host/api/v3 URL");
    }
}
