package com.sonetopia.service;

import java.io.IOException;
import java.net.URI;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class YtDlpService {

  private static final Logger logger = LoggerFactory.getLogger(YtDlpService.class);
  private static final long MAX_AUDIO_BYTES = 100L * 1024 * 1024;

  private final String executable;
  private final long timeoutSeconds;

  public YtDlpService(
      @Value("${spring.yt-dlp.path:yt-dlp}") String executable,
      @Value("${spring.yt-dlp.timeout-seconds:180}") long timeoutSeconds
  ) {
    this.executable = executable;
    this.timeoutSeconds = timeoutSeconds;
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
    command.add("100M");
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
        .orElseThrow(() -> new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "Unable to fetch from the provided URL"
        ));
    }

    if (Files.size(audioFile) > MAX_AUDIO_BYTES) {
      Files.deleteIfExists(audioFile);
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Audio file must not exceed 100 MiB");
    }

    return audioFile;
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
