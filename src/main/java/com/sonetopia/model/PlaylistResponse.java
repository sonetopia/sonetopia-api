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
public class PlaylistResponse {

  private UUID id;

  private String title;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
