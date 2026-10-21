package com.sonetopia.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.User;
import com.sonetopia.model.AudioMetadata;
import com.sonetopia.model.AudioResponse;
import com.sonetopia.model.AudioCoverResponse;
import com.sonetopia.model.CreateAudioRequest;
import com.sonetopia.model.CreateAudioFromTempRequest;
import com.sonetopia.model.FetchAudioRequest;
import com.sonetopia.model.FetchedAudioResponse;
import com.sonetopia.model.UpdateAudioRequest;
import com.sonetopia.repository.AudioRepository;

@Service 
public class AudioService {

  @Autowired 
  private AudioRepository audioRepository;
  
  @Autowired 
  private ValidationService validationService;

  @Autowired
  private LocalAudioStorageService localAudioStorageService;

  @Autowired
  private StorageQuotaService storageQuotaService;

  @Autowired
  private AudioMetadataService audioMetadataService;

  @Autowired
  private YtDlpService ytDlpService;

  @Value("${spring.storage.temp-audio-ttl-minutes:60}")
  private long tempAudioTtlMinutes;

  private AudioResponse toAudioResponse(Audio audio) {
    return AudioResponse.builder()
        .id(audio.getId())
        .title(audio.getTitle())
        .type(audio.getType())
        .author(audio.getAuthor())
        .album(audio.getAlbum())
        .year(audio.getYear())
        .coverVersion(audio.getCoverVersion())
        .mimeType(audio.getMimeType())
        .fileSize(audio.getFileSize())
        .duration(audio.getDuration())
        .createdAt(audio.getCreatedAt())
        .updatedAt(audio.getUpdatedAt())
        .build();
  }

  @Transactional(rollbackFor = {IOException.class, RuntimeException.class})
  public AudioResponse create(CreateAudioRequest request, User user) throws IOException {
    validationService.validate(request);

    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(user);

    MultipartFile audioFile = request.getAudioFile();
    MultipartFile imageCover = request.getImageCover();
    long uploadSize = audioFile.getSize() + (imageCover == null ? 0L : imageCover.getSize());
    storageQuotaService.addUsage(user, uploadSize);
    
    audio.setTitle(request.getTitle());
    audio.setType(request.getType());
    audio.setAuthor(request.getAuthor());
    audio.setAlbum(request.getAlbum());
    audio.setYear(request.getYear());
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());

    audio.setMimeType(audioFile.getContentType());
    audio.setFileSize(audioFile.getSize());

    try {
      String audioStorageKey = localAudioStorageService.storeAudio(
        user.getId(),
        audio.getId(),
        audioFile
      );
      audio.setStorageKey(audioStorageKey);

      Path audioPath = localAudioStorageService.resolveStorageKey(audioStorageKey);
      audio.setDuration(audioMetadataService.getDurationInSeconds(audioPath));
  
      if (imageCover != null && !imageCover.isEmpty()) {
        String imageCoverStorageKey = localAudioStorageService.storeImageCover(
          user.getId(),
          audio.getId(),
          imageCover
        );
        audio.setCoverMimeType(imageCover.getContentType());
        audio.setCoverFileSize(imageCover.getSize());
        audio.setCoverVersion(1);
        audio.setCoverStorageKey(imageCoverStorageKey);
      }
  
      audioRepository.save(audio);
  
      return toAudioResponse(audio);
    } catch (IOException | RuntimeException e) {
      // Roll back filesystem changes
      try {
          localAudioStorageService.deleteAudioDirectory(
              user.getId(),
              audio.getId()
          );
      } catch (RuntimeException cleanupException) {
          // Preserve the original exception
          e.addSuppressed(cleanupException);
      }

      throw e;
    }
  }

  public FetchedAudioResponse fetch(FetchAudioRequest request, User user) throws IOException {
    validationService.validate(request);
    localAudioStorageService.cleanupExpiredTempAudio(Duration.ofMinutes(tempAudioTtlMinutes));

    UUID tempId = UUID.randomUUID();
    Path tempDirectory = localAudioStorageService.createTempAudioDirectory(user.getId(), tempId);

    try {
      Path audioPath = ytDlpService.download(request.getUrl(), tempDirectory);
      AudioMetadata metadata;
      try {
        metadata = audioMetadataService.read(audioPath);
      } catch (IOException exception) {
        throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Downloaded file is not a supported audio file",
          exception
        );
      }

      return FetchedAudioResponse.builder()
        .tempId(tempId)
        .title(metadata.title() != null ? metadata.title() : "Untitled")
        .author(metadata.author())
        .album(metadata.album())
        .year(metadata.year())
        .duration(metadata.duration())
        .mimeType(mimeTypeFor(audioPath))
        .fileSize(Files.size(audioPath))
        .build();
    } catch (IOException | RuntimeException exception) {
      localAudioStorageService.deleteTempDirectory(user.getId(), tempId);
      throw exception;
    }
  }

  @Transactional(rollbackFor = {IOException.class, RuntimeException.class})
  public AudioResponse createFromTemp(CreateAudioFromTempRequest request, User user) throws IOException {
    validationService.validate(request);

    Path tempAudioPath = localAudioStorageService.resolveTempAudio(user.getId(), request.getTempId());
    long uploadSize = Files.size(tempAudioPath);
    storageQuotaService.addUsage(user, uploadSize);

    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(user);
    audio.setTitle(request.getTitle());
    audio.setType(request.getType());
    audio.setAuthor(request.getAuthor());
    audio.setAlbum(request.getAlbum());
    audio.setYear(request.getYear());
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());
    audio.setMimeType(mimeTypeFor(tempAudioPath));
    audio.setFileSize(uploadSize);

    try {
      String storageKey = localAudioStorageService.promoteTempAudio(
        user.getId(),
        audio.getId(),
        request.getTempId()
      );
      audio.setStorageKey(storageKey);

      Path audioPath = localAudioStorageService.resolveStorageKey(storageKey);
      audio.setDuration(audioMetadataService.getDurationInSeconds(audioPath));

      audioRepository.save(audio);

      return toAudioResponse(audio);
    } catch (IOException | RuntimeException exception) {
      localAudioStorageService.deleteTempDirectory(user.getId(), request.getTempId());
      try {
        localAudioStorageService.deleteAudioDirectory(user.getId(), audio.getId());
      } catch (RuntimeException cleanupException) {
        exception.addSuppressed(cleanupException);
      }

      throw exception;
    }
  }

  private String mimeTypeFor(Path audioPath) {
    String filename = audioPath.getFileName().toString().toLowerCase(Locale.ROOT);
    if (filename.endsWith(".mp3")) {
      return "audio/mpeg";
    }
    if (filename.endsWith(".wav")) {
      return "audio/wav";
    }
    if (filename.endsWith(".ogg")) {
      return "audio/ogg";
    }
    return "application/octet-stream";
  }

  @Transactional(readOnly = true)
  public AudioResponse get(UUID audioId, User user) {
    Audio audio = audioRepository.findByIdAndUser(audioId, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    AudioResponse audioResponse = toAudioResponse(audio);
    audioResponse.setSignedUrl(localAudioStorageService.createSignedUrl(audio.getId(), "audio", Duration.ofMinutes(60)));
    
    return audioResponse;
  }

  @Transactional(rollbackFor = {IOException.class, RuntimeException.class})
  public AudioResponse update(UUID audioId, UpdateAudioRequest request, User user) throws IOException {
    Audio audio = audioRepository.findByIdAndUser(audioId, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    validationService.validate(request);

    audio.setTitle(request.getTitle());
    audio.setType(request.getType());
    audio.setAuthor(request.getAuthor());
    audio.setAlbum(request.getAlbum());
    audio.setYear(request.getYear());
    audio.setUpdatedAt(LocalDateTime.now());

    MultipartFile imageCover = request.getImageCover();
    if (imageCover != null && !imageCover.isEmpty()) {
      long previousCoverSize = audio.getCoverFileSize() == null ? 0L : audio.getCoverFileSize();
      long sizeDelta = imageCover.getSize() - previousCoverSize;
      if (sizeDelta > 0) {
        storageQuotaService.addUsage(user, sizeDelta);
      } else if (sizeDelta < 0) {
        storageQuotaService.removeUsage(user, -sizeDelta);
      }

      if (audio.getCoverStorageKey() != null) {
        localAudioStorageService.delete(audio.getCoverStorageKey());
      }
      String imageCoverStorageKey = localAudioStorageService.storeImageCover(
        user.getId(),
        audio.getId(),
        imageCover
      );
      audio.setCoverMimeType(imageCover.getContentType());
      audio.setCoverFileSize(imageCover.getSize());
      audio.setCoverVersion(audio.getCoverVersion() != null ? audio.getCoverVersion() + 1 : 1);
      audio.setCoverStorageKey(imageCoverStorageKey);
    }

    Audio updatedAudio = audioRepository.save(audio);
    
    return toAudioResponse(updatedAudio);
  }

  @Transactional
  public void delete(UUID audioId, User user) {
    Audio audio = audioRepository.findByIdAndUser(audioId, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    localAudioStorageService.deleteAudioDirectory(user.getId(), audio.getId());

    long deletedSize = audio.getFileSize() + (audio.getCoverFileSize() == null ? 0L : audio.getCoverFileSize());
    storageQuotaService.removeUsage(user, deletedSize);

    audioRepository.delete(audio);
  }

  @Transactional(readOnly = true)
  public AudioCoverResponse getCover(UUID audioId, User user) {
    Audio audio = audioRepository.findByIdAndUser(audioId, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

    AudioCoverResponse audioCoverResponse = new AudioCoverResponse();
    audioCoverResponse.setId(audio.getId());
    audioCoverResponse.setCoverVersion(audio.getCoverVersion());
    audioCoverResponse.setMimeType(audio.getCoverMimeType());
    audioCoverResponse.setFileSize(audio.getCoverFileSize());
    audioCoverResponse.setSignedUrl(localAudioStorageService.createSignedUrl(audio.getId(), "cover", Duration.ofMinutes(60)));

    return audioCoverResponse;
  }

  @Transactional(readOnly = true)
  public List<AudioResponse> list(User user) {
    List<Audio> audioList = audioRepository.findByUser(user);
    List<AudioResponse> audioResponses = audioList.stream().map(this::toAudioResponse).toList();
    return audioResponses;
  }
}
