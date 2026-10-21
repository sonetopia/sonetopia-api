package com.sonetopia.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.stereotype.Service;

import com.sonetopia.model.AudioMetadata;

@Service 
public class AudioMetadataService {

  private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

  public int getDurationInSeconds(Path audioPath) throws IOException {
    try {
      AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
      return audioFile.getAudioHeader().getTrackLength();
    } catch (Exception e) {
      throw new IOException("Failed to read audio duration", e);
    }
  }

  public AudioMetadata read(Path audioPath) throws IOException {
    try {
      AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
      Tag tag = audioFile.getTag();

      return new AudioMetadata(
        tagValue(tag, FieldKey.TITLE),
        tagValue(tag, FieldKey.ARTIST),
        tagValue(tag, FieldKey.ALBUM),
        parseYear(tagValue(tag, FieldKey.YEAR)),
        audioFile.getAudioHeader() != null ? audioFile.getAudioHeader().getTrackLength() : null
      );
    } catch (Exception e) {
      throw new IOException("Failed to read audio metadata", e);
    }
  }

  private String tagValue(Tag tag, FieldKey key) {
    if (tag == null) {
      return null;
    }

    try {
      String value = tag.getFirst(key);
      return value == null || value.isBlank() ? null : value;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private Short parseYear(String value) {
    if (value == null) {
      return null;
    }

    Matcher matcher = YEAR_PATTERN.matcher(value);
    return matcher.find() ? Short.valueOf(matcher.group(1)) : null;
  }
}
