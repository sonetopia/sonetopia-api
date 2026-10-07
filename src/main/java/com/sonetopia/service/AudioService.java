package com.sonetopia.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
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
import com.sonetopia.entity.User;
import com.sonetopia.model.AudioResponse;
import com.sonetopia.model.CreateAudioRequest;
import com.sonetopia.repository.AudioRepository;

@Service 
public class AudioService {

  @Autowired 
  private AudioRepository audioRepository;
  
  @Autowired 
  private ValidationService validationService;

  @Autowired
  private LocalAudioStorageService localAudioStorageService;

  private AudioResponse toAudioResponse(Audio audio) {
    return AudioResponse.builder()
        .id(audio.getId())
        .title(audio.getTitle())
        .type(audio.getType())
        .author(audio.getAuthor())
        .album(audio.getAlbum())
        .year(audio.getYear())
        .imageCover(audio.getImageCover())
        .storageKey(audio.getStorageKey())
        .mimeType(audio.getMimeType())
        .fileExtension(audio.getFileExtension())
        .fileSize(audio.getFileSize())
        .build();
  }

  @Transactional 
  public AudioResponse create(CreateAudioRequest request, User user) throws IOException {
    validationService.validate(request);

    Audio audio = new Audio();
    audio.setUser(user);

    MultipartFile audioFile = request.getAudioFile();
    audio.setStorageKey("temp"); // Temporary storage key, will be updated after storing the audio file
    
    audio.setTitle(request.getTitle());
    audio.setType(request.getType());
    audio.setAuthor(request.getAuthor());
    audio.setAlbum(request.getAlbum());
    audio.setYear(request.getYear());
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());

    audio.setMimeType(audioFile.getContentType());
    audio.setFileSize(audioFile.getSize());

    String originalFilename = audioFile.getOriginalFilename();
    String exntension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

    audio.setFileExtension(exntension);

    // Save the audio entity to the database
    Audio savedAudio = audioRepository.save(audio);
    
    String audioStorageKey = localAudioStorageService.storeAudio(
      user.getId(),
      savedAudio.getId(),
      audioFile
    );
    savedAudio.setStorageKey(audioStorageKey);

    MultipartFile imageCover = request.getImageCover();
    if (imageCover != null && !imageCover.isEmpty()) {
      String imageCoverStorageKey = localAudioStorageService.storeImageCover(
        user.getId(),
        savedAudio.getId(),
        imageCover
      );
      savedAudio.setImageCover(imageCoverStorageKey);
    }

    audioRepository.save(savedAudio);

    return toAudioResponse(savedAudio);
  }

  @Transactional(readOnly = true)
  public AudioResponse get(UUID audioId, User user) {
    Audio audio = audioRepository.findById(audioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    AudioResponse audioResponse = toAudioResponse(audio);
    audioResponse.setSignedUrl(localAudioStorageService.createSignedUrl(audio.getId(), Duration.ofMinutes(60)));
    
    return audioResponse;
  }

  @Transactional(readOnly = true)
  public ResponseEntity<Resource> stream(@RequestParam("id") UUID audioId, @RequestParam("expires") Long expires, @RequestParam("signature") String signature) {
    Audio audio = audioRepository.findById(audioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    if (!localAudioStorageService.validateSignedUrl(audioId, expires, signature)) {
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
}
