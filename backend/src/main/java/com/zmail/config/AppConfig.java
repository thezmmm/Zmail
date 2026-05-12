package com.zmail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
public class AppConfig {

    @Value("${zmail.proxy.host:}")
    private String proxyHost;

    @Value("${zmail.proxy.port:0}")
    private int proxyPort;

    @Bean
    public RestTemplate restTemplate() {
        if (!proxyHost.isBlank() && proxyPort > 0) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost, proxyPort)));
            return new RestTemplate(factory);
        }
        return new RestTemplate();
    }
}