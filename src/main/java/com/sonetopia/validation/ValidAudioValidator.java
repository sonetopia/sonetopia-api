package com.sonetopia.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidAudioValidator implements ConstraintValidator<ValidAudio, MultipartFile> {

  private long maxSize;
  private Set<String> allowedMimeTypes;
  private Set<String> allowedExtensions;

  @Override
  public void initialize(ValidAudio annotation) {
    this.maxSize = annotation.maxSize();
    this.allowedMimeTypes = Arrays.stream(annotation.allowedMimeTypes()).map(type -> type.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    this.allowedExtensions = Arrays.stream(annotation.allowedExtensions()).map(ext -> ext.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }

  @Override
  public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
    if (file == null) {
      return true;
    }

    if (file.isEmpty()) {
      return reject(context, "Audio file cannot be empty");
    }

    if (file.getSize() > maxSize) {
      return reject(context, "Audio file must not exceed " + (maxSize / 1024 / 1024) + " MiB");
    }

    String mimeType = file.getContentType();

    if (mimeType == null || !allowedMimeTypes.contains(mimeType.toLowerCase(Locale.ROOT))) {
      return reject(context, "Unsupported audio MIME type");
    }

    String extension = getExtension(file.getOriginalFilename());

    if (extension == null || !allowedExtensions.contains(extension)) {
      return reject(context, "Unsupported audio file extension");
    }

    return true;
  }

  private boolean reject(ConstraintValidatorContext context, String message) {
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();

    return false;
  }

  private String getExtension(String filename) {

    if (filename == null || !filename.contains(".")) {
      return null;
    }

    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }
}