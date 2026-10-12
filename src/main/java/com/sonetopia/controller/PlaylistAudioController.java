package com.sonetopia.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.entity.User;
import com.sonetopia.model.PlaylistAudioResponse;
import com.sonetopia.model.WebResponse;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.service.PlaylistAudioService;

@RestController 
public class PlaylistAudioController {

  @Autowired 
  private PlaylistAudioService playlistAudioService;

  @Autowired 
  private UserRepository userRepository;
  
  @PostMapping(path = "/api/v1/playlists/{playlistId}/audio/{audioId}", produces = "application/json")
  public WebResponse<String> add(@PathVariable ("playlistId") UUID playlistId, @PathVariable("audioId") UUID audioId) {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    
    playlistAudioService.add(playlistId, audioId, user);

    return WebResponse.<String>builder().data("Audio added to playlist successfully").build();
  }

  @PutMapping(path = "/api/v1/playlists/{playlistId}/audio/{audioId}/position/{newPosition}", produces = "application/json")
  public WebResponse<String> update(@PathVariable ("playlistId") UUID playlistId, @PathVariable("audioId") UUID audioId, @PathVariable("newPosition") int newPosition) {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    
    playlistAudioService.update(playlistId, audioId, newPosition, user);

    return WebResponse.<String>builder().data("Audio position updated successfully").build();
  }

  @DeleteMapping(path = "/api/v1/playlists/{playlistId}/audio/{audioId}", produces = "application/json")
  public WebResponse<String> delete(@PathVariable ("playlistId") UUID playlistId, @PathVariable("audioId") UUID audioId) {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    
    playlistAudioService.remove(playlistId, audioId, user);

    return WebResponse.<String>builder().data("Audio removed from playlist successfully").build();
  }

  @GetMapping(path = "/api/v1/playlists/{playlistId}/audio", produces = "application/json")
  public WebResponse<List<PlaylistAudioResponse>> list(@PathVariable ("playlistId") UUID playlistId) {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    
    List<PlaylistAudioResponse> playlistAudios = playlistAudioService.list(playlistId, user);

    return WebResponse.<List<PlaylistAudioResponse>>builder().data(playlistAudios).build();
  }
}
