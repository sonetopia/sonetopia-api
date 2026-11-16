package com.sonetopia.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.User;
import com.sonetopia.model.SearchResponse;
import com.sonetopia.model.SearchResultResponse;
import com.sonetopia.model.SearchSource;
import com.sonetopia.repository.AudioRepository;

/**
 * Search with fallback across sources and "load more" paging.
 *
 * <p>yt-dlp returns results in a different order on every call, so a page can not be fetched on its
 * own: that would repeat or skip tracks. Instead a window of {@value #WINDOW_SIZE} results is fetched
 * once per source and query, kept briefly in memory, and pages are slices of that window.
 */
@Service
public class SearchService {

  static final int WINDOW_SIZE = 50;
  static final int MAX_PAGE_SIZE = 20;
  static final int MAX_QUERY_LENGTH = 200;
  static final Duration CACHE_TTL = Duration.ofMinutes(5);
  static final int MAX_CACHE_ENTRIES = 100;

  private final YtDlpService ytDlpService;
  private final AudioRepository audioRepository;
  private final Clock clock;
  private final Map<String, CachedWindow> cache = new ConcurrentHashMap<>();

  @Autowired
  public SearchService(YtDlpService ytDlpService, AudioRepository audioRepository) {
    this(ytDlpService, audioRepository, Clock.systemUTC());
  }

  SearchService(YtDlpService ytDlpService, AudioRepository audioRepository, Clock clock) {
    this.ytDlpService = ytDlpService;
    this.audioRepository = audioRepository;
    this.clock = clock;
  }

  /**
   * @param sourceLabel restrict to one source (required to keep paging on the same source); when
   *     empty, sources are tried in priority order and the first one with results wins
   */
  public SearchResponse search(User user, String query, String sourceLabel, int limit, int offset) {
    String normalizedQuery = query == null ? "" : query.trim();
    if (normalizedQuery.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query is required");
    }
    if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Search query must be at most " + MAX_QUERY_LENGTH + " characters"
      );
    }
    if (offset < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offset must not be negative");
    }
    int pageSize = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));

    List<SearchSource> sources = sourcesToTry(sourceLabel);

    boolean anySourceResponded = false;
    for (SearchSource source : sources) {
      List<SearchResultResponse> window;
      try {
        window = window(source, normalizedQuery);
      } catch (ResponseStatusException exception) {
        if (exception.getStatusCode().value() == HttpStatus.BAD_GATEWAY.value()) {
          continue;
        }
        throw exception;
      }

      anySourceResponded = true;
      if (window.isEmpty()) {
        continue;
      }

      int from = Math.min(offset, window.size());
      int to = Math.min(offset + pageSize, window.size());
      return SearchResponse.builder()
        .source(source.label())
        .offset(offset)
        .limit(pageSize)
        .hasMore(to < window.size())
        .results(markLibrary(user, window.subList(from, to)))
        .build();
    }

    if (!anySourceResponded) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Search is currently unavailable");
    }
    return SearchResponse.builder()
      .offset(offset)
      .limit(pageSize)
      .hasMore(false)
      .results(List.of())
      .build();
  }

  private List<SearchSource> sourcesToTry(String sourceLabel) {
    if (sourceLabel == null || sourceLabel.isBlank()) {
      return List.of(SearchSource.values());
    }
    SearchSource source = SearchSource.fromLabel(sourceLabel)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown search source"));
    return List.of(source);
  }

  private List<SearchResultResponse> window(SearchSource source, String query) {
    String key = source.label() + "\n" + query.toLowerCase(Locale.ROOT);
    Instant now = clock.instant();

    CachedWindow cached = cache.get(key);
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.results();
    }

    List<SearchResultResponse> results = List.copyOf(ytDlpService.search(source, query, WINDOW_SIZE));
    if (cache.size() >= MAX_CACHE_ENTRIES) {
      cache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
      if (cache.size() >= MAX_CACHE_ENTRIES) {
        cache.clear();
      }
    }
    cache.put(key, new CachedWindow(results, now.plus(CACHE_TTL)));
    return results;
  }

  private List<SearchResultResponse> markLibrary(User user, List<SearchResultResponse> page) {
    Map<String, String> keyByUrl = new HashMap<>();
    for (SearchResultResponse result : page) {
      String key = SourceUrls.canonicalize(result.getUrl());
      if (key != null) {
        keyByUrl.put(result.getUrl(), key);
      }
    }

    Map<String, Audio> audioByKey = new HashMap<>();
    if (user != null && !keyByUrl.isEmpty()) {
      for (Audio audio : audioRepository.findByUserAndSourceUrlIn(user, new ArrayList<>(keyByUrl.values()))) {
        audioByKey.putIfAbsent(audio.getSourceUrl(), audio);
      }
    }

    // Results are copied: the cached window is shared between users and must stay untouched.
    List<SearchResultResponse> marked = new ArrayList<>(page.size());
    for (SearchResultResponse result : page) {
      Audio match = audioByKey.get(keyByUrl.get(result.getUrl()));
      marked.add(result.toBuilder()
        .inLibrary(match != null)
        .libraryAudioId(match == null ? null : match.getId())
        .build());
    }
    return marked;
  }

  private record CachedWindow(List<SearchResultResponse> results, Instant expiresAt) {}
}
