package com.roundrobin_assignment.dpp;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ConfigParser {
    public Config parse(InputStream configStream) {
        Config config = new Config();
        NodeList rules = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(configStream);
            NodeList nodelist = doc.getChildNodes();
            Node configNode = nodelist.item(0);
            nodelist = configNode.getChildNodes();

            for (int i = 0; i < nodelist.getLength(); i++) {
                Node subnode = nodelist.item(i);
                if (subnode.getNodeName().equals("baseurl")) {
                    config.setApiUrl(subnode.getTextContent());
                }
                if (subnode.getNodeName().equals("apiuser")) {
                    config.setUser(subnode.getTextContent());
                }
                if (subnode.getNodeName().equals("apipassword")) {
                    config.setPassword(subnode.getTextContent());
                }
                if (subnode.getNodeName().equals("proxyurl")) {
                    config.setProxyUrl(subnode.getTextContent());
                }
                if (subnode.getNodeName().equals("proxyuser")) {
                    config.setProxyUser(subnode.getTextContent());
                }
                if (subnode.getNodeName().equals("proxypassword")) {
                    config.setProxyPassword(subnode.getTextContent());
                }
                if (subnode.getNodeName().equals("loglevel")) {
                    config.setLogLevel(subnode.getTextContent());
                }
                if (subnode.getNodeName().equals("rules")) {
                    rules = subnode.getChildNodes();
                }

            }
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException("Wrong configuration file: parse error", ex);
        } catch (SAXException ex) {
            throw new RuntimeException("Wrong configuration file: SAX parser error", ex);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Configuration file not found", ex);
        } catch (IOException ex) {
            throw new RuntimeException("Read configuration file error", ex);
        }

        config.setAuthHeader(authHeader(config.getUser(), config.getPassword()));
        config.setProxyAuthHeader(authHeader(config.getProxyUser(), config.getProxyPassword()));

        config.setRules(parseRules(rules));

        return config;
    }

    private List<Rule> parseRules(NodeList nodes) {
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeName().equals("node")) {
                continue;
            }
            NodeList ruleConfigs = node.getChildNodes();
            Rule rule = new Rule();
            for (int j = 0; j < ruleConfigs.getLength(); j++) {
                Node ruleConfig = ruleConfigs.item(j);
                if (ruleConfig.getNodeName().equals("method")) {
                    rule.setMethod(ruleConfig.getTextContent().toUpperCase());
                }
                if (ruleConfig.getNodeName().equals("uri")) {
                    rule.setUri(ruleConfig.getTextContent().toLowerCase());
                }
                if (ruleConfig.getNodeName().equals("name")) {
                    rule.setName(ruleConfig.getTextContent());
                }
                if (ruleConfig.getNodeName().equals("request")) {
                    rule.setRequestFields(parseFields(ruleConfig.getChildNodes()));
                }
                if (ruleConfig.getNodeName().equals("response")) {
                    rule.setResponseFields(parseFields(ruleConfig.getChildNodes()));
                }
            }
            if (rule.getUri() != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private static List<String> parseFields(NodeList fields) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < fields.getLength(); i++) {
            Node field = fields.item(i);
            if (field.getNodeName().equals("field") && !field.getTextContent().isEmpty()) {
                result.add(field.getTextContent());
            }
        }
        return result;
    }

    private String authHeader(String user, String password) {
        String authString = user + ":" + password;
        byte[] authEncBytes = Base64.getEncoder().encode(authString.getBytes());
        return "Basic " + new String(authEncBytes);
    }
}
