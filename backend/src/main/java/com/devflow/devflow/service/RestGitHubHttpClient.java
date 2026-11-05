package com.devflow.devflow.service;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class RestGitHubHttpClient implements GitHubHttpClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};
    private final RestClient restClient;

    public RestGitHubHttpClient(RestClient githubRestClient) { this.restClient = githubRestClient; }

    @Override
    public Map<String, Object> get(String path, String bearerToken) {
        try {
            return restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header("Accept", "application/vnd.github+json").retrieve().body(MAP_TYPE);
        } catch (RestClientResponseException exception) {
            boolean rateLimited = exception.getStatusCode().value() == 429
                || (exception.getStatusCode().value() == 403
                    && ("0".equals(exception.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
                        || exception.getResponseHeaders().containsHeader("Retry-After")));
            throw new GitHubApiException(exception.getStatusCode().value(), rateLimited);
        } catch (RestClientException exception) {
            throw exception;
        }
    }

    @Override
    public Map<String, Object> post(String path, String bearerToken, Map<String, Object> body) {
        try {
            return restClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header("Accept", "application/vnd.github+json").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(MAP_TYPE);
        } catch (RestClientResponseException exception) {
            boolean rateLimited = exception.getStatusCode().value() == 429
                || (exception.getStatusCode().value() == 403
                    && ("0".equals(exception.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
                        || exception.getResponseHeaders().containsHeader("Retry-After")));
            throw new GitHubApiException(exception.getStatusCode().value(), rateLimited);
        } catch (RestClientException exception) {
            throw exception;
        }
    }
}
