package com.roundrobin_assignment.dpp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ProxyApplication {

    @Value("${spring.profiles.active}")
    private String springProfile;

    public static void main(String[] args) {
        SpringApplication.run(ProxyApplication.class, args);
    }

    @Bean
    public ServletRegistrationBean<Proxy> proxyServletBean() {
        return new ServletRegistrationBean<>(new Proxy("standalone".equalsIgnoreCase(springProfile)), "/");
    }
}
