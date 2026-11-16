package com.sonetopia.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;

import com.sonetopia.entity.Audio;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.service.AudioMetadataService;
import com.sonetopia.service.LocalAudioStorageService;

class SyncAudioUrlsRunnerTest {

  @TempDir
  Path storageRoot;

  private AudioRepository audioRepository;
  private SyncAudioUrlsRunner runner;

  @BeforeEach
  void setUp() {
    audioRepository = mock(AudioRepository.class);
    runner = new SyncAudioUrlsRunner(
      audioRepository,
      new LocalAudioStorageService(storageRoot.toString()),
      new AudioMetadataService()
    );
  }

  @Test
  void doesNothingWithoutTheOption() {
    runner.run(new DefaultApplicationArguments());

    verify(audioRepository, never()).findBySourceUrlIsNull();
  }

  @Test
  void backfillsUrlsFromTagsAndSkipsTheRest() throws Exception {
    Audio fromYtDlp = audio("yt-dlp import", taggedMp3("ytdlp.mp3", "https://music.youtube.com/watch?v=9qnqYL0eNNI&list=abc"));
    Audio freeTextComment = audio("manual upload", taggedMp3("manual.mp3", "recorded live at home"));
    Audio untagged = audio("untagged", taggedMp3("untagged.mp3", null));
    Audio missingFile = audio("missing", "users/nobody/audio/gone/audio.mp3");

    when(audioRepository.findBySourceUrlIsNull()).thenReturn(List.of(fromYtDlp, freeTextComment, untagged, missingFile));

    runner.run(new DefaultApplicationArguments("--sync-audio-urls"));

    assertEquals("https://www.youtube.com/watch?v=9qnqYL0eNNI", fromYtDlp.getSourceUrl());
    assertNull(freeTextComment.getSourceUrl());
    assertNull(untagged.getSourceUrl());
    assertNull(missingFile.getSourceUrl());
    verify(audioRepository).saveAll(List.of(fromYtDlp, freeTextComment, untagged, missingFile));
  }

  @Test
  void readsTheUrlFfmpegWritesIntoRealYtDlpFiles() throws Exception {
    // Fixture made with ffmpeg the way yt-dlp does: purl/comment end up as custom TXXX frames.
    Path target = storageRoot.resolve("ytdlp-real.mp3");
    try (InputStream inputStream = new ClassPathResource("audio/ytdlp-tagged.mp3").getInputStream()) {
      Files.copy(inputStream, target);
    }
    Audio imported = audio("Yellow", "ytdlp-real.mp3");
    when(audioRepository.findBySourceUrlIsNull()).thenReturn(List.of(imported));

    runner.run(new DefaultApplicationArguments("--sync-audio-urls"));

    assertEquals("https://www.youtube.com/watch?v=9qnqYL0eNNI", imported.getSourceUrl());
  }

  @Test
  void dryRunReportsWithoutChangingAnything() throws Exception {
    Audio fromYtDlp = audio("yt-dlp import", taggedMp3("ytdlp.mp3", "https://youtu.be/9qnqYL0eNNI"));
    when(audioRepository.findBySourceUrlIsNull()).thenReturn(List.of(fromYtDlp));

    runner.run(new DefaultApplicationArguments("--sync-audio-urls", "--dry-run"));

    assertNull(fromYtDlp.getSourceUrl());
    verify(audioRepository, never()).saveAll(anyList());
  }

  private Audio audio(String title, String storageKey) {
    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setTitle(title);
    audio.setStorageKey(storageKey);
    return audio;
  }

  /** Copies the sample mp3 into the storage root and sets its comment tag; returns the storage key. */
  private String taggedMp3(String name, String comment) throws Exception {
    Path target = storageRoot.resolve(name);
    try (InputStream inputStream = new ClassPathResource("audio/sempurna.mp3").getInputStream()) {
      Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }

    AudioFile audioFile = AudioFileIO.read(target.toFile());
    Tag tag = audioFile.getTagOrCreateAndSetDefault();
    tag.deleteField(FieldKey.COMMENT);
    if (comment != null) {
      tag.setField(FieldKey.COMMENT, comment);
    }
    audioFile.commit();

    return name;
  }
}
