package com.sonetopia.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.RefreshToken;
import com.sonetopia.entity.User;
import com.sonetopia.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;

  @Value("${app.auth.refresh-token-expiration:2592000000}")
  private long expiration;

  public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional
  public IssuedToken issue(User user) {
    return issue(user, UUID.randomUUID());
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public RotatedToken rotate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw invalidToken();
    }

    RefreshToken storedToken = refreshTokenRepository
        .findByTokenHashForUpdate(hash(rawToken))
        .orElseThrow(this::invalidToken);

    LocalDateTime now = LocalDateTime.now();

    if (storedToken.getRevokedAt() != null) {
      refreshTokenRepository.revokeAllByFamilyId(storedToken.getFamilyId(), now);
      throw invalidToken();
    }

    if (!storedToken.getExpiresAt().isAfter(now)) {
      storedToken.setRevokedAt(now);
      throw invalidToken();
    }

    storedToken.setRevokedAt(now);

    User user = storedToken.getUser();
    return new RotatedToken(user, issue(user, storedToken.getFamilyId()));
  }

  @Transactional
  public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }

    refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
      if (token.getRevokedAt() == null) {
        token.setRevokedAt(LocalDateTime.now());
      }
    });
  }

  @Transactional
  public void revokeAll(UUID userId) {
    refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
  }

  private IssuedToken issue(User user, UUID familyId) {
    String rawToken = generateToken();
    LocalDateTime now = LocalDateTime.now();

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setTokenHash(hash(rawToken));
    refreshToken.setFamilyId(familyId);
    refreshToken.setExpiresAt(now.plus(Duration.ofMillis(expiration)));
    refreshToken.setCreatedAt(now);
    refreshTokenRepository.save(refreshToken);

    return new IssuedToken(rawToken, expiration / 1000);
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private ResponseStatusException invalidToken() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
  }

  public record IssuedToken(String token, long expiresInSeconds) {}

  public record RotatedToken(User user, IssuedToken refreshToken) {}
}
