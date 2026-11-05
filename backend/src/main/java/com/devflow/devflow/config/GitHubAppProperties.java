package com.devflow.devflow.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-side GitHub App settings. Private key material is never exposed as a bean response. */
@ConfigurationProperties(prefix = "devflow.github.app")
public class GitHubAppProperties {
    private String appId = "";
    private String installationId = "";
    private String privateKey = "";
    private String apiBaseUrl = "https://api.github.com";
    private List<String> allowedEnterpriseHosts = new ArrayList<>();

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public List<String> getAllowedEnterpriseHosts() { return allowedEnterpriseHosts; }
    public void setAllowedEnterpriseHosts(List<String> allowedEnterpriseHosts) {
        this.allowedEnterpriseHosts = allowedEnterpriseHosts == null ? new ArrayList<>() : new ArrayList<>(allowedEnterpriseHosts);
    }
}
