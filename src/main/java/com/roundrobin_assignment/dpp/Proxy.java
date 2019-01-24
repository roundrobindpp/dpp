//Legal notice:

package com.roundrobin_assignment.dpp;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;
import java.nio.charset.Charset;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Enumeration;
import java.io.File;
import java.io.FileNotFoundException;

public class Proxy extends HttpServlet {
    private static final Logger log = Logger.getLogger(Proxy.class.getName());
    String baseUrl;
    String targetUrl;
    String logLevel = "";

    private String checkReq (String parent_key, JSONObject json, List<String> fields) {
        Iterator<?> keys = json.keys();
        String error_field = null;
        while( keys.hasNext() ) {
            String key = (String)keys.next();
            String check_key;
            if (parent_key != null && !parent_key.isEmpty()) {
                check_key = parent_key + "." + key;
            }
            else {
                check_key = key;
            }
            if (!fields.contains(check_key)) {
                if (json.get(key) instanceof JSONObject) {
                    error_field = checkReq(check_key, json.getJSONObject(key), fields);
                    if (error_field != null) {
                        return error_field;
                    }

                }
                else {
                        error_field = key;
                        return error_field;
                }
            }
        }
        return null;
    }

    private JSONObject replaceJson (String parent_key, JSONObject json, List<String> fields) {
        Iterator<?> keys = json.keys();
        while( keys.hasNext() ) {
            String key = (String)keys.next();
            String check_key;
            if (parent_key != null && !parent_key.isEmpty()) {
                check_key = parent_key + "." + key;
            }
            else {
                check_key = key;
            }

            if (!fields.contains(check_key)) {
                if (json.get(key) instanceof JSONObject) {
                    JSONObject json_sub = replaceJson(check_key, json.getJSONObject(key), fields);
                } else if (json.get(key) instanceof JSONArray) {
                    JSONArray json_array = json.getJSONArray(key);
                    for (int i = 0 ; i < json_array.length(); i++) {
                        if (json_array.get(i) instanceof JSONObject) {
                            JSONObject json_sub = json_array.getJSONObject(i);
                            json_sub = replaceJson(check_key, json_sub, fields);
                        }
                        else {
                            json_array.put(i, JSONObject.NULL);
                        }
                    }
                }
                else {json.put(key, JSONObject.NULL);
                }
            }
        }
        return json;
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = req.getMethod();
        String uri = "";
        if (req.getQueryString() != null) uri = req.getRequestURI()+"?"+req.getQueryString(); else uri = req.getRequestURI();
        baseUrl = req.getRequestURL().substring(0, req.getRequestURL().length() - req.getRequestURI().length()) + req.getContextPath();
        String authHeader = req.getHeader("Authorization");
        if (uri.equals("/_ah/start")) {
            return;
        }
		
        if (uri.equals("/")) {
            resp.setStatus(200);
            resp.getWriter().print("{\"info\":\"DPP is ready for operation\"} ");
            return;
        }

        if (authHeader == null)
        {
            log.warning("Couldn't authenticate you");
            resp.setStatus(401);
            resp.getWriter().print("{\"error\":\"Couldn't authenticate you\"} ");
            return;
        }


        String apiurl = "";
        String user = "";
        String password = "";
        String proxyuser = "";
        String proxypassword = "";
        String rule_method;
        String rule_uri = "";
        String rule_name = "";
        Node current_rule = null;
        NodeList rule_configs;
        NodeList rules = null;
        Node rule_config;
        JSONObject req_data;
        Boolean find_rule = false;
		
        try {
            req_data = new JSONObject(req.getReader().lines().collect(Collectors.joining(System.lineSeparator())));
        }
        catch (Exception e) {
            req_data = null;
        }
        JSONObject resp_data;
        List<String> lt_req_fields = new ArrayList();
        List<String> lt_resp_fields = new ArrayList();
        resp.setContentType("application/javascript");
        resp.setCharacterEncoding("UTF-8");
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new File("WEB-INF/config.xml"));
            NodeList nodelist = doc.getChildNodes();
            Node configNode = nodelist.item(0);
            nodelist = configNode.getChildNodes();

            for (int i=0; i < nodelist.getLength(); i++) {
                Node subnode = nodelist.item(i);
                if (subnode.getNodeName().equals("baseurl")) {
                    apiurl = subnode.getTextContent();
                }
                if (subnode.getNodeName().equals("apiuser")) {
                    user = subnode.getTextContent();
                }
                if (subnode.getNodeName().equals("apipassword")) {
                    password = subnode.getTextContent();
                }
                if (subnode.getNodeName().equals("proxyuser")) {
                    proxyuser = subnode.getTextContent();
                }
                if (subnode.getNodeName().equals("proxypassword")) {
                    proxypassword = subnode.getTextContent();
                }
                if (subnode.getNodeName().equals("loglevel")) {
                    logLevel = subnode.getTextContent();
                }
                if (subnode.getNodeName().equals("rules")) {
                    rules = subnode.getChildNodes();
                }

            }

            String checkAuthString = proxyuser + ":" + proxypassword;
            byte[] checkAuthEncBytes = Base64.getEncoder().encode(checkAuthString.getBytes());
            String checkAuthStringEnc = new String(checkAuthEncBytes);
            if (!authHeader.equals("Basic " + checkAuthStringEnc)) {
                log.warning("Couldn't authenticate you");
                resp.setStatus(401);
                resp.getWriter().print("{\"error\":\"Couldn't authenticate you\"} ");
                return;
            }

            switch(logLevel.toLowerCase()) {
                case "warning" :
                    log.setLevel(Level.WARNING);
                    break;
                case "config" :
                    log.setLevel(Level.CONFIG);
                    break;
                default :
                    log.setLevel(Level.INFO);
            }

            if (rules != null) {
                for (int j = 0; j < rules.getLength(); j++) {
                    Node rule = rules.item(j);
                    if (rule.getNodeName().equals("rule")) {
                        rule_configs = rule.getChildNodes();
                        rule_name = "";
                        rule_method = "";
                        rule_uri = "";
                        lt_resp_fields.clear();
                        lt_req_fields.clear();
                        for (int r = 0; r < rule_configs.getLength(); r++) {
                            rule_config = rule_configs.item(r);
                            if (rule_config.getNodeName().equals("method")) {
                                rule_method = rule_config.getTextContent();
                            }
                            if (rule_config.getNodeName().equals("uri")) {
                                rule_uri = rule_config.getTextContent();
                            }
                            if (rule_config.getNodeName().equals("name")) {
                                rule_name = rule_config.getTextContent();
                            }
                            if (rule_config.getNodeName().equals("response")) {
                                NodeList resp_fields = rule_config.getChildNodes();
                                for (int f = 0; f < resp_fields.getLength(); f++) {
                                    Node field = resp_fields.item(f);
                                    if (!field.getTextContent().isEmpty()) {
                                        lt_resp_fields.add(field.getTextContent());
                                    }
                                }
                            }
                            if (rule_config.getNodeName().equals("request")) {
                                NodeList req_fields = rule_config.getChildNodes();
                                for (int f = 0; f < req_fields.getLength(); f++) {
                                    Node field = req_fields.item(f);
                                    if (!field.getTextContent().isEmpty()) {
                                        lt_req_fields.add(field.getTextContent());
                                    }
                                }
                            }
                        }
                        if (rule_name != null) {
                            if ((rule_method.toUpperCase().equals(method.toUpperCase())) && (uri.toLowerCase().matches("^"+rule_uri.toLowerCase()+".*"))) {
                                current_rule = rule;
                                find_rule = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!find_rule) {
                log.warning(uri+" is not allowed");
                resp.setStatus(403);
                resp.getWriter().print("{\"error\":\""+uri+" is not allowed\"} ");
                return;
            }
            log.info("Request uri: " + uri+" | Rule string: " + rule_uri+" | Rule name: " + rule_name );
            if (lt_req_fields.size() > 0) {
                String lv_error_field = checkReq("", req_data, lt_req_fields);
                if (lv_error_field != null) {
                    log.warning("Field " + lv_error_field+" is not allowed in request body");
                    resp.setStatus(403);
                    resp.getWriter().print("{\"error\":\"Field "+lv_error_field+" is not allowed in request body\"} ");
                    return;
                }

            }
            String webPage = apiurl + uri;
            log.config("Target url: " + webPage);
            targetUrl = apiurl;
            String authString = user + ":" + password;
            byte[] authEncBytes = Base64.getEncoder().encode(authString.getBytes());
            String authStringEnc = new String(authEncBytes);
            URL url = new URL(webPage.trim());
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                urlConnection.setRequestProperty("Accept-Charset", "UTF-8");
                urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestMethod(method);

                for (Enumeration<?> e = req.getHeaderNames(); e.hasMoreElements();) {
                    String headerName = (String) e.nextElement();
                    if (headerName.equals("X-Zendesk-Marketplace-Name")||headerName.equals("X-Zendesk-Marketplace-Organization-Id")||headerName.equals("X-Zendesk-Marketplace-App-Id")) {
                        String headerValue = req.getHeader(headerName);
                        urlConnection.setRequestProperty(headerName, headerValue);
                    }
                }
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.connect();
                if (req_data != null) {
                    OutputStreamWriter wr = new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8");
                    wr.write(req_data.toString());
                    wr.flush();
                    wr.close();
                }
                int status = urlConnection.getResponseCode();
                log.config("Req status: " + status);
                if (status == HttpURLConnection.HTTP_OK) {
                    InputStream is = urlConnection.getInputStream();
                    Charset charset = Charset.forName("UTF8");
                    InputStreamReader isr = new InputStreamReader(is, charset);

                    int numCharsRead;
                    char[] charArray = new char[1024];
                    StringBuffer sb = new StringBuffer();
                    while ((numCharsRead = isr.read(charArray)) > 0) {
                        sb.append(charArray, 0, numCharsRead);
                    }
                    isr.close();
                    resp_data = new JSONObject(sb.toString());
                    if (log.getLevel()==Level.CONFIG) log.config("Original data:" + resp_data.toString());
                    resp_data = replaceJson("", resp_data, lt_resp_fields);
                    if (log.getLevel()==Level.CONFIG) log.config("Responce data:" + resp_data.toString().replace(targetUrl, baseUrl));
                    resp.getWriter().print(resp_data.toString().replace(targetUrl, baseUrl));
                } else {
                    resp.setStatus(status);
                    log.warning("Zendesk API error code: "+ status + ", message: " + urlConnection.getResponseMessage());
                }
            }
            finally {
                urlConnection.disconnect();
            }
        }
        catch (ParserConfigurationException ex) {
            resp.setStatus(500);
            log.warning("Wrong configuration file");
            resp.getWriter().print("{\"error\":\"Wrong configuration file\"} ");
        }
        catch (SAXException ex) {
            resp.setStatus(500);
            log.warning("Wrong configuration file");
            resp.getWriter().print("{\"error\":\"Wrong configuration file\"} ");
        }
        catch (FileNotFoundException ex) {
            resp.setStatus(500);
            log.warning("Configuration file not found");
            resp.getWriter().print("{\"error\":\"Configuration file not found\"} ");
        }
    }

    @Override
    public void destroy() {}
}