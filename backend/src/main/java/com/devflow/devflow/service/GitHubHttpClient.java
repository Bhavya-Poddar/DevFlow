package com.devflow.devflow.service;

import java.util.Map;

/** Small seam around HTTP so GitHub interactions can be tested without network access. */
public interface GitHubHttpClient {
    Map<String, Object> get(String path, String bearerToken);
    Map<String, Object> post(String path, String bearerToken, Map<String, Object> body);
}
