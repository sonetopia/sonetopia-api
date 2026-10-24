package com.sonetopia.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppleTokenVerifier {

  private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";
  private static final String ISSUER = "https://appleid.apple.com";

  private final boolean enabled;
  private final JwtDecoder decoder;

  @Autowired
  public AppleTokenVerifier(
      @Value("${app.apple.enabled:false}") boolean enabled,
      @Value("${app.apple.client-id:}") String clientId
  ) {
    this(enabled, enabled ? buildDecoder(clientId) : null);
  }

  AppleTokenVerifier(boolean enabled, JwtDecoder decoder) {
    this.enabled = enabled;
    this.decoder = decoder;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public AppleIdentity verify(String idToken) {
    if (!enabled) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Apple sign-in is not enabled"
      );
    }

    Jwt token;
    try {
      token = decoder.decode(idToken);
    } catch (JwtException exception) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Apple token", exception);
    }

    String subject = token.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Apple token");
    }

    return new AppleIdentity(
      subject,
      token.getClaimAsString("email"),
      isTrue(token.getClaim("email_verified"))
    );
  }

  private static boolean isTrue(Object value) {
    return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
  }

  private static JwtDecoder buildDecoder(String clientId) {
    Assert.hasText(clientId, "APPLE_CLIENT_ID must be set when APPLE_ENABLED=true");

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
      JwtValidators.createDefault(),
      issuerValidator(),
      audienceValidator(clientId)
    ));
    return decoder;
  }

  private static OAuth2TokenValidator<Jwt> issuerValidator() {
    return token -> ISSUER.equals(token.getClaimAsString("iss"))
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Invalid Apple token issuer", null)
        );
  }

  private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
    return token -> token.getAudience().contains(clientId)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Invalid Apple token audience", null)
        );
  }

  public record AppleIdentity(String subject, String email, boolean emailVerified) {}
}
