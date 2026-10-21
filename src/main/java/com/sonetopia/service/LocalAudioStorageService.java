package com.sonetopia.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.sonetopia.entity.Audio;
import com.sonetopia.repository.AudioRepository;

@Service 
public class LocalAudioStorageService implements AudioStorageService {

  private static final String TEMP_DIRECTORY = "temp";

  private final Path rootLocation;

  @Value("${spring.storage.signed-url-secret}")
  private String signedUrlSecret;

  @Autowired 
  private AudioRepository audioRepository;

  public LocalAudioStorageService(@Value("${spring.storage.audio-path}") String audioPath) {
    this.rootLocation = Paths.get(audioPath).toAbsolutePath().normalize();
  }

  @Override
  public String storeAudio(UUID userId, UUID audioId, MultipartFile audioFile) throws IOException {

    String extension = getExtension(audioFile.getOriginalFilename());
    String storageKey = "users/" + userId + "/audio/" + audioId + "/audio." + extension;
    Path destination = resolveStorageKey(storageKey);

    Files.createDirectories(destination.getParent());
    try (var inputStream = audioFile.getInputStream()) {
      Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new IOException("Failed to store audio file", e);
    }

    try {
      AudioFile parsedAudio = AudioFileIO.read(destination.toFile());
      if (parsedAudio.getAudioHeader() == null) {
        throw new IOException("Invalid or unsupported audio file");
      }
    } catch (Exception e) {
      try {
        Files.deleteIfExists(destination);
      } catch (IOException cleanupException) {
        e.addSuppressed(cleanupException);
      }

      throw new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Invalid or unsupported audio file",
        e
      );
    }

    return storageKey;
  }

  @Override
  public String storeImageCover(UUID userId, UUID audioId, MultipartFile imageCover) throws IOException {

    String extension = getExtension(imageCover.getOriginalFilename());
    String storageKey = "users/" + userId + "/audio/" + audioId + "/cover." + extension;
    Path destination = resolveStorageKey(storageKey);
    
    Files.createDirectories(destination.getParent());
    try (var inputStream = imageCover.getInputStream()) {
      Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new IOException("Failed to store image cover", e);
    }

    return storageKey;
  }

  public Path createTempAudioDirectory(UUID userId, UUID tempId) throws IOException {
    Path directory = tempDirectory(userId, tempId);
    Files.createDirectories(directory);
    return directory;
  }

  public Path resolveTempAudio(UUID userId, UUID tempId) {
    Path directory = tempDirectory(userId, tempId);
    if (!Files.isDirectory(directory)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Temporary audio not found");
    }

    try (Stream<Path> files = Files.list(directory)) {
      return files
        .filter(Files::isRegularFile)
        .filter(file -> !file.getFileName().toString().endsWith(".part"))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Temporary audio not found"));
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Temporary audio not found", exception);
    }
  }

  public String promoteTempAudio(UUID userId, UUID audioId, UUID tempId) throws IOException {
    Path source = resolveTempAudio(userId, tempId);
    String extension = getExtension(source.getFileName().toString());
    String storageKey = "users/" + userId + "/audio/" + audioId + "/audio." + extension;
    Path destination = resolveStorageKey(storageKey);

    Files.createDirectories(destination.getParent());
    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    deleteTempDirectory(userId, tempId);

    return storageKey;
  }

  public void deleteTempDirectory(UUID userId, UUID tempId) {
    Path directory = tempDirectory(userId, tempId);
    try {
      deleteDirectory(directory);
    } catch (RuntimeException ignored) {
      // Temporary files are also removed by the TTL cleanup.
    }
  }

  public void cleanupExpiredTempAudio(Duration ttl) {
    Path tempRoot = tempRoot();
    if (!Files.isDirectory(tempRoot)) {
      return;
    }

    Instant cutoff = Instant.now().minus(ttl);
    try (Stream<Path> users = Files.list(tempRoot)) {
      users.filter(Files::isDirectory).forEach(userDirectory -> {
        try (Stream<Path> temps = Files.list(userDirectory)) {
          temps.filter(Files::isDirectory).forEach(tempDirectory -> {
            try {
              if (Files.getLastModifiedTime(tempDirectory).toInstant().isBefore(cutoff)) {
                deleteDirectory(tempDirectory);
              }
            } catch (IOException | RuntimeException ignored) {
              // Keep the stale directory; the next cleanup pass can retry.
            }
          });
        } catch (IOException ignored) {
          // Keep the stale user directory; the next cleanup pass can retry.
        }
      });
    } catch (IOException ignored) {
      // Temporary cleanup must not block an import.
    }
  }

  @Override 
  public Resource load(String storageKey) {
    Path filePath = resolveStorageKey(storageKey);
    try {
      return new UrlResource(filePath.toUri());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load file: " + storageKey, e);
    }
  }

  @Override
  public void delete(String storageKey) {
    Path filePath = resolveStorageKey(storageKey);
    try {
      Files.deleteIfExists(filePath);
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete file: " + storageKey, e);
    }
  }

  @Override
  public String createSignedUrl(UUID id, String type, Duration expiration) {
    if (!type.equals("audio") && !type.equals("cover")) {
      throw new IllegalArgumentException("Invalid type. Must be 'audio' or 'cover'.");
    }

    Audio audio = audioRepository.findById(id).orElseThrow(() -> new RuntimeException("Audio not found"));
    
    Long expirationTime = Instant.now().plus(expiration).getEpochSecond();

    String payload = id + ":" + type + ":" + expirationTime;
    String signature = generateSignature(payload);

    String basePath = type.equals("audio") ? "/api/v1/media/audio" : "/api/v1/media/cover";

    return UriComponentsBuilder.fromPath(basePath)
        .queryParam("id", audio.getId())
        .queryParam("expires", expirationTime)
        .queryParam("signature", signature)
        .build()
        .toUriString();
  }

  @Override
  public Boolean validateSignedUrl(UUID id, String type, Long expires, String signature) {
    if (Instant.now().getEpochSecond() > expires) {
      return false;
    }

    String payload = id + ":" + type + ":" + expires;
    String expectedSignature = generateSignature(payload);

    return expectedSignature.equals(signature);
  }

  public Boolean deleteAudioDirectory(UUID userId, UUID audioId) {
    Path directory = rootLocation
      .resolve("users")
      .resolve(userId.toString())
      .resolve("audio")
      .resolve(audioId.toString())
      .normalize();

    if (!directory.startsWith(rootLocation.normalize())) {
      throw new IllegalArgumentException("Invalid directory path");
    }

    return deleteDirectory(directory);
  }

  private Path tempRoot() {
    return rootLocation.resolve(TEMP_DIRECTORY).normalize();
  }

  private Path tempDirectory(UUID userId, UUID tempId) {
    Path directory = tempRoot().resolve(userId.toString()).resolve(tempId.toString()).normalize();
    if (!directory.startsWith(tempRoot())) {
      throw new IllegalArgumentException("Invalid temporary path");
    }
    return directory;
  }

  private boolean deleteDirectory(Path directory) {
    if (!Files.exists(directory)) {
      return false;
    }

    try {
      Files.walkFileTree(directory, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          if (exc != null) {
            throw exc;
          }

          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });

      return true;
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete directory: " + directory, e);
    }
  }

  public Path resolveStorageKey(String storageKey) {
    if (storageKey == null || storageKey.isEmpty()) {
      throw new IllegalArgumentException("Storage key must not be empty");
    }

    Path filePath = rootLocation.resolve(storageKey).normalize();
    if (!filePath.startsWith(rootLocation)) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    return filePath;
  }

  private String generateSignature(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec secretKeySpec = new SecretKeySpec(signedUrlSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      
      mac.init(secretKeySpec);

      byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      
      return HexFormat.of().formatHex(hash);

    } catch (Exception e) {
      throw new RuntimeException("Failed to generate signature", e);
    }
  }

  private String getExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      throw new IllegalArgumentException("Invalid file name");
    }

    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }
}
