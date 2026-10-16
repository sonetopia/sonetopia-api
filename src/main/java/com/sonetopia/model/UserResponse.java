package com.sonetopia.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponse {

  private UUID id;

  private String username;

  private String email;

  private Long storageUsed;

  private Boolean isAdmin;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
