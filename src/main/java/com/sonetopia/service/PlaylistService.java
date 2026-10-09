package com.sonetopia.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sonetopia.entity.Playlist;
import com.sonetopia.entity.User;
import com.sonetopia.model.CreatePlaylistRequest;
import com.sonetopia.model.PlaylistResponse;
import com.sonetopia.repository.PlaylistRepository;

@Service 
public class PlaylistService {

  @Autowired 
  private PlaylistRepository playlistRepository;
  
  @Autowired 
  private ValidationService validationService;

  private PlaylistResponse toPlaylistResponse(Playlist playlist) {
    return PlaylistResponse.builder()
        .id(playlist.getId())
        .title(playlist.getTitle())
        .createdAt(playlist.getCreatedAt())
        .updatedAt(playlist.getUpdatedAt())
        .build();
  }

  @Transactional 
  public PlaylistResponse create(CreatePlaylistRequest request, User user) {
    validationService.validate(request);

    Playlist playlist = new Playlist();
    playlist.setUser(user);
    playlist.setTitle(request.getTitle());
    playlist.setCreatedAt(LocalDateTime.now());
    playlist.setUpdatedAt(LocalDateTime.now());

    playlistRepository.save(playlist);

    return toPlaylistResponse(playlist);
  }

  @Transactional(readOnly = true) 
  public PlaylistResponse get(UUID playlistId, User user) {
    Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));

    return toPlaylistResponse(playlist);
  }

  @Transactional
  public PlaylistResponse update(UUID playlistId, CreatePlaylistRequest request, User user) {
    validationService.validate(request);

    Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));

    playlist.setTitle(request.getTitle());
    playlist.setUpdatedAt(LocalDateTime.now());

    playlistRepository.save(playlist);

    return toPlaylistResponse(playlist);
  }

  @Transactional
  public void delete(UUID playlistId, User user) {
    Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));

    playlistRepository.delete(playlist);
  }

  @Transactional(readOnly = true)
  public List<PlaylistResponse> list(User user) {
    List<Playlist> playlists = playlistRepository.findByUser(user);
    return playlists.stream().map(this::toPlaylistResponse).toList();
  }
}
