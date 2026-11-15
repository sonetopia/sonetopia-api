package com.sonetopia.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FetchAudioRequest {

  @NotBlank(message = "URL is required")
  @Size(max = 2048, message = "URL must be at most 2048 characters")
  private String url;

  // Wrapper type on purpose: Jackson 3 rejects a missing primitive boolean (FAIL_ON_NULL_FOR_PRIMITIVES),
  // which made requests without this optional field fail with 400.
  @JsonProperty("with_thumbnail")
  @JsonAlias({"fetch_thumbnail", "withThumbnail"})
  @Getter(AccessLevel.NONE)
  private Boolean withThumbnail;

  public boolean isWithThumbnail() {
    return Boolean.TRUE.equals(withThumbnail);
  }
}
