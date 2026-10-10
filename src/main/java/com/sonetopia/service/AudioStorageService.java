package com.sonetopia.service;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AudioStorageService {

  String storeAudio(UUID userId, UUID audioId, MultipartFile audioFile) throws IOException;

  String storeImageCover(UUID userId, UUID audioId, MultipartFile imageCover) throws IOException;

  Resource load(String storageKey);

  void delete(String storageKey);

  String createSignedUrl(UUID id, String type, Duration expiration);

  Boolean validateSignedUrl(UUID id, String type, Long expires, String signature);
}
