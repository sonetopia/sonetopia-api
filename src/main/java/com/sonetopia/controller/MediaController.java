package com.sonetopia.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.service.MediaService;

@RestController 
public class MediaController {

  @Autowired 
  private MediaService mediaService;

  @GetMapping(path = "/api/v1/media/audio")
  public ResponseEntity<Resource> getAudio(@RequestParam("id") UUID audioId, @RequestParam("expires") Long expires, @RequestParam("signature") String signature) {
    return mediaService.getAudio(audioId, expires, signature);
  }

  @GetMapping(path = "/api/v1/media/cover")
  public ResponseEntity<Resource> getImageCover(@RequestParam("id") UUID audioId, @RequestParam("expires") Long expires, @RequestParam("signature") String signature) {
    return mediaService.getImageCover(audioId, expires, signature);
  }
}
