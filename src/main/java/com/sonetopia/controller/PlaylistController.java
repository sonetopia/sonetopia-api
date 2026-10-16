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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.model.WebResponse;
import com.sonetopia.entity.User;
import com.sonetopia.model.PlaylistResponse;
import com.sonetopia.model.CreatePlaylistRequest;
import com.sonetopia.service.PlaylistService;

@RestController 
public class PlaylistController {

  @Autowired 
  private PlaylistService playlistService;

  @PostMapping(path= "/api/v1/playlists", consumes = "application/json", produces = "application/json")
  public WebResponse<PlaylistResponse> create(@AuthenticationPrincipal User user, @RequestBody CreatePlaylistRequest request) {
    PlaylistResponse playlistResponse = playlistService.create(request, user);
    return WebResponse.<PlaylistResponse>builder().data(playlistResponse).build();
  }
  
  @GetMapping (path = "/api/v1/playlists/{playlistId}", produces = "application/json")
  public WebResponse<PlaylistResponse> get(@AuthenticationPrincipal User user, @PathVariable("playlistId") UUID playlistId) {
    PlaylistResponse playlistResponse = playlistService.get(playlistId, user);
    return WebResponse.<PlaylistResponse>builder().data(playlistResponse).build();
  }

  @PutMapping(path = "/api/v1/playlists/{playlistId}", consumes = "application/json", produces = "application/json")
  public WebResponse<PlaylistResponse> update(@AuthenticationPrincipal User user, @PathVariable("playlistId") UUID playlistId, @RequestBody CreatePlaylistRequest request) {
    PlaylistResponse playlistResponse = playlistService.update(playlistId, request, user);
    return WebResponse.<PlaylistResponse>builder().data(playlistResponse).build();
  }

  @DeleteMapping(path = "/api/v1/playlists/{playlistId}", produces = "application/json")
  public WebResponse<UUID> delete(@AuthenticationPrincipal User user, @PathVariable("playlistId") UUID playlistId) {
    playlistService.delete(playlistId, user);
    return WebResponse.<UUID>builder().data(playlistId).build();
  }

  @GetMapping(path = "/api/v1/playlists", produces = "application/json")
  public WebResponse<List<PlaylistResponse>> list(@AuthenticationPrincipal User user) {
    List<PlaylistResponse> playlistResponses = playlistService.list(user);
    return WebResponse.<List<PlaylistResponse>>builder().data(playlistResponses).build();
  }
}
