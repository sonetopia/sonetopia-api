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
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import com.sonetopia.entity.Audio;
import com.sonetopia.repository.AudioRepository;

@Service 
public class LocalAudioStorageService implements AudioStorageService {

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
    Path destination = rootLocation.resolve(storageKey);
    Files.createDirectories(destination.getParent());
    Files.copy(audioFile.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

    return storageKey;
  }

  @Override
  public String storeImageCover(UUID userId, UUID audioId, MultipartFile imageCover) throws IOException {

    String extension = getExtension(imageCover.getOriginalFilename());
    String storageKey = "users/" + userId + "/audio/" + audioId + "/cover." + extension;
    Path destination = rootLocation.resolve(storageKey);
    Files.createDirectories(destination.getParent());
    Files.copy(imageCover.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

    return storageKey;
  }

  @Override 
  public Resource load(String storageKey) {
    Path filePath = rootLocation.resolve(storageKey);
    try {
      return new org.springframework.core.io.UrlResource(filePath.toUri());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load file: " + storageKey, e);
    }
  }

  @Override
  public void delete(String storageKey) {
    Path filePath = rootLocation.resolve(storageKey);
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
      throw new RuntimeException("Failed to delete audio directory: " + directory, e);
    }
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

    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }
}
