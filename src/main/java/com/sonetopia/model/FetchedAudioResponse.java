package com.sonetopia.model;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchedAudioResponse {

  private UUID tempId;
  private String title;
  private String author;
  private String album;
  private Short year;
  private Integer duration;
  private String mimeType;
  private Long fileSize;
}
