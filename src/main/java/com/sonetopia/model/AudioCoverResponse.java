package com.sonetopia.model;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
public class AudioCoverResponse {

  private UUID id;

  private String mimeType;

  private Long fileSize;

  private Integer coverVersion;

  private String signedUrl;
}
