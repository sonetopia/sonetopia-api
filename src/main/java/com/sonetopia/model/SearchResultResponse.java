package com.sonetopia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultResponse {

  private String title;
  private String url;
  private String uploader;
  private Integer duration;
  private String thumbnail;
  private String source;
}
