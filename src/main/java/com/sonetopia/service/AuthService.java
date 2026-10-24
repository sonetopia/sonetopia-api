package com.sonetopia.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.User;
import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.GoogleLoginRequest;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.model.ResendVerificationRequest;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.security.JwtService;

@Service
public class AuthService {

  private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private ValidationService validationService;

  @Autowired
  private UserService userService;

  @Autowired
  private EmailService emailService;

  @Autowired
  @Qualifier("googleJwtDecoder")
  private JwtDecoder googleJwtDecoder;

  @Value("${app.email.verification-resend-cooldown-seconds:60}")
  private long verificationResendCooldownSeconds;

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    validationService.validate(request);

    if (userRepository.findByUsername(request.getUsername()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }

    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }

    LocalDateTime now = LocalDateTime.now();

    User user = new User();
    user.setUsername(request.getUsername());
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setStorageUsed(0L);
    user.setIsAdmin(false);
    user.setEmailVerificationSentAt(now);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);

    userRepository.save(user);

    try {
      emailService.sendVerificationEmail(user, jwtService.generateEmailVerificationToken(user));
    } catch (MailException exception) {
      logger.error("Failed to send verification email to {}", user.getEmail(), exception);
      throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Failed to send verification email",
        exception
      );
    }

    return buildAuthResponse(user);
  }

  @Transactional
  public void verifyEmail(String token) {
    UUID userId;
    try {
      userId = jwtService.extractEmailVerificationUserId(token);
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid or expired verification token",
        exception
      );
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid or expired verification token"
        ));

    if (user.getEmailVerifiedAt() == null) {
      LocalDateTime now = LocalDateTime.now();
      user.setEmailVerifiedAt(now);
      user.setUpdatedAt(now);
      userRepository.save(user);
    }
  }

  public void resendVerificationEmail(ResendVerificationRequest request) {
    validationService.validate(request);

    User user = userRepository.findByEmail(request.getEmail()).orElse(null);
    if (user == null || user.getEmailVerifiedAt() != null) {
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime sentAt = user.getEmailVerificationSentAt();
    if (sentAt != null) {
      long elapsedSeconds = Duration.between(sentAt, now).getSeconds();
      if (elapsedSeconds < verificationResendCooldownSeconds) {
        long waitSeconds = verificationResendCooldownSeconds - Math.max(elapsedSeconds, 0);
        throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Please wait " + waitSeconds + " seconds before requesting another verification email"
        );
      }
    }

    try {
      emailService.sendVerificationEmail(user, jwtService.generateEmailVerificationToken(user));
    } catch (MailException exception) {
      logger.error("Failed to resend verification email to {}", user.getEmail(), exception);
      throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Failed to send verification email",
        exception
      );
    }

    user.setEmailVerificationSentAt(now);
    user.setUpdatedAt(now);
    userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    validationService.validate(request);

    User user = userRepository.findByUsername(request.getUsername())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

    if (user.getPassword() == null
        || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    return buildAuthResponse(user);
  }

  public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
    validationService.validate(request);

    Jwt googleToken;
    try {
      googleToken = googleJwtDecoder.decode(request.getIdToken());
    } catch (JwtException exception) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token", exception);
    }

    String googleId = googleToken.getSubject();
    String email = googleToken.getClaimAsString("email");
    Boolean emailVerified = googleToken.getClaim("email_verified");

    if (googleId == null || googleId.isBlank()
        || email == null || email.isBlank()
        || !Boolean.TRUE.equals(emailVerified)) {
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Google account email is not verified"
      );
    }

    LocalDateTime now = LocalDateTime.now();
    User user = userRepository.findByGoogleId(googleId).orElse(null);

    if (user == null) {
      user = userRepository.findByEmail(email).orElse(null);

      if (user == null) {
        user = new User();
        user.setUsername(generateUsername(email));
        user.setName(resolveName(googleToken, email));
        user.setEmail(email);
        user.setStorageUsed(0L);
        user.setIsAdmin(false);
        user.setCreatedAt(now);
      } else if (user.getGoogleId() != null) {
        throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Email is already linked to another Google account"
        );
      }

      user.setGoogleId(googleId);
      if (user.getEmailVerifiedAt() == null) {
        user.setEmailVerifiedAt(now);
      }
      user.setUpdatedAt(now);
      userRepository.save(user);
    } else if (user.getEmailVerifiedAt() == null) {
      user.setEmailVerifiedAt(now);
      user.setUpdatedAt(now);
      userRepository.save(user);
    }

    return buildAuthResponse(user);
  }

  private String generateUsername(String email) {
    int at = email.indexOf('@');
    String localPart = at > 0 ? email.substring(0, at) : email;
    String base = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");

    if (base.length() < 3) {
      base = "user" + base;
    }
    base = base.substring(0, Math.min(base.length(), 40));

    String candidate = base;
    int suffix = 1;
    while (userRepository.findByUsername(candidate).isPresent()) {
      String suffixText = String.valueOf(suffix++);
      candidate = base.substring(0, Math.min(base.length(), 50 - suffixText.length())) + suffixText;
    }
    return candidate;
  }

  private String resolveName(Jwt googleToken, String email) {
    String name = googleToken.getClaimAsString("name");
    if (name == null || name.isBlank()) {
      int at = email.indexOf('@');
      name = at > 0 ? email.substring(0, at) : email;
    }
    return name.length() > 100 ? name.substring(0, 100) : name;
  }

  private AuthResponse buildAuthResponse(User user) {
    return AuthResponse.builder()
        .token(jwtService.generateToken(user))
        .tokenType("Bearer")
        .expiresIn(jwtService.getExpirationSeconds())
        .user(userService.toUserResponse(user))
        .build();
  }
}
