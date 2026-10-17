package com.sonetopia.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {

  @NotBlank(message = "Username is required")
  @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
  @Pattern(
    regexp = "^[a-z0-9]+(?:[._][a-z0-9]+)*$",
    message = "Username may only contain lowercase letters, numbers, dots, and underscores; separators cannot be consecutive or at the beginning or end"
  )
  private String username;

  @NotBlank(message = "Email is required")
  @Email(message = "Email is not valid")
  @Size(max = 255, message = "Email must be less than or equal to 255 characters")
  private String email;

  @NotBlank(message = "Password is required")
  @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
  private String password;
}
