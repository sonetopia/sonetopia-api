package com.sonetopia.model;

import org.springframework.web.multipart.MultipartFile;

import com.sonetopia.validation.ValidAudio;
import com.sonetopia.validation.ValidImageCover;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
public class CreateAudioRequest {
  
  @NotNull(message = "Audio file is required") 
  @ValidAudio(
    maxSize = 10 * 1024 * 1024, // 10 MB
    allowedMimeTypes = {"audio/mpeg", "audio/wav", "audio/ogg"},
    allowedExtensions = {"mp3", "wav", "ogg"}
  )
  private MultipartFile audioFile;

  @ValidImageCover(
    maxSize = 5 * 1024 * 1024, // 5 MB
    allowedMimeTypes = {"image/jpeg", "image/png"},
    allowedExtensions = {"jpg", "png"}
  )
  private MultipartFile imageCover;

  @NotBlank(message = "Title is required")
  @Size(max = 255, message = "Title must be less than or equal to 255 characters")
  private String title;

  private String type;

  private String author;

  private String album;

  private Short year;
}
