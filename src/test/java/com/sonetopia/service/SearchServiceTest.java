package com.sonetopia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.User;
import com.sonetopia.model.SearchResponse;
import com.sonetopia.model.SearchResultResponse;
import com.sonetopia.model.SearchSource;
import com.sonetopia.repository.AudioRepository;

class SearchServiceTest {

  private YtDlpService ytDlpService;
  private AudioRepository audioRepository;
  private MutableClock clock;
  private SearchService service;
  private User user;

  @BeforeEach
  void setUp() {
    ytDlpService = mock(YtDlpService.class);
    audioRepository = mock(AudioRepository.class);
    clock = new MutableClock();
    service = new SearchService(ytDlpService, audioRepository, clock);
    user = new User();
    user.setId(UUID.randomUUID());
  }

  @Test
  void rejectsInvalidInput() {
    assertEquals(400, status(() -> service.search(user, "   ", null, 10, 0)));
    assertEquals(400, status(() -> service.search(user, null, null, 10, 0)));
    assertEquals(400, status(() -> service.search(user, "a".repeat(201), null, 10, 0)));
    assertEquals(400, status(() -> service.search(user, "coldplay", null, 10, -1)));
    assertEquals(400, status(() -> service.search(user, "coldplay", "spotify", 10, 0)));
  }

  @Test
  void pagesThroughOneWindowWithoutRefetching() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), eq("coldplay"), anyInt()))
      .thenReturn(tracks("youtube_music", 25));

    SearchResponse first = service.search(user, "coldplay", null, 10, 0);
    SearchResponse second = service.search(user, "coldplay", "youtube_music", 10, 10);
    SearchResponse third = service.search(user, "coldplay", "youtube_music", 10, 20);

    assertEquals("youtube_music", first.getSource());
    assertEquals(10, first.getResults().size());
    assertEquals("Track 0", first.getResults().get(0).getTitle());
    assertTrue(first.isHasMore());

    assertEquals("Track 10", second.getResults().get(0).getTitle());
    assertEquals(10, second.getResults().size());
    assertTrue(second.isHasMore());

    assertEquals(5, third.getResults().size());
    assertEquals("Track 24", third.getResults().get(4).getTitle());
    assertFalse(third.isHasMore());

    verify(ytDlpService, times(1)).search(any(), anyString(), eq(SearchService.WINDOW_SIZE));
  }

  @Test
  void hasMoreIsFalseWhenPageEndsExactlyAtTheEnd() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt()))
      .thenReturn(tracks("youtube_music", 20));

    assertFalse(service.search(user, "coldplay", null, 10, 10).isHasMore());
  }

  @Test
  void offsetPastTheWindowReturnsAnEmptyPage() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt()))
      .thenReturn(tracks("youtube_music", 5));

    SearchResponse response = service.search(user, "coldplay", "youtube_music", 10, 100);

    assertEquals("youtube_music", response.getSource());
    assertTrue(response.getResults().isEmpty());
    assertFalse(response.isHasMore());
  }

  @Test
  void clampsPageSize() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt()))
      .thenReturn(tracks("youtube_music", 50));

    assertEquals(20, service.search(user, "coldplay", null, 500, 0).getResults().size());
    assertEquals(1, service.search(user, "coldplay", null, 0, 0).getResults().size());
  }

  @Test
  void refetchesAfterTheCacheExpires() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt()))
      .thenReturn(tracks("youtube_music", 5));

    service.search(user, "coldplay", null, 10, 0);
    clock.advance(SearchService.CACHE_TTL.minusSeconds(1));
    service.search(user, "coldplay", null, 10, 0);
    verify(ytDlpService, times(1)).search(any(), anyString(), anyInt());

    clock.advance(Duration.ofSeconds(2));
    service.search(user, "coldplay", null, 10, 0);
    verify(ytDlpService, times(2)).search(any(), anyString(), anyInt());
  }

  @Test
  void cacheIgnoresQueryCaseAndSurroundingSpaces() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt()))
      .thenReturn(tracks("youtube_music", 5));

    service.search(user, "Coldplay", null, 10, 0);
    service.search(user, "  coldplay ", null, 10, 0);

    verify(ytDlpService, times(1)).search(any(), anyString(), anyInt());
  }

  @Test
  void fallsBackWhenASourceFailsOrIsEmpty() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt()))
      .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "down"));
    when(ytDlpService.search(eq(SearchSource.YOUTUBE), anyString(), anyInt()))
      .thenReturn(List.of());
    when(ytDlpService.search(eq(SearchSource.SOUNDCLOUD), anyString(), anyInt()))
      .thenReturn(tracks("soundcloud", 3));

    SearchResponse response = service.search(user, "obscure", null, 10, 0);

    assertEquals("soundcloud", response.getSource());
    assertEquals(3, response.getResults().size());
  }

  @Test
  void anExplicitSourceDoesNotFallBack() {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE), anyString(), anyInt()))
      .thenReturn(List.of());

    SearchResponse response = service.search(user, "obscure", "youtube", 10, 0);

    assertTrue(response.getResults().isEmpty());
    assertNull(response.getSource());
    verify(ytDlpService, never()).search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt());
    verify(ytDlpService, never()).search(eq(SearchSource.SOUNDCLOUD), anyString(), anyInt());
  }

  @Test
  void failsWhenEverySourceFails() {
    when(ytDlpService.search(any(), anyString(), anyInt()))
      .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "down"));

    assertEquals(502, status(() -> service.search(user, "coldplay", null, 10, 0)));
  }

  @Test
  void doesNotMaskUnexpectedErrors() {
    when(ytDlpService.search(any(), anyString(), anyInt()))
      .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "yt-dlp is not installed"));

    assertEquals(500, status(() -> service.search(user, "coldplay", null, 10, 0)));
    verify(ytDlpService, times(1)).search(any(), anyString(), anyInt());
  }

  @Test
  void flagsResultsAlreadyInTheLibrary() {
    UUID audioId = UUID.randomUUID();
    Audio imported = new Audio();
    imported.setId(audioId);
    imported.setSourceUrl("https://www.youtube.com/watch?v=track00000a");

    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt())).thenReturn(List.of(
      result("Imported", "https://music.youtube.com/watch?v=track00000a"),
      result("New", "https://music.youtube.com/watch?v=track00000b")
    ));
    when(audioRepository.findByUserAndSourceUrlIn(eq(user), any())).thenReturn(List.of(imported));

    List<SearchResultResponse> results = service.search(user, "coldplay", null, 10, 0).getResults();

    assertTrue(results.get(0).isInLibrary());
    assertEquals(audioId, results.get(0).getLibraryAudioId());
    assertFalse(results.get(1).isInLibrary());
    assertNull(results.get(1).getLibraryAudioId());
  }

  @Test
  void libraryFlagsAreNotLeakedThroughTheSharedCache() {
    Audio imported = new Audio();
    imported.setId(UUID.randomUUID());
    imported.setSourceUrl("https://www.youtube.com/watch?v=track00000a");
    User otherUser = new User();
    otherUser.setId(UUID.randomUUID());

    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt()))
      .thenReturn(List.of(result("Imported", "https://music.youtube.com/watch?v=track00000a")));
    when(audioRepository.findByUserAndSourceUrlIn(eq(user), any())).thenReturn(List.of(imported));
    when(audioRepository.findByUserAndSourceUrlIn(eq(otherUser), any())).thenReturn(List.of());

    assertTrue(service.search(user, "coldplay", null, 10, 0).getResults().get(0).isInLibrary());
    assertFalse(service.search(otherUser, "coldplay", null, 10, 0).getResults().get(0).isInLibrary());
  }

  private static int status(Runnable call) {
    return assertThrows(ResponseStatusException.class, call::run).getStatusCode().value();
  }

  private static List<SearchResultResponse> tracks(String source, int count) {
    List<SearchResultResponse> results = new ArrayList<>();
    IntStream.range(0, count).forEach(index -> results.add(SearchResultResponse.builder()
      .title("Track " + index)
      .url("https://example.com/track/" + index)
      .source(source)
      .build()));
    return results;
  }

  private static SearchResultResponse result(String title, String url) {
    return SearchResultResponse.builder().title(title).url(url).source("youtube_music").build();
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-16T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
