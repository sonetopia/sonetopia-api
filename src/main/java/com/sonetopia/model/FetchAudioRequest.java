package com.sonetopia.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FetchAudioRequest {

  @NotBlank(message = "URL is required")
  @Size(max = 2048, message = "URL must be at most 2048 characters")
  private String url;
}
