package com.roundrobin_assignment.dpp;

import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class HttpUtils {

    public static final Charset UTF8 = StandardCharsets.UTF_8;
    public static final String JSON_CONTENT_TYPE = "application/json";

    private static final int HTTP_TIMEOUT = 60000;
    private static final int RESPONSE_BUFFER_SIZE = 1024;

    public static HttpURLConnection createHttpURLConnection(HttpServletRequest request, String webPage, String auth, String targetRequest) throws IOException {
        HttpURLConnection urlConnection = createHttpURLConnection(webPage, request.getMethod(), auth);

        for (Enumeration<?> e = request.getHeaderNames(); e.hasMoreElements(); ) {
            String headerName = (String) e.nextElement();
            if (headerName.equals("X-Zendesk-Marketplace-Name")
                    || headerName.equals("X-Zendesk-Marketplace-Organization-Id")
                    || headerName.equals("X-Zendesk-Marketplace-App-Id")) {
                String headerValue = request.getHeader(headerName);
                urlConnection.setRequestProperty(headerName, headerValue);
            }
        }
        urlConnection.setDoOutput(true);
        //urlConnection.connect();
        if (!targetRequest.isEmpty()) {
            urlConnection.setDoInput(true);
            try (OutputStreamWriter wr = new OutputStreamWriter(urlConnection.getOutputStream(), UTF8)) {
                wr.write(targetRequest);
            }
        }
        return urlConnection;
    }

    public static HttpURLConnection createHttpPostURLConnection(String url, String auth, String body) throws IOException {
        HttpURLConnection urlConnection = createHttpURLConnection(url, HttpMethod.POST.name(), auth);
        urlConnection.setDoOutput(true);
        if (StringUtils.hasLength(body)) {
            urlConnection.setDoInput(true);
            //urlConnection.connect();
            try (OutputStreamWriter wr = new OutputStreamWriter(urlConnection.getOutputStream(), UTF8)) {
                wr.write(body);
            }
        }
        return urlConnection;
    }

    public static HttpURLConnection createHttpURLConnection(String url, String method, String auth) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url.trim()).openConnection();
        urlConnection.setRequestMethod(method);
        urlConnection.setConnectTimeout(HTTP_TIMEOUT);
        urlConnection.setReadTimeout(HTTP_TIMEOUT);
        urlConnection.setRequestProperty("Accept-Charset", String.format("%s;q=0.9, *;q=0.1", UTF8.name()));
        urlConnection.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);
        //urlConnection.setRequestProperty("Accept", Config.JSON_CONTENT_TYPE);
        urlConnection.setRequestProperty("Accept", "*/*");
        if (StringUtils.hasLength(auth)) {
            urlConnection.setRequestProperty("Authorization", auth);
        }
        return urlConnection;
    }

    public static String readFromStream(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        int numCharsRead;
        char[] charArray = new char[RESPONSE_BUFFER_SIZE];
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader isr = new InputStreamReader(is, UTF8)) {
            while ((numCharsRead = isr.read(charArray)) > 0) {
                sb.append(charArray, 0, numCharsRead);
            }
        }
        return sb.toString();
    }

    public static boolean isJsonContent(String content) {
        if (!StringUtils.hasLength(content)) {
            return false;
        }
        try {
            new JSONObject(content);
        } catch (JSONException e) {
            return false;
        }
        return true;
    }
}
