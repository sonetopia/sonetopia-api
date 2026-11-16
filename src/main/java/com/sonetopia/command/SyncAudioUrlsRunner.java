package com.sonetopia.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sonetopia.entity.Audio;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.service.AudioMetadataService;
import com.sonetopia.service.LocalAudioStorageService;

/**
 * Backfills {@code audio.source_url} for audios imported before the URL was stored, using the URL
 * yt-dlp embedded in the file's tags. Audios that already have a URL are left alone, so it is safe to
 * run more than once.
 */
@Component
public class SyncAudioUrlsRunner implements ApplicationRunner {

  public static final String SYNC_OPTION = "sync-audio-urls";
  public static final String DRY_RUN_OPTION = "dry-run";

  private final AudioRepository audioRepository;
  private final LocalAudioStorageService storageService;
  private final AudioMetadataService metadataService;

  public SyncAudioUrlsRunner(
      AudioRepository audioRepository,
      LocalAudioStorageService storageService,
      AudioMetadataService metadataService
  ) {
    this.audioRepository = audioRepository;
    this.storageService = storageService;
    this.metadataService = metadataService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!args.containsOption(SYNC_OPTION)) {
      return;
    }

    boolean dryRun = args.containsOption(DRY_RUN_OPTION);
    List<Audio> audios = audioRepository.findBySourceUrlIsNull();

    System.out.println((dryRun ? "[dry run] " : "") + "Checking " + audios.size() + " audio(s) without a source URL...");

    int updated = 0;
    int noUrl = 0;
    int unreadable = 0;

    for (Audio audio : audios) {
      String label = "'" + audio.getTitle() + "' (" + audio.getId() + ")";
      Optional<String> sourceUrl;

      try {
        Path file = storageService.resolveStorageKey(audio.getStorageKey());
        if (!Files.isRegularFile(file)) {
          throw new IOException("file not found");
        }
        sourceUrl = metadataService.readSourceUrl(file);
      } catch (IOException | RuntimeException exception) {
        unreadable++;
        System.out.println("  ! cannot read " + label + ": " + exception.getMessage());
        continue;
      }

      if (sourceUrl.isEmpty()) {
        noUrl++;
        System.out.println("  - no source URL in tags: " + label);
        continue;
      }

      updated++;
      System.out.println("  + " + label + " -> " + sourceUrl.get());
      if (!dryRun) {
        audio.setSourceUrl(sourceUrl.get());
      }
    }

    if (!dryRun && updated > 0) {
      audioRepository.saveAll(audios);
    }

    System.out.println(
      (dryRun ? "[dry run] Would update " : "Updated ") + updated
        + ", no URL in tags " + noUrl
        + ", unreadable " + unreadable + "."
    );
  }
}
