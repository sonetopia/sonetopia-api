package com.sonetopia.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.User;
import com.sonetopia.model.UpdateUserRequest;
import com.sonetopia.model.UserResponse;
import com.sonetopia.repository.UserRepository;

@Service
public class UserService {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private ValidationService validationService;

  public UserResponse toUserResponse(User user) {
    return UserResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .name(user.getName())
        .email(user.getEmail())
        .emailVerifiedAt(user.getEmailVerifiedAt())
        .storageUsed(user.getStorageUsed())
        .isAdmin(user.getIsAdmin())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  @Transactional(readOnly = true)
  public UserResponse get(User user) {
    User currentUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    return toUserResponse(currentUser);
  }

  @Transactional
  public UserResponse update(User user, UpdateUserRequest request) {
    validationService.validate(request);

    User currentUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    if (!currentUser.getUsername().equals(request.getUsername())
        && userRepository.findByUsername(request.getUsername()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }

    boolean changingPassword = request.getPassword() != null && !request.getPassword().isBlank();

    if (changingPassword) {
      String currentPassword = request.getCurrentPassword();

      if (currentPassword == null || currentPassword.isBlank()) {
        throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Current password is required to change password"
        );
      }

      if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
      }
    }

    currentUser.setUsername(request.getUsername());
    currentUser.setName(request.getName());
    currentUser.setUpdatedAt(LocalDateTime.now());

    if (changingPassword) {
      currentUser.setPassword(passwordEncoder.encode(request.getPassword()));
    }

    userRepository.save(currentUser);

    return toUserResponse(currentUser);
  }
}
