package com.roundrobin_assignment.dpp;

import java.util.List;

public class Config {

    private List<Rule> rules;

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public List<Rule> getRules() {
        return this.rules;
    }

    public boolean hasRules() {
        return this.rules != null && this.rules.size() > 0;
    }
}
