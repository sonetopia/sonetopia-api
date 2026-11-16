package com.sonetopia.service;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.model.SearchResultResponse;
import com.sonetopia.model.SearchSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
  void searchFailsWhenExecutableMissing() {
    YtDlpService missing = new YtDlpService("yt-dlp-does-not-exist", 5);
    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> missing.search(SearchSource.YOUTUBE, "coldplay", 5)
    );
    assertEquals(500, exception.getStatusCode().value());
  }

  @Test
  void parseSearchResultsKeepsOnlyPlayableTracks() {
    String output = String.join("\n",
      "{\"title\": \"Yellow\", \"id\": \"9qnqYL0eNNI\", \"url\": \"https://music.youtube.com/watch?v=9qnqYL0eNNI\", \"webpage_url\": \"https://music.youtube.com/watch?v=9qnqYL0eNNI\"}",
      "{\"title\": \"Coldplay\", \"url\": \"https://music.youtube.com/browse/UCIaFw5VBEK8qaW6nRpx_qnw\"}",
      "not json",
      "{\"title\": \"Coldplay - Yellow (Official Video)\", \"duration\": 273, \"channel\": \"Coldplay\", \"url\": \"https://www.youtube.com/watch?v=yKNxeF4KMsY\", \"thumbnails\": [{\"url\": \"https://i.ytimg.com/small.jpg\"}, {\"url\": \"https://i.ytimg.com/large.jpg\"}]}",
      "{\"title\": \"Yellow\", \"uploader\": \"coldplayofficial\", \"duration\": 30.0, \"webpage_url\": \"https://soundcloud.com/coldplay/yellow\"}"
    );

    List<SearchResultResponse> results = YtDlpService.parseSearchResults(output, "youtube_music", 10);

    assertEquals(3, results.size());
    assertEquals("https://music.youtube.com/watch?v=9qnqYL0eNNI", results.get(0).getUrl());
    assertNull(results.get(0).getDuration());
    assertEquals("https://i.ytimg.com/vi/9qnqYL0eNNI/hqdefault.jpg", results.get(0).getThumbnail());
    assertEquals("youtube_music", results.get(0).getSource());
    assertEquals(273, results.get(1).getDuration());
    assertEquals("Coldplay", results.get(1).getUploader());
    assertEquals("https://i.ytimg.com/large.jpg", results.get(1).getThumbnail());
    assertEquals("https://soundcloud.com/coldplay/yellow", results.get(2).getUrl());
    assertEquals(30, results.get(2).getDuration());
  }

  @Test
  void parseSearchResultsRespectsLimit() {
    String line = "{\"title\": \"T\", \"url\": \"https://www.youtube.com/watch?v=%s\"}";
    String output = String.join("\n", line.formatted("a"), line.formatted("b"), line.formatted("c"));

    assertEquals(2, YtDlpService.parseSearchResults(output, "youtube", 2).size());
  }

  @Test
  void missingFileExplainsFilesizeLimit() {
    YtDlpService service = new YtDlpService("yt-dlp", 1, "", 250, 0);

    ResponseStatusException exception = service.missingFileException(
      "[download] File is larger than max-filesize (300000000 bytes > 262144000 bytes). Aborting."
    );

    assertEquals(422, exception.getStatusCode().value());
    assertEquals("Audio file must not exceed 250 MiB", exception.getReason());
  }

  @Test
  void missingFileExplainsDurationLimit() {
    YtDlpService service = new YtDlpService("yt-dlp", 1, "", 100, 3600);

    ResponseStatusException exception = service.missingFileException(
      "[download] Coldplay - Yellow (Official Video) does not pass filter (duration<=3600), skipping .."
    );

    assertEquals(422, exception.getStatusCode().value());
    assertEquals("Audio duration must not exceed 60 minutes", exception.getReason());
  }

  @Test
  void missingFileIgnoresFilterMessageWhenNoDurationLimit() {
    ResponseStatusException exception = ytDlpService.missingFileException("x does not pass filter (y), skipping ..");

    assertEquals("Unable to fetch from the provided URL", exception.getReason());
  }

  @Test
  void missingFileFallsBackToGenericMessage() {
    ResponseStatusException exception = ytDlpService.missingFileException("something else happened");

    assertEquals("Unable to fetch from the provided URL", exception.getReason());
  }

  @Test
  void rejectsPrivateHosts() {
    assertThrows(
      ResponseStatusException.class,
      () -> ytDlpService.download("http://127.0.0.1/audio.mp3", Path.of("target/test-storage"), false)
    );
  }
}
