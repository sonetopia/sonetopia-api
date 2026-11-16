package com.sonetopia.service;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes media URLs so the same source always maps to the same key, e.g. a track opened from
 * music.youtube.com, youtube.com or youtu.be.
 */
public final class SourceUrls {

  public static final int MAX_LENGTH = 2048;

  private static final Pattern YOUTUBE_ID = Pattern.compile("[\\w-]{11}");
  private static final String[] YOUTUBE_ID_PATH_PREFIXES = {"/shorts/", "/embed/", "/live/", "/v/"};

  private SourceUrls() {}

  /** Returns the canonical form of {@code url}, or {@code null} when it is not a usable http(s) URL. */
  public static String canonicalize(String url) {
    if (url == null) {
      return null;
    }

    String trimmed = url.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
      return null;
    }

    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (IllegalArgumentException exception) {
      return null;
    }

    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null || host == null
        || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      return null;
    }
    host = host.toLowerCase(Locale.ROOT);

    String youtubeId = youtubeVideoId(host, uri);
    if (youtubeId != null) {
      return "https://www.youtube.com/watch?v=" + youtubeId;
    }

    StringBuilder canonical = new StringBuilder("https://").append(host);
    if (uri.getPort() != -1) {
      canonical.append(':').append(uri.getPort());
    }

    String path = uri.getRawPath() == null ? "" : uri.getRawPath();
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    canonical.append(path);

    if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
      canonical.append('?').append(uri.getRawQuery());
    }

    return canonical.length() > MAX_LENGTH ? null : canonical.toString();
  }

  private static String youtubeVideoId(String host, URI uri) {
    String path = uri.getRawPath() == null ? "" : uri.getRawPath();

    if (host.equals("youtu.be")) {
      return validId(firstSegment(path, 1));
    }

    if (!host.equals("youtube.com") && !host.endsWith(".youtube.com")) {
      return null;
    }

    if (path.equals("/watch")) {
      return validId(queryParameter(uri.getRawQuery(), "v"));
    }

    for (String prefix : YOUTUBE_ID_PATH_PREFIXES) {
      if (path.startsWith(prefix)) {
        return validId(firstSegment(path, prefix.length()));
      }
    }

    return null;
  }

  private static String firstSegment(String path, int from) {
    if (path.length() <= from) {
      return null;
    }
    int end = path.indexOf('/', from);
    return end == -1 ? path.substring(from) : path.substring(from, end);
  }

  private static String queryParameter(String rawQuery, String name) {
    if (rawQuery == null) {
      return null;
    }
    for (String pair : rawQuery.split("&")) {
      int separator = pair.indexOf('=');
      if (separator > 0 && pair.substring(0, separator).equals(name)) {
        return pair.substring(separator + 1);
      }
    }
    return null;
  }

  private static String validId(String candidate) {
    return candidate != null && YOUTUBE_ID.matcher(candidate).matches() ? candidate : null;
  }
}
