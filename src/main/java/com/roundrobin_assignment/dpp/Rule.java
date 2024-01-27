package com.roundrobin_assignment.dpp;

import java.util.List;

public class Rule {
    private String name;
    private String method;
    private String uri;
    private List<String> requestFields;
    private List<String> responseFields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public List<String> getRequestFields() {
        return requestFields;
    }

    public List<String> getResponseFields() {
        return responseFields;
    }

    public void setRequestFields(List<String> requestFields) {
        this.requestFields = requestFields;
    }

    public void setResponseFields(List<String> responseFields) {
        this.responseFields = responseFields;
    }
}
