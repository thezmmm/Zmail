package com.zmail.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Applies HTTP/HTTPS proxy settings for the dev profile so that outbound calls
 * (Google API, OpenAI, etc.) are routed through the local Clash proxy.
 *
 * Configure via application-dev.yml:
 *   dev.proxy.host = 127.0.0.1
 *   dev.proxy.port = 7890
 *
 * Set host to an empty string to disable.
 */
@Slf4j
@Configuration
@Profile("dev")
public class DevProxyConfig {

    @Value("${dev.proxy.host:}")
    private String host;

    @Value("${dev.proxy.port:7890}")
    private int port;

    @PostConstruct
    void apply() {
        if (host == null || host.isBlank()) {
            log.info("Dev proxy disabled (dev.proxy.host is empty)");
            return;
        }
        String portStr = String.valueOf(port);
        System.setProperty("http.proxyHost",  host);
        System.setProperty("http.proxyPort",  portStr);
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", portStr);
        // Bypass proxy for localhost traffic
        System.setProperty("http.nonProxyHosts",  "localhost|127.*|[::1]");
        System.setProperty("https.nonProxyHosts", "localhost|127.*|[::1]");
        log.info("Dev proxy configured: {}:{}", host, port);
    }
}
