package com.roundrobin_assignment.dpp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.logging.Logger;

import static com.roundrobin_assignment.dpp.Encryptor.*;
import static com.roundrobin_assignment.dpp.HttpUtils.*;
import static com.roundrobin_assignment.dpp.HttpUtils.readFromStream;

@Service
public class OAuthService {

    private static final Logger log = Logger.getLogger(OAuthService.class.getName());

    public static final String OAUTH_URI = "/oauth";
    private static final String OAUTH_AUTHORIZATION_NEW = OAUTH_URI + "/authorizations/new";
    private static final String OAUTH_AUTHORIZATION_URL = "https://%s.zendesk.com" + OAUTH_AUTHORIZATION_NEW +
            "?response_type=code&client_id=%s&scope=%s&code_challenge_method=S256&code_challenge=%s" +
            "&redirect_uri=%s&state=%s";

    private static final String OAUTH_REDIRECT_URI = OAUTH_URI + "/oauth_token";
    private static final String OAUTH_REFRESH_URI = OAUTH_URI + "/tokens";
    private static final String OAUTH_TOKENS_URL = "https://%s.zendesk.com" + OAUTH_URI + "/tokens";

    private static final String BEARER_AUTH = "Bearer ";
    private static final String DECODED_TOKEN = "decodedToken";

    @Value("${zendesk.api.url}")
    private String zendeskApiUrl;

    @Value("${proxy.public.url}")
    private String proxyUrl;

    @Value("${dpp.secret.key}")
    private String dppSecretKey;

    @Value("${zendesk.oauth.client.id}")
    private String oauthClientId;

    @Value("${zendesk.oauth.scope}")
    private String oauthScope;

    @Value("${zendesk.oauth.token.expires}")
    private String oauthTokenExpires;

    @Value("${zendesk.oauth.refreshToken.expires}")
    private String oauthRefreshTokenExpires;

    private String zendeskSubdomain = null;

    public boolean authorize(HttpServletRequest request, HttpServletResponse response, String uri) throws IOException {
        if (uri.startsWith(OAUTH_URI)) {
            return true; //handle later in handleOAuthRequest
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasLength(authHeader) || !authHeader.startsWith(BEARER_AUTH)) {
            String reason = StringUtils.hasLength(authHeader) ? "- bearer required" : "empty";
            log.warning("Couldn't authenticate " + reason);
            response.setStatus(401);
            response.getWriter().print(String.format("{\"error\":\"Couldn't authenticate %s\"} ", reason));
            return false;
        }

        try {
            request.setAttribute(DECODED_TOKEN, decryptToken(authHeader));
        } catch (Exception ex) {
            log.severe(ex.toString());
            log.warning("Couldn't authenticate you");
            response.setStatus(401);
            response.getWriter().print("{\"error\":\"Couldn't authenticate you\"} ");
            return false;
        }
        return true;
    }

    public String getZendeskAuth(HttpServletRequest request) {
        return BEARER_AUTH + request.getAttribute(DECODED_TOKEN);
    }

    public void handleOAuthRequest(HttpServletRequest request, HttpServletResponse response, String uri) throws IOException {
        if (uri.startsWith(OAUTH_AUTHORIZATION_NEW)) {
            String redirectUrl = createOAuthUrl(request);
            log.info("Redirect to " + redirectUrl);
            response.sendRedirect(redirectUrl);
            return;
        }
        if (uri.startsWith(OAUTH_REDIRECT_URI)) {
            String error = request.getParameter("error");
            String code = request.getParameter("code");
            if (StringUtils.hasLength(error) || !StringUtils.hasLength(code)) {
                handleOAuthError(request, response, StringUtils.hasLength(error) ? error : "No access code in Zendesk call");
            } else {
                handleOauthAccessCode(code, request.getParameter("state"), request, response);
            }
            return;
        }
        if (uri.startsWith(OAUTH_REFRESH_URI)) {
            handleRefreshToken(request, response);
        }
    }

    private String createOAuthUrl(HttpServletRequest request) {
        JSONObject authState = new JSONObject();
        String projectId = request.getParameter("project");
        authState.put("project", projectId);
        authState.put("originalRedirectUri", request.getParameter("redirect_uri"));
        authState.put("originalState", request.getParameter("state"));

        String key = dppSecretKey + projectId;
        String codeVerifier = Encryptor.generateCodeVerifier(key);
        String codeChallenge = Encryptor.generateCodeChallenge(codeVerifier);

        return String.format(OAUTH_AUTHORIZATION_URL,
                urlEncode(getZendeskSubdomain()),
                urlEncode(oauthClientId),
                urlEncode(oauthScope),
                codeChallenge,
                urlEncode(createRedirectUrl(request)),
                base64UrlEncode(authState.toString()));
    }

    private void handleOAuthError(HttpServletRequest request, HttpServletResponse response, String error) throws IOException {
        String errorDescription = request.getParameter("error_description");
        errorDescription = StringUtils.hasLength(errorDescription) ? errorDescription : "";
        String state = request.getParameter("state");
        if (!StringUtils.hasLength(state)) {
            sendInternalOAuthError(response, error, errorDescription);
            return;
        }
        JSONObject authState = new JSONObject(base64UrlDecodeString(state));
        String project = authState.optString("project");
        if (!StringUtils.hasLength(project)) {
            sendInternalOAuthError(response, error, errorDescription + " Illegal state: absent project");
            return;
        }
        String redirectUri = urlDecode(authState.optString("originalRedirectUri"));
        if (!StringUtils.hasLength(redirectUri)) {
            sendInternalOAuthError(response, error, errorDescription + " Illegal state: not found original redirect url");
            return;
        }
        String originalState = authState.optString("originalState");
        if (!StringUtils.hasLength(originalState)) {
            sendInternalOAuthError(response, error, errorDescription + " Illegal state: not found original state");
            return;
        }
        log.severe(String.format("Receive error from Zendesk: %s, description: %s", error, errorDescription));
        String redirectUrl = String.format("%s?project=%s&error=%s&error_description=%s&state=%s", redirectUri, project,
                urlEncode(error), urlEncode(errorDescription), originalState);
        log.warning("Redirect error to " + redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    private void sendInternalOAuthError(HttpServletResponse response, String error, String errorDescription) throws IOException {
        log.severe(String.format("Handle OAuth error: %s, description: %s", error, errorDescription));
        response.setStatus(500);
        response.getWriter().print(String.format("{\"error\":\"%s\", \"error_description\":\"%s\"}", error, errorDescription));
    }

    private void handleOauthAccessCode(String code, String state, HttpServletRequest request, HttpServletResponse response) throws IOException {
        JSONObject authState = new JSONObject(base64UrlDecodeString(state));
        String project = authState.optString("project");
        String redirectUri = urlDecode(authState.optString("originalRedirectUri"));
        if (!StringUtils.hasLength(redirectUri)) {
            sendInternalOAuthError(response,"Not found original redirect url", "");
            return;
        }

        JSONObject oauthTokenResponse = requestOAuthToken(code, project, request);
        JSONObject jsonContent = new JSONObject();
        jsonContent.put("access_token", Encryptor.encrypt(dppSecretKey, oauthTokenResponse.optString("access_token")));
        jsonContent.put("refresh_token", Encryptor.encrypt(dppSecretKey, oauthTokenResponse.optString("refresh_token")));

        String originalState = authState.optString("originalState");
        redirectUri += String.format("?project=%s&state=%s", project, originalState);
        HttpURLConnection connection = createHttpPostURLConnection(redirectUri, null, jsonContent.toString());
        int responseCode = connection.getResponseCode();
        log.info("Register token complete with code: " + responseCode);
        String rrResponse = readFromStream(connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream());
        String resultRedirectUrl = HttpUtils.isJsonContent(rrResponse) ? new JSONObject(rrResponse).optString("redirect") : null;
        if (responseCode == HttpStatus.OK.value() && StringUtils.hasLength(resultRedirectUrl)) {
            log.info("Redirect successful to " + resultRedirectUrl);
        } else {
            String error = "Create token failed with code " + responseCode;
            log.severe(error + (StringUtils.hasLength(rrResponse) ? " content=" + rrResponse : ""));
            if (!StringUtils.hasLength(resultRedirectUrl)) {
                resultRedirectUrl = String.format("%s?project=%s&error=%s&error_description=%s&state=%s", redirectUri, project,
                        urlEncode(error), urlEncode(rrResponse), originalState);
            }
            log.warning("Redirect create token failure to " + resultRedirectUrl);
        }
        response.sendRedirect(resultRedirectUrl);
    }

    private JSONObject requestOAuthToken(String code, String project, HttpServletRequest request) {
        String errorMessage;
        try {
            String url = String.format(OAUTH_TOKENS_URL, getZendeskSubdomain());
            String requestBody = createTokenRequestBody(code, project, request);
            HttpURLConnection connection = createHttpPostURLConnection(url, null, requestBody);
            int responseCode = connection.getResponseCode();
            log.info(String.format("Request token from %s complete with code %s", url, responseCode));
            String response = readFromStream(connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream());
            if (responseCode == HttpStatus.OK.value() && StringUtils.hasLength(response)) {
                log.info(String.format("Create token successfully from %s", url));
                return new JSONObject(response);
            } else {
                errorMessage = "Create token failed with response code=" + responseCode +
                        (StringUtils.hasLength(response) ? " content=" + response : "");
            }
        } catch (Exception e) {
            errorMessage = "Create token failure " + e.getMessage() + " Exception: " + e;
        }
        log.severe(errorMessage);
        throw new RuntimeException(errorMessage);
    }

    private String createTokenRequestBody(String code, String project, HttpServletRequest request) {
        String key = dppSecretKey + project;
        String codeVerifier = Encryptor.generateCodeVerifier(key);

        JSONObject parametersMap = new JSONObject();
        parametersMap.put("grant_type", "authorization_code");
        parametersMap.put("code", code);
        parametersMap.put("client_id", oauthClientId);
        parametersMap.put("code_verifier", codeVerifier);
        parametersMap.put("scope", oauthScope);
        parametersMap.put("redirect_uri", createRedirectUrl(request));
        parametersMap.put("expires_in", Integer.parseInt(oauthTokenExpires));
        parametersMap.put("refresh_token_expires_in", Integer.parseInt(oauthRefreshTokenExpires));
        return parametersMap.toString();
    }

    private void handleRefreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String refreshRequest = IOUtils.toString(request.getInputStream(), HttpUtils.UTF8);
        JSONObject parametersMap = new JSONObject(refreshRequest);
        parametersMap.put("refresh_token", Encryptor.decrypt(dppSecretKey, parametersMap.getString("refresh_token")));
        parametersMap.put("client_id", oauthClientId);
        parametersMap.put("expires_in", Integer.parseInt(oauthTokenExpires));
        parametersMap.put("refresh_token_expires_in", Integer.parseInt(oauthRefreshTokenExpires));
        String json = parametersMap.toString();
        String url = String.format(OAUTH_TOKENS_URL, getZendeskSubdomain());
        HttpURLConnection connection = createHttpPostURLConnection(url, null, json);
        int responseCode = connection.getResponseCode();
        log.info(String.format("Receive refresh token response from %s with code %s", url, responseCode));
        String refreshResponse = readFromStream(connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream());
        response.setStatus(responseCode);
        if (responseCode == HttpStatus.OK.value() && StringUtils.hasLength(refreshResponse)) {
            JSONObject refreshTokenResponse = new JSONObject(refreshResponse);
            refreshTokenResponse.put("access_token", Encryptor.encrypt(dppSecretKey, refreshTokenResponse.optString("access_token")));
            refreshTokenResponse.put("refresh_token", Encryptor.encrypt(dppSecretKey, refreshTokenResponse.optString("refresh_token")));
            response.getWriter().print(refreshTokenResponse);
        } else {
            String errorMessage = "Refresh token failed with response code=" + responseCode +
                    (StringUtils.hasLength(refreshResponse) ? " content=" + refreshResponse : "");
            log.severe(errorMessage);
            if (StringUtils.hasLength(refreshResponse)) {
                if (!HttpUtils.isJsonContent(refreshResponse)) {
                    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                }
                response.getWriter().print(refreshResponse);
            }
        }
    }

    private String createRedirectUrl(HttpServletRequest request) {
        if (StringUtils.hasLength(proxyUrl)) { //use public dpp address
            return proxyUrl.replaceFirst("/$", "") + OAUTH_REDIRECT_URI;
        }
        return request.getRequestURL().toString().
                replace(OAUTH_AUTHORIZATION_NEW, "").
                replace(OAUTH_REDIRECT_URI, "").
                replace("http://", "https://")
                + OAUTH_REDIRECT_URI; //https required
    }

    private String extractSubdomainOnly(String zendeskUrl) {
        String subdomain = zendeskUrl.replaceAll("^http(s)?://", "");
        subdomain = subdomain.replaceAll(".zendesk.com.*", "");
        subdomain = subdomain.replaceAll(".*@", "");
        return subdomain;
    }

    private String getZendeskSubdomain() {
        if (zendeskSubdomain == null) {
            zendeskSubdomain = extractSubdomainOnly(zendeskApiUrl);
        }
        return zendeskSubdomain;
    }

    private String decryptToken(String authHeader) {
        return Encryptor.decrypt(dppSecretKey, authHeader.replace(BEARER_AUTH, ""));
    }
}
