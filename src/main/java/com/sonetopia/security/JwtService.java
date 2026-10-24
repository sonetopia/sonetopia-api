package com.sonetopia.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sonetopia.entity.User;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String EMAIL_VERIFICATION_PURPOSE = "email_verification";
  private static final String ENCODED_HEADER = base64UrlEncode(
    "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)
  );

  private final ObjectMapper objectMapper;

  @Value("${spring.security.jwt.secret}")
  private String secret;

  @Value("${spring.security.jwt.expiration}")
  private long expiration;

  @Value("${spring.security.email-verification.expiration:86400000}")
  private long emailVerificationExpiration;

  public JwtService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String generateToken(User user) {
    return generateToken(user, null, expiration);
  }

  public String generateEmailVerificationToken(User user) {
    return generateToken(user, EMAIL_VERIFICATION_PURPOSE, emailVerificationExpiration);
  }

  private String generateToken(User user, String purpose, long expiresIn) {
    Instant issuedAt = Instant.now();

    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", user.getId().toString());
    claims.put("username", user.getUsername());
    if (purpose != null) {
      claims.put("purpose", purpose);
    }
    claims.put("iat", issuedAt.getEpochSecond());
    claims.put("exp", issuedAt.plusMillis(expiresIn).getEpochSecond());

    String encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(claims));
    String signingInput = ENCODED_HEADER + "." + encodedPayload;

    return signingInput + "." + sign(signingInput);
  }

  public UUID extractUserId(String token) {
    Map<String, Object> claims = parseAndValidate(token);

    if (claims.get("purpose") != null) {
      throw new IllegalArgumentException("Invalid token");
    }

    return extractSubject(claims);
  }

  public UUID extractEmailVerificationUserId(String token) {
    Map<String, Object> claims = parseAndValidate(token);

    if (!EMAIL_VERIFICATION_PURPOSE.equals(claims.get("purpose"))) {
      throw new IllegalArgumentException("Invalid token purpose");
    }

    return extractSubject(claims);
  }

  private UUID extractSubject(Map<String, Object> claims) {

    Object subject = claims.get("sub");
    if (subject == null) {
      throw new IllegalArgumentException("Invalid token");
    }

    return UUID.fromString(subject.toString());
  }

  public long getExpirationSeconds() {
    return expiration / 1000;
  }

  private Map<String, Object> parseAndValidate(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid token");
    }

    String signingInput = parts[0] + "." + parts[1];
    if (!constantTimeEquals(sign(signingInput), parts[2])) {
      throw new IllegalArgumentException("Invalid token signature");
    }

    Map<String, Object> claims = objectMapper.readValue(
      base64UrlDecode(parts[1]),
      new TypeReference<Map<String, Object>>() {}
    );

    Object expirationClaim = claims.get("exp");
    if (expirationClaim == null) {
      throw new IllegalArgumentException("Invalid token");
    }

    long expiresAt = ((Number) expirationClaim).longValue();
    if (Instant.now().getEpochSecond() > expiresAt) {
      throw new IllegalArgumentException("Token expired");
    }

    return claims;
  }

  private String sign(String signingInput) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

      return base64UrlEncode(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign token", e);
    }
  }

  private boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
      expected.getBytes(StandardCharsets.UTF_8),
      actual.getBytes(StandardCharsets.UTF_8)
    );
  }

  private static String base64UrlEncode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static byte[] base64UrlDecode(String value) {
    return Base64.getUrlDecoder().decode(value);
  }
}
