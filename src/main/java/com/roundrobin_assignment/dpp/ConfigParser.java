package com.roundrobin_assignment.dpp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ConfigParser {

    private static final String CONFIG_FILE = "/config.xml";

    @Bean
    public Config config() {
        try {
            return parse(Config.class.getResourceAsStream(CONFIG_FILE));
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Fail to load %s. ERROR: %s, Cause: %s", CONFIG_FILE, ex.getMessage(), ex.getCause()));
        }
    }

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

    private List<String> parseFields(NodeList fields) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < fields.getLength(); i++) {
            Node field = fields.item(i);
            if (field.getNodeName().equals("field") && !field.getTextContent().isEmpty()) {
                result.add(field.getTextContent());
            }
        }
        return result;
    }
}
