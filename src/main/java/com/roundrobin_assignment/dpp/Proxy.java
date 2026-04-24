package com.roundrobin_assignment.dpp;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet
public class Proxy extends HttpServlet {

    private static final Logger log = Logger.getLogger(Proxy.class.getName());

    @Autowired
    private OAuthService authService;

    @Autowired
    private Config config;

    @Value("${spring.profiles.active}")
    private String springProfile;

    @Value("${dpp.version}")
    private String dppVersion;

    @Value("${zendesk.api.url}")
    private String zendeskApiUrl;

    @Value("${proxy.public.url}")
    private String proxyUrl;

    private boolean isStandaloneMode;

    @PostConstruct
    public void init() {
        log.info(String.format("Dpp v%s started", dppVersion));
        isStandaloneMode = "standalone".equalsIgnoreCase(springProfile);
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        uri = java.net.URLDecoder.decode(uri, HttpUtils.UTF8);
        if (uri.equals("/_ah/start")) {
            return; //Google AppEngine start instance
        }
        response.setCharacterEncoding(HttpUtils.UTF8.name());
        response.setContentType(HttpUtils.JSON_CONTENT_TYPE);

        if (!validateRequest(request, response, uri)) {
            return;
        }
        if (uri.startsWith(OAuthService.OAUTH_URI)) {
            authService.handleOAuthRequest(request, response, uri);
            return;
        }

        Rule rule = findRule(uri, request.getMethod(), response);
        String targetRequest = extractTargetRequest(request, rule, uri, response);
        if (targetRequest == null) {
            return;
        }

        String baseUrl = request.getRequestURL().substring(0, request.getRequestURL().length() - request.getRequestURI().length()) + request.getContextPath();
        String targetUrl = zendeskApiUrl + uri;
        log.config("Target url: " + targetUrl);
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = HttpUtils.createHttpURLConnection(request, targetUrl, authService.getZendeskAuth(request), targetRequest);
            int status = urlConnection.getResponseCode();
            response.setStatus(status);
            log.config("Response code: " + status);
            if (HttpStatus.valueOf(status).is2xxSuccessful()) {
                handleResponse(urlConnection, rule, baseUrl, response);
            } else {
                String responseContent = HttpUtils.readFromStream(urlConnection.getErrorStream() != null ? urlConnection.getErrorStream() : urlConnection.getInputStream());
                log.warning(String.format("Zendesk API error code: %s, message: %s, content = %s", status, urlConnection.getResponseMessage(), responseContent));
                if (!HttpUtils.isJsonContent(responseContent)) {
                    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                }
                response.getWriter().print(responseContent);
            }
        } catch (Exception ex) {
            handleCallException(ex, targetUrl, response);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private Rule findRule(String uri, String method, HttpServletResponse response) throws IOException {
        Rule rule;
        if (!config.hasRules()) { //empty rules == disable rules check
            return new Rule();
        }
        rule = findRule(config.getRules(), method, uri.toLowerCase());
        if (rule != null) {
            return rule;
        }
        log.warning(uri + " is not allowed");
        response.setStatus(403);
        response.getWriter().print("{\"error\":\"Url " + uri + " is not allowed\"} ");
        return null;
    }

    private Rule findRule(List<Rule> rules, String method, String uri) {
        if (rules == null) {
            return null;
        }
        for (Rule rule : rules) {
            if (rule.getMethod().equalsIgnoreCase(method) && uri.matches("^" + rule.getUri() + ".*")) {
                return rule;
            }
        }
        return null;
    }

    private String extractTargetRequest(HttpServletRequest request, Rule rule, String uri, HttpServletResponse response) throws IOException {
        if (rule == null) {
            return null;
        }
        if (rule.isEmpty()) {
            String targetRequest = IOUtils.toString(request.getInputStream(), HttpUtils.UTF8);
            return targetRequest != null ? targetRequest : "";
        }
        JSONObject requestData = parseRequestData(request);
        log.info("Request uri: " + uri + " | Rule string: " + rule.getUri() + " | Rule name: " + rule.getName());
        if (rule.getRequestFields() != null && !rule.getRequestFields().isEmpty()) {
            String errorField = checkRequest("", requestData, rule.getRequestFields());
            if (errorField != null) {
                log.warning("Field " + errorField + " is not allowed in request body");
                response.setStatus(403);
                response.getWriter().print("{\"error\":\"Field " + errorField + " is not allowed in request body\"} ");
                return null;
            }
        }
        return requestData != null ? requestData.toString() : "";
    }

    private boolean validateRequest(HttpServletRequest request, HttpServletResponse response, String uri) throws IOException {
        if (!"https".equals(isStandaloneMode ? request.getScheme() : request.getHeader("X-Forwarded-Proto"))) {
            response.setStatus(500);
            response.getWriter().print("{\"error\":\"Only https protocol is supported\"} ");
            return false;
        }

        if (uri.equals("/")) {
            response.setStatus(200);
            response.getWriter().print(String.format("{\"info\":\"DPP v%s is ready for operation\"}", dppVersion));
            return false;
        }

        return authService.authorize(request, response, uri);
    }

    private void handleResponse(HttpURLConnection urlConnection, Rule rule, String baseUrl, HttpServletResponse response) throws IOException {
        if (HttpMethod.DELETE.matches(StringUtils.hasLength(rule.getMethod()) ? rule.getMethod() : "")) {
            return; //no response
        }
        String targetResponse = HttpUtils.readFromStream(urlConnection.getInputStream());
        if (config.hasRules()) {
            JSONObject responseData = StringUtils.hasLength(targetResponse) ? new JSONObject(targetResponse) : new JSONObject();
            if (log.isLoggable(Level.FINE)) {
                log.config("Original data:" + targetResponse);
            }
            responseData = replaceJson("", responseData, !rule.isEmpty() ? rule.getResponseFields() : null);
            targetResponse = responseData.toString();
        }
        String replaceUrl = (StringUtils.hasLength(proxyUrl) ? proxyUrl : baseUrl).replaceFirst("/$", "");
        targetResponse = targetResponse.replace(zendeskApiUrl.replaceFirst("/$", ""), replaceUrl);
        if (log.isLoggable(Level.FINE)) {
            log.config("Response data:" + targetResponse);
        }
        response.getWriter().print(targetResponse);
    }

    private JSONObject parseRequestData(HttpServletRequest request) {
        try {
            return new JSONObject(request.getReader().lines().collect(Collectors.joining(System.lineSeparator())));
        } catch (Exception ignored) {}
        return null;
    }

    private void handleCallException(Exception ex, String url, HttpServletResponse response) throws IOException {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        printWriter.flush();
        log.severe(String.format("Exception when call %s: %s", url, writer));
        response.setStatus(500);
        response.getWriter().print(String.format("{\"error\":\"Call %s failed: %s\"} ", url, ex.getMessage()));
    }

    private String checkRequest(String parent_key, JSONObject json, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        Iterator<?> keys = json.keys();
        String error_field;
        while (keys.hasNext()) {
            String key = (String) keys.next();
            String check_key;
            if (parent_key != null && !parent_key.isEmpty()) {
                check_key = parent_key + "." + key;
            } else {
                check_key = key;
            }
            if (!fields.contains(check_key)) {
                if (json.get(key) instanceof JSONObject) {
                    error_field = checkRequest(check_key, json.getJSONObject(key), fields);
                    if (error_field != null) {
                        return error_field;
                    }

                } else {
                    error_field = key;
                    return error_field;
                }
            }
        }
        return null;
    }

    private JSONObject replaceJson(String parent_key, JSONObject json, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return json;
        }
        Iterator<?> keys = json.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            String check_key;
            if (parent_key != null && !parent_key.isEmpty()) {
                check_key = parent_key + "." + key;
            } else {
                check_key = key;
            }

            if (!fields.contains(check_key)) {
                if (json.get(key) instanceof JSONObject) {
                    replaceJson(check_key, json.getJSONObject(key), fields);
                } else if (json.get(key) instanceof JSONArray) {
                    JSONArray json_array = json.getJSONArray(key);
                    for (int i = 0; i < json_array.length(); i++) {
                        if (json_array.get(i) instanceof JSONObject) {
                            JSONObject json_sub = json_array.getJSONObject(i);
                            replaceJson(check_key, json_sub, fields);
                        } else {
                            json_array.put(i, JSONObject.NULL);
                        }
                    }
                } else {
                    json.put(key, JSONObject.NULL);
                }
            }
        }
        return json;
    }
}