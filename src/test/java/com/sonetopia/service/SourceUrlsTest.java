package com.sonetopia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SourceUrlsTest {

  private static final String CANONICAL = "https://www.youtube.com/watch?v=9qnqYL0eNNI";

  @Test
  void youtubeHostVariantsShareOneKey() {
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://music.youtube.com/watch?v=9qnqYL0eNNI"));
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://www.youtube.com/watch?v=9qnqYL0eNNI"));
    assertEquals(CANONICAL, SourceUrls.canonicalize("http://youtube.com/watch?v=9qnqYL0eNNI"));
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://m.youtube.com/watch?v=9qnqYL0eNNI"));
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://youtu.be/9qnqYL0eNNI"));
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://www.youtube.com/shorts/9qnqYL0eNNI"));
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://www.youtube.com/embed/9qnqYL0eNNI"));
  }

  @Test
  void youtubeTrackingParametersAreDropped() {
    assertEquals(
      CANONICAL,
      SourceUrls.canonicalize("https://music.youtube.com/watch?v=9qnqYL0eNNI&list=RDAMVM9qnqYL0eNNI&si=abc")
    );
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://www.youtube.com/watch?feature=share&v=9qnqYL0eNNI"));
    assertEquals(CANONICAL, SourceUrls.canonicalize("https://youtu.be/9qnqYL0eNNI?t=30"));
  }

  @Test
  void youtubeUrlsWithoutAVideoIdStayAsPlainUrls() {
    assertEquals(
      "https://www.youtube.com/channel/UCIaFw5VBEK8qaW6nRpx_qnw",
      SourceUrls.canonicalize("https://www.youtube.com/channel/UCIaFw5VBEK8qaW6nRpx_qnw/")
    );
    assertEquals("https://www.youtube.com/watch", SourceUrls.canonicalize("https://www.youtube.com/watch"));
    assertEquals(
      "https://www.youtube.com/watch?v=short",
      SourceUrls.canonicalize("https://www.youtube.com/watch?v=short")
    );
  }

  @Test
  void otherSitesAreNormalizedLightly() {
    assertEquals(
      "https://soundcloud.com/coldplay/yellow",
      SourceUrls.canonicalize("http://SoundCloud.com/coldplay/yellow/#t=1")
    );
    assertEquals("https://example.com/a?b=1", SourceUrls.canonicalize("https://example.com/a/?b=1"));
    assertEquals("https://example.com:8443/a", SourceUrls.canonicalize("https://example.com:8443/a"));
    assertEquals("https://example.com", SourceUrls.canonicalize("https://example.com/"));
  }

  @Test
  void rejectsUnusableValues() {
    assertNull(SourceUrls.canonicalize(null));
    assertNull(SourceUrls.canonicalize("   "));
    assertNull(SourceUrls.canonicalize("not a url"));
    assertNull(SourceUrls.canonicalize("check this https://example.com out"));
    assertNull(SourceUrls.canonicalize("ftp://example.com/file"));
    assertNull(SourceUrls.canonicalize("file:///tmp/audio.mp3"));
    assertNull(SourceUrls.canonicalize("/relative/path"));
    assertNull(SourceUrls.canonicalize("https://example.com/" + "a".repeat(SourceUrls.MAX_LENGTH)));
  }
}
