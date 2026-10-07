package com.sonetopia.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.model.WebResponse;
import com.sonetopia.entity.User;
import com.sonetopia.model.AudioResponse;
import com.sonetopia.model.CreateAudioRequest;
import com.sonetopia.service.AudioService;
import com.sonetopia.repository.UserRepository;

@RestController 
public class AudioController {

  @Autowired 
  private AudioService audioService;

  @Autowired
  private UserRepository userRepository;
  
  @PostMapping(path= "/api/v1/audio", consumes = "multipart/form-data", produces = "application/json")
  public WebResponse<AudioResponse> create(@ModelAttribute CreateAudioRequest request) throws IOException  {
    User user = userRepository.findByUsername("test")
    .orElseThrow();
    AudioResponse audioResponse = audioService.create(request, user);
    return WebResponse.<AudioResponse>builder().data(audioResponse).build();
  }
}
