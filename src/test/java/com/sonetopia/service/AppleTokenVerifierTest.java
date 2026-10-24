package com.sonetopia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ResponseStatusException;

class AppleTokenVerifierTest {

  @Test
  void acceptsBooleanAndStringEmailVerifiedClaims() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    AppleTokenVerifier verifier = new AppleTokenVerifier(true, decoder);

    when(decoder.decode("boolean-token")).thenReturn(appleToken(true));
    when(decoder.decode("string-token")).thenReturn(appleToken("true"));
    when(decoder.decode("unverified-token")).thenReturn(appleToken("false"));

    AppleTokenVerifier.AppleIdentity booleanIdentity = verifier.verify("boolean-token");
    assertEquals("apple-sub", booleanIdentity.subject());
    assertEquals("apple@example.com", booleanIdentity.email());
    assertTrue(booleanIdentity.emailVerified());

    assertTrue(verifier.verify("string-token").emailVerified());
    assertFalse(verifier.verify("unverified-token").emailVerified());
  }

  @Test
  void rejectsInvalidToken() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    AppleTokenVerifier verifier = new AppleTokenVerifier(true, decoder);
    when(decoder.decode("bad-token")).thenThrow(new JwtException("invalid"));

    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> verifier.verify("bad-token")
    );

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }

  @Test
  void rejectsLoginWhenAppleIsDisabled() {
    AppleTokenVerifier verifier = new AppleTokenVerifier(false, (JwtDecoder) null);

    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> verifier.verify("token")
    );

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
  }

  private static Jwt appleToken(Object emailVerified) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("apple-sub")
        .claim("email", "apple@example.com")
        .claim("email_verified", emailVerified)
        .build();
  }
}
