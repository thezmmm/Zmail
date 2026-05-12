package com.zmail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
            // Set JVM system proxy so Google API Client (NetHttpTransport) also uses it
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost, proxyPort)));
            return new RestTemplate(factory);
        }
        return new RestTemplate();
    }

    // Prevent JwtAuthenticationFilter (@Component) from being auto-registered as a
    // Servlet filter — it must only run inside Spring Security's filter chain,
    // otherwise SecurityContextHolderFilter resets the context after our filter sets auth.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }
}