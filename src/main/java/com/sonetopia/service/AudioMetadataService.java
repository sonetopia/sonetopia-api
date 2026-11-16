package com.sonetopia.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.springframework.stereotype.Service;

import com.sonetopia.model.AudioMetadata;

@Service 
public class AudioMetadataService {

  private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");
  private static final List<String> SOURCE_URL_FRAME_DESCRIPTIONS = List.of("purl", "comment");

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

  /**
   * Source URL that yt-dlp embeds in imported files. ffmpeg stores it as custom text frames named
   * {@code purl} and {@code comment}; a plain comment frame is accepted too. A value only counts when it
   * is exactly one URL, so free-text comments on uploaded files are ignored.
   */
  public Optional<String> readSourceUrl(Path audioPath) throws IOException {
    try {
      Tag tag = AudioFileIO.read(audioPath.toFile()).getTag();

      if (tag instanceof AbstractID3v2Tag id3Tag) {
        for (String description : SOURCE_URL_FRAME_DESCRIPTIONS) {
          String sourceUrl = SourceUrls.canonicalize(customTextFrame(id3Tag, description));
          if (sourceUrl != null) {
            return Optional.of(sourceUrl);
          }
        }
      }

      return Optional.ofNullable(SourceUrls.canonicalize(tagValue(tag, FieldKey.COMMENT)));
    } catch (Exception e) {
      throw new IOException("Failed to read audio tags", e);
    }
  }

  private String customTextFrame(AbstractID3v2Tag tag, String description) {
    List<TagField> frames = tag.getFrame("TXXX");
    if (frames == null) {
      return null;
    }

    for (TagField field : frames) {
      if (field instanceof AbstractID3v2Frame frame
          && frame.getBody() instanceof FrameBodyTXXX body
          && description.equalsIgnoreCase(body.getDescription())) {
        return body.getFirstTextValue();
      }
    }
    return null;
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
