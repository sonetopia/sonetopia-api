package com.sonetopia.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

  /** Source the results came from; pass it back as {@code source} when loading more. */
  private String source;
  private int offset;
  private int limit;
  private boolean hasMore;
  private List<SearchResultResponse> results;
}
