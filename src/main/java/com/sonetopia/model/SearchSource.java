package com.sonetopia.model;

import java.util.Arrays;
import java.util.Optional;

/** Where a search runs. Declaration order is the fallback priority. */
public enum SearchSource {
  YOUTUBE_MUSIC("youtube_music"),
  YOUTUBE("youtube"),
  SOUNDCLOUD("soundcloud");

  private final String label;

  SearchSource(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static Optional<SearchSource> fromLabel(String label) {
    if (label == null) {
      return Optional.empty();
    }
    String normalized = label.trim();
    return Arrays.stream(values()).filter(source -> source.label.equalsIgnoreCase(normalized)).findFirst();
  }
}
