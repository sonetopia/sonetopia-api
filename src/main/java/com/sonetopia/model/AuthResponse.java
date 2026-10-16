package com.sonetopia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {

  private String token;

  @Builder.Default
  private String tokenType = "Bearer";

  private Long expiresIn;

  private UserResponse user;
}
