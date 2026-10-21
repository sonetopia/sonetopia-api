package com.sonetopia.service;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class YtDlpServiceTest {

  private final YtDlpService ytDlpService = new YtDlpService("yt-dlp", 1);

  @Test
  void rejectsNonHttpUrls() {
    assertThrows(
      ResponseStatusException.class,
      () -> ytDlpService.download("file:///tmp/audio.mp3", Path.of("target/test-storage"), false)
    );
  }

  @Test
  void rejectsPrivateHosts() {
    assertThrows(
      ResponseStatusException.class,
      () -> ytDlpService.download("http://127.0.0.1/audio.mp3", Path.of("target/test-storage"), false)
    );
  }
}
