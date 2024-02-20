package com.roundrobin_assignment.dpp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Config {

    public static final Charset UTF8 = StandardCharsets.UTF_8;
    public static final String JSON_CONTENT_TYPE = "application/json";

    private String apiUrl = "";
    private String user = "";
    private String password = "";
    private String authHeader = "";
    private String proxyUrl = "";
    private String proxyUser = "";
    private String proxyPassword = "";
    private String proxyAuthHeader = "";
    private String logLevel = "";

    private List<Rule> rules;

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public List<Rule> getRules() {
        return this.rules;
    }

    public boolean hasRules() {
        return this.rules != null && this.rules.size() > 0;
    }

    public String getProxyAuthHeader() {
        return proxyAuthHeader;
    }

    public void setProxyAuthHeader(String proxyAuthHeader) {
        this.proxyAuthHeader = proxyAuthHeader;
    }

    public String getAuthHeader() {
        return authHeader;
    }

    public void setAuthHeader(String authHeader) {
        this.authHeader = authHeader;
    }
}
