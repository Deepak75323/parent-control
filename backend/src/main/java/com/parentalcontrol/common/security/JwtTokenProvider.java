package com.parentalcontrol.common.security;

import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.domain.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final JwtProperties properties;
  private final SecretKey secretKey;

  public JwtTokenProvider(JwtProperties properties) {
    this.properties = properties;
    String secret = properties.getSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "Refusing to start: JWT_SECRET must be set (see backend/.env.example)");
    }
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String createAccessToken(UUID userId, String email, Role role) {
    return buildToken(
        userId, email, role, TokenType.ACCESS, properties.getAccessTokenExpirationMs(), Map.of());
  }

  public String createRefreshToken(UUID userId, String email, Role role) {
    return buildToken(
        userId, email, role, TokenType.REFRESH, properties.getRefreshTokenExpirationMs(), Map.of());
  }

  public String createDeviceToken(UUID deviceId, UUID parentUserId, long tokenVersion) {
    return buildToken(
        parentUserId,
        "device@" + deviceId,
        Role.ROLE_CHILD_DEVICE,
        TokenType.DEVICE,
        properties.getDeviceTokenExpirationMs(),
        Map.of("deviceId", deviceId.toString(), "tokenVersion", tokenVersion));
  }

  public long getTokenVersion(Claims claims) {
    Object version = claims.get("tokenVersion");
    if (version instanceof Number number) {
      return number.longValue();
    }
    if (version instanceof String text) {
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }
    return 0L;
  }

  private String buildToken(
      UUID subjectId,
      String email,
      Role role,
      TokenType type,
      long expirationMs,
      Map<String, Object> extraClaims) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    var builder =
        Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(subjectId.toString())
            .claim("email", email)
            .claim("role", role.name())
            .claim("type", type.name())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey);
    extraClaims.forEach(builder::claim);
    return builder.compact();
  }

  public Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public UUID getUserId(Claims claims) {
    return UUID.fromString(claims.getSubject());
  }

  public Role getRole(Claims claims) {
    return Role.valueOf(claims.get("role", String.class));
  }

  public UUID getDeviceId(Claims claims) {
    String deviceId = claims.get("deviceId", String.class);
    return deviceId != null ? UUID.fromString(deviceId) : null;
  }

  public TokenType getTokenType(Claims claims) {
    return TokenType.valueOf(claims.get("type", String.class));
  }
}
