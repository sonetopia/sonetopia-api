package com.sonetopia.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.User;
import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.security.JwtService;

@Service
public class AuthService {

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
    user.setEmail(request.getEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setStorageUsed(0L);
    user.setIsAdmin(false);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);

    userRepository.save(user);

    return buildAuthResponse(user);
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
