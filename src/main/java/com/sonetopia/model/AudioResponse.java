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
public class AudioResponse {

  private UUID id;

  private String title;

  private String type;

  private String author;

  private String album;

  private Short year;

  private Integer coverVersion;

  private String mimeType;

  private Long fileSize;

  private Integer duration;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  private String signedUrl;
}
