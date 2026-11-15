package com.sonetopia.model;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchAudioRequestTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void withThumbnailIsOptionalAndDefaultsToFalse() throws Exception {
    FetchAudioRequest request = mapper.readValue("{\"url\":\"https://example.com/video\"}", FetchAudioRequest.class);

    assertEquals("https://example.com/video", request.getUrl());
    assertFalse(request.isWithThumbnail());
  }

  @Test
  void explicitNullWithThumbnailIsTreatedAsFalse() throws Exception {
    FetchAudioRequest request = mapper.readValue(
      "{\"url\":\"https://example.com/video\",\"with_thumbnail\":null}",
      FetchAudioRequest.class
    );

    assertFalse(request.isWithThumbnail());
  }

  @Test
  void acceptsAllWithThumbnailSpellings() throws Exception {
    for (String key : new String[] {"with_thumbnail", "fetch_thumbnail", "withThumbnail"}) {
      FetchAudioRequest request = mapper.readValue(
        "{\"url\":\"https://example.com/video\",\"" + key + "\":true}",
        FetchAudioRequest.class
      );
      assertTrue(request.isWithThumbnail(), key);
    }
  }
}
