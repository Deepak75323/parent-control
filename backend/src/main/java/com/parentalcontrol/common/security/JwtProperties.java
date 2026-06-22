package com.parentalcontrol.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "change-me-in-production-use-256-bit-minimum-secret-key!!";
    private long accessTokenExpirationMs = 900_000; // 15 minutes
    private long refreshTokenExpirationMs = 604_800_000; // 7 days
    private long deviceTokenExpirationMs = 31_536_000_000L; // 1 year
    private String issuer = "parental-control-api";
}
