package com.sonetopia.model;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultResponse {

  private String title;
  private String url;
  private String uploader;
  private Integer duration;
  private String thumbnail;
  private String source;

  /** True when the user already has an audio imported from this URL. */
  private boolean inLibrary;
  private UUID libraryAudioId;
}
