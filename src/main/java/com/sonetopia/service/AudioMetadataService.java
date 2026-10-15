package com.sonetopia.service;

import java.io.IOException;
import java.nio.file.Path;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Service;

@Service 
public class AudioMetadataService {

  public int getDurationInSeconds(Path audioPath) throws IOException {
    try {
      AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
      return audioFile.getAudioHeader().getTrackLength();
    } catch (Exception e) {
      throw new IOException("Failed to read audio duration", e);
    }
  }
}
