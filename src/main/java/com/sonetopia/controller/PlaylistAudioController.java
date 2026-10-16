package com.sonetopia.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.entity.User;
import com.sonetopia.model.PlaylistAudioResponse;
import com.sonetopia.model.WebResponse;
import com.sonetopia.service.PlaylistAudioService;

@RestController 
public class PlaylistAudioController {

  @Autowired 
  private PlaylistAudioService playlistAudioService;

  @PostMapping(path = "/api/v1/playlists/{playlistId}/audio/{audioId}", produces = "application/json")
  public WebResponse<String> add(@AuthenticationPrincipal User user, @PathVariable ("playlistId") UUID playlistId, @PathVariable("audioId") UUID audioId) {
    playlistAudioService.add(playlistId, audioId, user);

    return WebResponse.<String>builder().data("Audio added to playlist successfully").build();
  }

  @PutMapping(path = "/api/v1/playlists/{playlistId}/audio/{audioId}/position/{newPosition}", produces = "application/json")
  public WebResponse<String> update(@AuthenticationPrincipal User user, @PathVariable ("playlistId") UUID playlistId, @PathVariable("audioId") UUID audioId, @PathVariable("newPosition") int newPosition) {
    playlistAudioService.update(playlistId, audioId, newPosition, user);

    return WebResponse.<String>builder().data("Audio position updated successfully").build();
  }

  @DeleteMapping(path = "/api/v1/playlists/{playlistId}/audio/{audioId}", produces = "application/json")
  public WebResponse<String> delete(@AuthenticationPrincipal User user, @PathVariable ("playlistId") UUID playlistId, @PathVariable("audioId") UUID audioId) {
    playlistAudioService.remove(playlistId, audioId, user);

    return WebResponse.<String>builder().data("Audio removed from playlist successfully").build();
  }

  @GetMapping(path = "/api/v1/playlists/{playlistId}/audio", produces = "application/json")
  public WebResponse<List<PlaylistAudioResponse>> list(@AuthenticationPrincipal User user, @PathVariable ("playlistId") UUID playlistId) {
    List<PlaylistAudioResponse> playlistAudios = playlistAudioService.list(playlistId, user);

    return WebResponse.<List<PlaylistAudioResponse>>builder().data(playlistAudios).build();
  }
}
