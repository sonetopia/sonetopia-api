package com.sonetopia.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.Audio;
import com.sonetopia.repository.AudioRepository;

@Service 
public class MediaService {

  @Autowired 
  private AudioRepository audioRepository;

  @Autowired
  private LocalAudioStorageService localAudioStorageService;

  @Transactional(readOnly = true)
  public ResponseEntity<Resource> getAudio(@RequestParam("id") UUID audioId, @RequestParam("expires") Long expires, @RequestParam("signature") String signature) {
    Audio audio = audioRepository.findById(audioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    if (!localAudioStorageService.validateSignedUrl(audioId, "audio", expires, signature)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired signature");
    }

    Resource audioResource = localAudioStorageService.load(audio.getStorageKey());
    try {
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_TYPE, audio.getMimeType())
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .contentLength(audioResource.contentLength())
          .body(audioResource);
      
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to stream audio", e);
    }
  }

  @Transactional(readOnly = true)
  public ResponseEntity<Resource> getImageCover(@RequestParam("id") UUID audioId, @RequestParam("expires") Long expires, @RequestParam("signature") String signature) {
    Audio audio = audioRepository.findById(audioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    if (!localAudioStorageService.validateSignedUrl(audioId, "cover", expires, signature)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired signature");
    }

    Resource imageCoverResource = localAudioStorageService.load(audio.getCoverStorageKey());
    try {
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_TYPE, audio.getCoverMimeType())
          .contentLength(imageCoverResource.contentLength())
          .body(imageCoverResource);
      
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to stream image cover", e);
    }
  }
}
