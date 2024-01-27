package com.roundrobin_assignment.dpp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.servlet.http.HttpServlet;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Enumeration;

public class Proxy extends HttpServlet {

    private static final Logger log = Logger.getLogger(Proxy.class.getName());
    private static Config storedConfig;

    private final boolean isStandaloneMode;

    public Proxy(boolean isStandaloneMode) {
        this.isStandaloneMode = isStandaloneMode;
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String method = request.getMethod();
        String uri = request.getQueryString() != null ? request.getRequestURI() + "?" + request.getQueryString() : request.getRequestURI();
        uri = java.net.URLDecoder.decode(uri, Config.UTF8);
        if (uri.equals("/_ah/start")) {
            return;
        }
        response.setCharacterEncoding(Config.UTF8.name());
        response.setContentType(Config.JSON_CONTENT_TYPE);

        if (!"https".equals(isStandaloneMode ? request.getScheme() : request.getHeader("X-Forwarded-Proto"))) {
            response.setStatus(500);
            response.getWriter().print("{\"error\":\"Only https protocol is supported\"} ");
            return;
        }

        if (uri.equals("/")) {
            response.setStatus(200);
            response.getWriter().print("{\"info\":\"DPP is ready for operation\"} ");
            return;
        }

        String baseUrl = request.getRequestURL().substring(0, request.getRequestURL().length() - request.getRequestURI().length()) + request.getContextPath();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null) {
            log.warning("Couldn't authenticate empty");
            response.setStatus(401);
            response.getWriter().print("{\"error\":\"Couldn't authenticate empty\"} ");
            return;
        }

        Config config = getConfig(response);
        if (config == null) {
            log.warning("Couldn't load config");
            return;
        }

        if (!authHeader.equals(config.getProxyAuthHeader())) {
            log.warning("Couldn't authenticate you");
            response.setStatus(401);
            response.getWriter().print("{\"error\":\"Couldn't authenticate you\"} ");
            return;
        }

        String targetRequest;
        Rule rule = null;
        if (config.hasRules()) { //empty rules == disable rules check
            rule = findRule(config.getRules(), method, uri.toLowerCase());
            if (rule == null) {
                log.warning(uri + " is not allowed");
                response.setStatus(403);
                response.getWriter().print("{\"error\":\"Url " + uri + " is not allowed\"} ");
                return;
            }
            JSONObject requestData = parseRequestData(request);
            log.info("Request uri: " + uri + " | Rule string: " + rule.getUri() + " | Rule name: " + rule.getName());
            if (rule.getRequestFields() != null && !rule.getRequestFields().isEmpty()) {
                String errorField = checkRequest("", requestData, rule.getRequestFields());
                if (errorField != null) {
                    log.warning("Field " + errorField + " is not allowed in request body");
                    response.setStatus(403);
                    response.getWriter().print("{\"error\":\"Field " + errorField + " is not allowed in request body\"} ");
                    return;
                }
            }
            targetRequest = requestData != null ? requestData.toString() : null;

        } else { //empty rules == disable rules check
            targetRequest = IOUtils.toString(request.getInputStream(), Config.UTF8);
            log.info("Request uri: " + uri + " | Rules empty");
        }

        String webPage = config.getApiUrl() + uri;
        log.config("Target url: " + webPage);
        URL url = new URL(webPage.trim());
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            urlConnection.setRequestProperty("Accept-Charset", String.format("%s;q=0.9, *;q=0.1", Config.UTF8.name()));
            urlConnection.setRequestProperty("Authorization", config.getAuthHeader());
            urlConnection.setRequestProperty("Content-Type", Config.JSON_CONTENT_TYPE);
            urlConnection.setRequestProperty("Accept", "*/*");
            urlConnection.setRequestMethod(method);

            for (Enumeration<?> e = request.getHeaderNames(); e.hasMoreElements(); ) {
                String headerName = (String) e.nextElement();
                if (headerName.equals("X-Zendesk-Marketplace-Name")
                        || headerName.equals("X-Zendesk-Marketplace-Organization-Id")
                        || headerName.equals("X-Zendesk-Marketplace-App-Id")) {
                    String headerValue = request.getHeader(headerName);
                    urlConnection.setRequestProperty(headerName, headerValue);
                }
            }
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.connect();
            if (targetRequest != null && !targetRequest.isEmpty()) {
                OutputStreamWriter wr = new OutputStreamWriter(urlConnection.getOutputStream(), Config.UTF8);
                wr.write(targetRequest);
                wr.flush();
                wr.close();
            }
            int status = urlConnection.getResponseCode();
            log.config("Req status: " + status);
            if (status == HttpURLConnection.HTTP_OK) {
                InputStream is = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(is, Config.UTF8);

                int numCharsRead;
                char[] charArray = new char[1024];
                StringBuilder stringBuilder = new StringBuilder();
                while ((numCharsRead = isr.read(charArray)) > 0) {
                    stringBuilder.append(charArray, 0, numCharsRead);
                }
                isr.close();
                String targetResponse = stringBuilder.toString();
                if (config.hasRules()) {
                    JSONObject responseData = new JSONObject(stringBuilder.toString());
                    if (log.getLevel() == Level.CONFIG) {
                        log.config("Original data:" + responseData);
                    }
                    responseData = replaceJson("", responseData, rule != null ? rule.getResponseFields() : null);
                    targetResponse = responseData.toString();
                }
                targetResponse = targetResponse.replace(config.getApiUrl(), baseUrl);
                if (log.getLevel() == Level.CONFIG) {
                    log.config("Response data:" + targetResponse);
                }
                response.getWriter().print(targetResponse);
            } else {
                response.setStatus(status);
                log.warning("Zendesk API error code: " + status + ", message: " + urlConnection.getResponseMessage());
            }
        } catch (Exception ex) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            ex.printStackTrace(printWriter);
            printWriter.flush();
            log.severe(String.format("Exception when call %s: %s", webPage, writer));
            response.setStatus(500);
            response.getWriter().print(String.format("{\"error\":\"Call %s failed: %s\"} ", webPage, ex.getMessage()));
        } finally {
            urlConnection.disconnect();
        }
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

    private JSONObject parseRequestData(HttpServletRequest request) {
        try {
            return new JSONObject(request.getReader().lines().collect(Collectors.joining(System.lineSeparator())));
        } catch (Exception ignored) {}
        return null;
    }

    private static Config getConfig(HttpServletResponse resp) throws IOException {
        if (storedConfig != null) {
            return storedConfig;
        }
        synchronized (Proxy.class) {
            if (storedConfig == null) {
                try {
                    storedConfig = new ConfigParser().parse(Proxy.class.getResourceAsStream("/config.xml"));
                } catch (Exception ex) {
                    resp.setStatus(500);
                    String error = String.format("Fail to load config.xml. ERROR: %s, Cause: %s", ex.getMessage(), ex.getCause());
                    log.warning(error);
                    resp.getWriter().print(String.format("{\"error\":\"%s\"} ", error));
                    return null;
                }
                switch (storedConfig.getLogLevel().toLowerCase()) {
                    case "warning" -> log.setLevel(Level.WARNING);
                    case "config" -> log.setLevel(Level.CONFIG);
                    default -> log.setLevel(Level.INFO);
                }
            }
        }
        return storedConfig;
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