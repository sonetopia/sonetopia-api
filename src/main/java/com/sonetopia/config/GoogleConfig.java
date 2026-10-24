package com.sonetopia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class GoogleConfig {

  private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

  @Bean
  public JwtDecoder googleJwtDecoder(@Value("${app.google.client-id}") String clientId) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
      JwtValidators.createDefault(),
      issuerValidator(),
      audienceValidator(clientId)
    ));
    return decoder;
  }

  private static OAuth2TokenValidator<Jwt> issuerValidator() {
    return token -> {
      String issuer = token.getClaimAsString("iss");
      if ("accounts.google.com".equals(issuer) || "https://accounts.google.com".equals(issuer)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Invalid Google token issuer", null)
      );
    };
  }

  private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
    return token -> token.getAudience().contains(clientId)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Invalid Google token audience", null)
        );
  }
}
