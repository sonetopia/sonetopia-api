package com.sonetopia.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.Playlist;
import com.sonetopia.entity.PlaylistAudio;
import com.sonetopia.entity.User;
import com.sonetopia.model.PlaylistAudioResponse;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.repository.PlaylistAudioRepository;
import com.sonetopia.repository.PlaylistRepository;

@Service 
public class PlaylistAudioService {

  @Autowired 
  private AudioRepository audioRepository;

  @Autowired 
  private PlaylistRepository playlistRepository;

  @Autowired
  private PlaylistAudioRepository playlistAudioRepository;

  @Transactional 
  public void add(UUID playlistId, UUID audioId, User user) {
    Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
      .orElseThrow(() -> new IllegalArgumentException("Playlist not found or does not belong to the user"));
    
    Audio audio = audioRepository.findByIdAndUser(audioId, user)
      .orElseThrow(() -> new IllegalArgumentException("Audio not found or does not belong to the user"));

    int nextPosition = playlistAudioRepository
      .findFirstByPlaylist_IdOrderByPositionDesc(playlist.getId())
      .map(item -> item.getPosition() + 1)
      .orElse(0);

    PlaylistAudio playlistAudio = new PlaylistAudio();
    playlistAudio.setPlaylist(playlist);
    playlistAudio.setAudio(audio);
    playlistAudio.setPosition(nextPosition);
    
    playlistAudioRepository.save(playlistAudio);
  }

  @Transactional
  public void update(UUID playlistId, UUID audioId, int newPosition, User user) {
    Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
      .orElseThrow(() -> new IllegalArgumentException(
          "Playlist not found or does not belong to the user"
      ));

    Audio audio = audioRepository.findByIdAndUser(audioId, user)
      .orElseThrow(() -> new IllegalArgumentException(
          "Audio not found or does not belong to the user"
      ));

    PlaylistAudio playlistAudio = playlistAudioRepository
      .findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio.getId())
      .orElseThrow(() -> new IllegalArgumentException(
          "Audio not found in the specified playlist"
      ));

    List<PlaylistAudio> playlistAudioList = playlistAudioRepository.findByPlaylist_Id(playlistId);

    int oldPosition = playlistAudio.getPosition();
    int maxPosition = playlistAudioList.size() - 1;

    if (newPosition < 0 || newPosition > maxPosition) {
      throw new IllegalArgumentException("Invalid playlist position");
    }

    if (newPosition < oldPosition) {
      playlistAudioList.stream()
        .filter(pa -> !pa.getId().equals(playlistAudio.getId()))
        .filter(pa -> pa.getPosition() >= newPosition && pa.getPosition() < oldPosition)
        .forEach(pa -> pa.setPosition(pa.getPosition() + 1));

    } else if (newPosition > oldPosition) {
      playlistAudioList.stream()
        .filter(pa -> !pa.getId().equals(playlistAudio.getId()))
        .filter(pa -> pa.getPosition() > oldPosition && pa.getPosition() <= newPosition)
        .forEach(pa -> pa.setPosition(pa.getPosition() - 1));
    }

    playlistAudio.setPosition(newPosition);
    playlistAudioRepository.save(playlistAudio);
  }

  @Transactional
  public void remove(UUID playlistId, UUID audioId, User user) {
    Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
      .orElseThrow(() -> new IllegalArgumentException(
          "Playlist not found or does not belong to the user"
      ));

    Audio audio = audioRepository.findByIdAndUser(audioId, user)
      .orElseThrow(() -> new IllegalArgumentException(
          "Audio not found or does not belong to the user"
      ));

    PlaylistAudio playlistAudio = playlistAudioRepository
      .findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio.getId())
      .orElseThrow(() -> new IllegalArgumentException(
          "Audio not found in the specified playlist"
      ));

    int deletedPosition = playlistAudio.getPosition();
    playlistAudioRepository.delete(playlistAudio);

    List<PlaylistAudio> playlistAudioList = playlistAudioRepository.findByPlaylist_Id(playlistId);
    playlistAudioList.stream()
      .filter(pa -> pa.getPosition() > deletedPosition)
      .forEach(pa -> pa.setPosition(pa.getPosition() - 1));
  }

  @Transactional(readOnly = true)
  public List<PlaylistAudioResponse> list(UUID playlistId, User user) {
    Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
      .orElseThrow(() -> new IllegalArgumentException(
          "Playlist not found or does not belong to the user"
      ));

    return playlistAudioRepository.findByPlaylist_IdOrderByPositionAsc(playlist.getId())
            .stream()
            .map(pa -> new PlaylistAudioResponse(
                pa.getAudio().getId(),
                pa.getPosition()
            ))
            .toList();
  }
}
