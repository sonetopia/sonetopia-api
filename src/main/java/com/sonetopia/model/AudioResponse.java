package com.sonetopia.model;

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

  private String imageCover;

  private String storageKey;

  private String mimeType;

  private String fileExtension;

  private Long fileSize;

  private Integer duration;

  private String createdAt;

  private String updatedAt;

  private String signedUrl;
}
