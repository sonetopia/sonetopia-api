package com.sonetopia.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GithubLoginRequest {

  @NotBlank(message = "GitHub authorization code is required")
  private String code;
}
