package com.sonetopia.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.User;
import com.sonetopia.model.AuthResponse;
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

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    return buildAuthResponse(user);
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
