package com.sonetopia.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.model.WebResponse;
import com.sonetopia.entity.User;
import com.sonetopia.model.PlaylistResponse;
import com.sonetopia.model.CreatePlaylistRequest;
import com.sonetopia.service.PlaylistService;
import com.sonetopia.repository.UserRepository;

@RestController 
public class PlaylistController {

  @Autowired 
  private PlaylistService playlistService;

  @Autowired
  private UserRepository userRepository;
  
  @PostMapping(path= "/api/v1/playlists", consumes = "application/json", produces = "application/json")
  public WebResponse<PlaylistResponse> create(@RequestBody CreatePlaylistRequest request) throws IOException  {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    PlaylistResponse playlistResponse = playlistService.create(request, user);
    return WebResponse.<PlaylistResponse>builder().data(playlistResponse).build();
  }
  
  @GetMapping (path = "/api/v1/playlists/{playlistId}", produces = "application/json")
  public WebResponse<PlaylistResponse> get(@PathVariable("playlistId") UUID playlistId) {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    PlaylistResponse playlistResponse = playlistService.get(playlistId, user);
    return WebResponse.<PlaylistResponse>builder().data(playlistResponse).build();
  }

  @PutMapping(path = "/api/v1/playlists/{playlistId}", consumes = "application/json", produces = "application/json")
  public WebResponse<PlaylistResponse> update(@PathVariable("playlistId") UUID playlistId, @RequestBody CreatePlaylistRequest request) {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    PlaylistResponse playlistResponse = playlistService.update(playlistId, request, user);
    return WebResponse.<PlaylistResponse>builder().data(playlistResponse).build();
  }

  @DeleteMapping(path = "/api/v1/playlists/{playlistId}", produces = "application/json")
  public WebResponse<UUID> delete(@PathVariable("playlistId") UUID playlistId) {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    playlistService.delete(playlistId, user);
    return WebResponse.<UUID>builder().data(playlistId).build();
  }
}
