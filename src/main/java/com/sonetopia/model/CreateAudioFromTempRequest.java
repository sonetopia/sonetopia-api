package com.sonetopia.model;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAudioFromTempRequest {

  @NotNull(message = "Temporary audio ID is required")
  private UUID tempId;

  @NotBlank(message = "Title is required")
  @Size(max = 255, message = "Title must be less than or equal to 255 characters")
  private String title;

  private String type;
  private String author;
  private String album;
  private Short year;
}
