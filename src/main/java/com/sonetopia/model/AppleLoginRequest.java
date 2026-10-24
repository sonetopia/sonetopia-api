package com.sonetopia.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppleLoginRequest {

  @NotBlank(message = "Apple ID token is required")
  private String idToken;

  @Size(max = 100, message = "Name must be less than or equal to 100 characters")
  private String name;
}
