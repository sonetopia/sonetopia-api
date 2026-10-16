package com.sonetopia.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.model.WebResponse;
import com.sonetopia.entity.User;
import com.sonetopia.model.AudioCoverResponse;
import com.sonetopia.model.AudioResponse;
import com.sonetopia.model.CreateAudioRequest;
import com.sonetopia.model.UpdateAudioRequest;
import com.sonetopia.service.AudioService;

@RestController 
public class AudioController {

  @Autowired 
  private AudioService audioService;

  @PostMapping(path= "/api/v1/audio", consumes = "multipart/form-data", produces = "application/json")
  public WebResponse<AudioResponse> create(@AuthenticationPrincipal User user, @ModelAttribute CreateAudioRequest request) throws IOException  {
    AudioResponse audioResponse = audioService.create(request, user);
    return WebResponse.<AudioResponse>builder().data(audioResponse).build();
  }

  @GetMapping (path = "/api/v1/audio/{audioId}", produces = "application/json")
  public WebResponse<AudioResponse> get(@AuthenticationPrincipal User user, @PathVariable("audioId") UUID audioId) {
    AudioResponse audioResponse = audioService.get(audioId, user);
    return WebResponse.<AudioResponse>builder().data(audioResponse).build();
  }

  @PutMapping(path = "/api/v1/audio/{audioId}", consumes = "multipart/form-data", produces = "application/json")
  public WebResponse<AudioResponse> update(@AuthenticationPrincipal User user, @PathVariable("audioId") UUID audioId, @ModelAttribute UpdateAudioRequest request) throws IOException {
    AudioResponse audioResponse = audioService.update(audioId, request, user);
    return WebResponse.<AudioResponse>builder().data(audioResponse).build();
  }

  @DeleteMapping(path = "/api/v1/audio/{audioId}", produces = "application/json")
  public WebResponse<String> delete(@AuthenticationPrincipal User user, @PathVariable("audioId") UUID audioId) {
    audioService.delete(audioId, user);
    return WebResponse.<String>builder().data("Audio deleted successfully").build();
  }

  @GetMapping (path = "/api/v1/audio/{audioId}/cover", produces = "application/json")
  public WebResponse<AudioCoverResponse> getCover(@AuthenticationPrincipal User user, @PathVariable("audioId") UUID audioId) {
    AudioCoverResponse audioCoverResponse = audioService.getCover(audioId, user);
    return WebResponse.<AudioCoverResponse>builder().data(audioCoverResponse).build();
  }

  @GetMapping (path = "/api/v1/audio", produces = "application/json")
  public WebResponse<List<AudioResponse>> list(@AuthenticationPrincipal User user) {
    List<AudioResponse> audioResponses = audioService.list(user);
    return WebResponse.<List<AudioResponse>>builder().data(audioResponses).build();
  }
}
