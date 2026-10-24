package com.sonetopia.model;

import jakarta.validation.constraints.Email;
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
public class ResendVerificationRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Email is not valid")
  @Size(max = 255, message = "Email must be less than or equal to 255 characters")
  private String email;
}
