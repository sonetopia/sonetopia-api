package com.sonetopia.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.model.SearchResultResponse;
import com.sonetopia.model.SearchSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class YtDlpService {

  private static final Logger logger = LoggerFactory.getLogger(YtDlpService.class);
  private static final long SEARCH_TIMEOUT_SECONDS = 30;
  // YouTube Music "Songs" filter: drops artist/album/playlist entries that are not fetchable tracks.
  private static final String MUSIC_SONGS_FILTER = "EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String executable;
  private final long timeoutSeconds;
  private final String cookiesPath;
  private final long maxFilesizeMib;
  private final long maxDurationSeconds;

  @Autowired
  public YtDlpService(
      @Value("${spring.yt-dlp.path:yt-dlp}") String executable,
      @Value("${spring.yt-dlp.timeout-seconds:180}") long timeoutSeconds,
      @Value("${spring.yt-dlp.cookies-path:}") String cookiesPath,
      @Value("${spring.yt-dlp.max-filesize-mib:100}") long maxFilesizeMib,
      @Value("${spring.yt-dlp.max-duration-seconds:0}") long maxDurationSeconds
  ) {
    this.executable = executable;
    this.timeoutSeconds = timeoutSeconds;
    this.cookiesPath = cookiesPath;
    this.maxFilesizeMib = Math.max(1, maxFilesizeMib);
    this.maxDurationSeconds = Math.max(0, maxDurationSeconds);
  }

  public YtDlpService(String executable, long timeoutSeconds, String cookiesPath) {
    this(executable, timeoutSeconds, cookiesPath, 100, 0);
  }

  public YtDlpService(String executable, long timeoutSeconds) {
    this(executable, timeoutSeconds, "");
  }

  public Path download(String url, Path destinationDirectory, boolean withThumbnail) throws IOException {
    validateUrl(url);
    Files.createDirectories(destinationDirectory);

    Path outputTemplate = destinationDirectory.resolve("audio.%(ext)s");
    List<String> command = new ArrayList<>();
    command.add(executable);
    command.add("--no-playlist");
    command.add("--no-progress");
    command.add("--no-warnings");
    command.add("-x");
    command.add("--audio-format");
    command.add("mp3");
    command.add("--audio-quality");
    command.add("0");
    command.add("--max-filesize");
    command.add(maxFilesizeMib + "M");
    if (maxDurationSeconds > 0) {
      command.add("--match-filter");
      command.add("duration<=" + maxDurationSeconds);
    }
    if (cookiesPath != null && !cookiesPath.isBlank()) {
      command.add("--cookies");
      command.add(cookiesPath);
    }
    command.add("--embed-metadata");
    if (withThumbnail) {
      command.add("--write-thumbnail");
      command.add("--convert-thumbnails");
      command.add("jpg");
    }
    command.add("-o");
    command.add(outputTemplate.toString());
    command.add(url);

    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "yt-dlp is not installed or not configured",
        exception
      );
    }

    CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
      try {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException exception) {
        throw new CompletionException(exception);
      }
    });

    boolean finished;
    try {
      finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Audio import interrupted", exception);
    }

    if (!finished) {
      process.destroyForcibly();
      throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Audio fetch timed out");
    }

    String processOutput = output.join();
    if (process.exitValue() != 0) {
      logger.warn("yt-dlp failed for {}: {}", url, processOutput);
      throw new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Unable to fetch from the provided URL"
      );
    }

    Path audioFile;
    try (Stream<Path> files = Files.list(destinationDirectory)) {
      audioFile = files
        .filter(Files::isRegularFile)
        .filter(YtDlpService::isAudioFile)
        .findFirst()
        .orElse(null);
    }

    // yt-dlp exits 0 without producing a file when it skips a video (size/duration limits, filters).
    if (audioFile == null) {
      logger.warn("yt-dlp produced no audio file for {}: {}", url, processOutput);
      throw missingFileException(processOutput);
    }

    if (Files.size(audioFile) > maxFilesizeMib * 1024 * 1024) {
      Files.deleteIfExists(audioFile);
      throw new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Audio file must not exceed " + maxFilesizeMib + " MiB"
      );
    }

    return audioFile;
  }

  ResponseStatusException missingFileException(String processOutput) {
    if (processOutput.contains("larger than max-filesize")) {
      return new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Audio file must not exceed " + maxFilesizeMib + " MiB"
      );
    }
    if (maxDurationSeconds > 0 && processOutput.contains("does not pass filter")) {
      return new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Audio duration must not exceed " + formatDuration(maxDurationSeconds)
      );
    }
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Unable to fetch from the provided URL");
  }

  private static String formatDuration(long seconds) {
    return seconds % 60 == 0 ? (seconds / 60) + " minutes" : seconds + " seconds";
  }

  /**
   * Runs one search against a single source and returns up to {@code count} tracks. Throws a 502
   * {@link ResponseStatusException} when the source fails or times out, so callers can fall back to
   * another source; an empty list means the source answered with no tracks.
   */
  public List<SearchResultResponse> search(SearchSource source, String query, int count) {
    List<String> command = new ArrayList<>();
    command.add(executable);
    command.add("--flat-playlist");
    command.add("--dump-json");
    command.add("--no-warnings");
    command.add("--playlist-end");
    command.add(String.valueOf(count));
    if (cookiesPath != null && !cookiesPath.isBlank()) {
      command.add("--cookies");
      command.add(cookiesPath);
    }
    command.add(searchTarget(source, query, count));

    String output = runForOutput(command, Math.min(timeoutSeconds, SEARCH_TIMEOUT_SECONDS), source);
    return parseSearchResults(output, source.label(), count);
  }

  private static String searchTarget(SearchSource source, String query, int count) {
    return switch (source) {
      case YOUTUBE_MUSIC -> "https://music.youtube.com/search?q="
        + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&sp=" + MUSIC_SONGS_FILTER;
      case YOUTUBE -> "ytsearch" + count + ":" + query;
      case SOUNDCLOUD -> "scsearch" + count + ":" + query;
    };
  }

  static List<SearchResultResponse> parseSearchResults(String output, String source, int limit) {
    List<SearchResultResponse> results = new ArrayList<>();
    for (String line : output.split("\\R")) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode node;
      try {
        node = JSON.readTree(line);
      } catch (JacksonException exception) {
        continue;
      }

      String url = firstText(node, "webpage_url", "url");
      String title = firstText(node, "title");
      if (url == null || title == null || !isPlayableUrl(url)) {
        continue;
      }

      results.add(SearchResultResponse.builder()
        .title(title)
        .url(url)
        .uploader(firstText(node, "uploader", "channel", "artist"))
        .duration(node.hasNonNull("duration") ? (int) Math.round(node.get("duration").asDouble()) : null)
        .thumbnail(thumbnail(node, url))
        .source(source)
        .build());
      if (results.size() >= limit) {
        break;
      }
    }
    return results;
  }

  // Music search mixes in artist/album pages (…/browse/…, playlists); only single tracks are fetchable.
  private static boolean isPlayableUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException exception) {
      return false;
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null || host == null || !(scheme.equals("http") || scheme.equals("https"))) {
      return false;
    }
    if (host.endsWith("youtube.com")) {
      return "/watch".equals(uri.getPath());
    }
    return true;
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      JsonNode value = node.get(field);
      if (value != null && value.isString() && !value.asString().isBlank()) {
        return value.asString();
      }
    }
    return null;
  }

  private static String thumbnail(JsonNode node, String url) {
    String direct = firstText(node, "thumbnail");
    if (direct != null) {
      return direct;
    }
    JsonNode thumbnails = node.get("thumbnails");
    if (thumbnails != null && thumbnails.isArray() && !thumbnails.isEmpty()) {
      // yt-dlp lists thumbnails from smallest to largest.
      return firstText(thumbnails.get(thumbnails.size() - 1), "url");
    }
    // YouTube Music flat results carry no thumbnail; derive the standard one from the video id.
    String id = firstText(node, "id");
    if (id != null && id.matches("[\\w-]{11}") && URI.create(url).getHost().endsWith("youtube.com")) {
      return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }
    return null;
  }

  private String runForOutput(List<String> command, long timeout, SearchSource source) {
    Process process;
    try {
      process = new ProcessBuilder(command)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "yt-dlp is not installed or not configured",
        exception
      );
    }

    CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
      try {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException exception) {
        throw new CompletionException(exception);
      }
    });

    boolean finished;
    try {
      finished = process.waitFor(timeout, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Search interrupted", exception);
    }

    if (!finished) {
      process.destroyForcibly();
      logger.warn("yt-dlp search via {} timed out after {}s", source.label(), timeout);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Search source is unavailable");
    }
    if (process.exitValue() != 0) {
      logger.warn("yt-dlp search via {} failed with exit code {}", source.label(), process.exitValue());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Search source is unavailable");
    }
    return output.join();
  }

  private static boolean isAudioFile(Path file) {
    String filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
    return filename.endsWith(".mp3") || filename.endsWith(".wav") || filename.endsWith(".ogg");
  }

  private void validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL", exception);
    }

    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only HTTP and HTTPS URLs are supported");
    }

    String host = uri.getHost();
    if (host == null || host.isBlank() || uri.getUserInfo() != null || isPrivateHost(host)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL host is not allowed");
    }
  }

  // ponytail: blocks literal local/private hosts only; add DNS/IP resolution if this is exposed publicly.
  private boolean isPrivateHost(String host) {
    String normalized = host.toLowerCase(Locale.ROOT);
    if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
      return true;
    }
    if (normalized.equals("::1") || normalized.equals("[::1]")) {
      return true;
    }
    if (normalized.startsWith("127.") || normalized.startsWith("10.")
        || normalized.startsWith("192.168.") || normalized.startsWith("169.254.")) {
      return true;
    }
    if (normalized.startsWith("172.")) {
      String[] parts = normalized.split("\\.");
      if (parts.length > 1) {
        try {
          int secondOctet = Integer.parseInt(parts[1]);
          return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException ignored) {
          return false;
        }
      }
    }
    return false;
  }
}
