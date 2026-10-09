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
public class CreatePlaylistRequest {

  @NotBlank(message = "Title is required")
  @Size(max = 255, message = "Title must be less than or equal to 255 characters")
  private String title;
}
