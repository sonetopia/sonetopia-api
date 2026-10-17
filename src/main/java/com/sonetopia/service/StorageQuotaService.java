package com.sonetopia.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.User;
import com.sonetopia.model.StorageUsageResponse;
import com.sonetopia.repository.UserRepository;

@Service
public class StorageQuotaService {

  private final UserRepository userRepository;
  private final long userQuotaBytes;

  public StorageQuotaService(
      UserRepository userRepository,
      @Value("${spring.storage.user-quota-bytes}") long userQuotaBytes) {
    this.userRepository = userRepository;
    this.userQuotaBytes = userQuotaBytes;
  }

  @Transactional
  public void addUsage(User user, long bytes) {
    if (bytes < 0) {
      throw new IllegalArgumentException("Storage usage increment must not be negative");
    }

    User currentUser = getLockedUser(user.getId());
    long currentUsage = currentUser.getStorageUsed();
    long newUsage;
    try {
      newUsage = Math.addExact(currentUsage, bytes);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Storage quota exceeded");
    }

    if (!Boolean.TRUE.equals(currentUser.getIsAdmin()) && newUsage > userQuotaBytes) {
      throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Storage quota exceeded");
    }

    currentUser.setStorageUsed(newUsage);
  }

  @Transactional
  public void removeUsage(User user, long bytes) {
    if (bytes < 0) {
      throw new IllegalArgumentException("Storage usage decrement must not be negative");
    }

    User currentUser = getLockedUser(user.getId());
    currentUser.setStorageUsed(Math.max(0L, currentUser.getStorageUsed() - bytes));
  }

  @Transactional(readOnly = true)
  public StorageUsageResponse getUsage(User user) {
    User currentUser = userRepository.findById(user.getId()).orElseThrow();
    boolean unlimited = Boolean.TRUE.equals(currentUser.getIsAdmin());
    long usedBytes = currentUser.getStorageUsed();

    return StorageUsageResponse.builder()
        .usedBytes(usedBytes)
        .maxBytes(unlimited ? null : userQuotaBytes)
        .remainingBytes(unlimited ? null : Math.max(0L, userQuotaBytes - usedBytes))
        .unlimited(unlimited)
        .build();
  }

  private User getLockedUser(UUID userId) {
    return userRepository.findByIdForUpdate(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
  }
}