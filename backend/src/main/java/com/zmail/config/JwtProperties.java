package com.zmail.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("zmail.jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private long expiryMs = 86400000L;
}