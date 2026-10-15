package com.sonetopia.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidImageCoverValidator implements ConstraintValidator<ValidImageCover, MultipartFile> {

  private long maxSize;
  private Set<String> allowedMimeTypes;
  private Set<String> allowedExtensions;

  @Override
  public void initialize(ValidImageCover annotation) {
    this.maxSize = annotation.maxSize();
    this.allowedMimeTypes = Arrays.stream(annotation.allowedMimeTypes()).map(type -> type.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    this.allowedExtensions = Arrays.stream(annotation.allowedExtensions()).map(ext -> ext.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }

  @Override
  public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {

    if (file == null || file.isEmpty()) {
      return true;
    }

    if (file.getSize() > maxSize) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Image cover must not exceed " + (maxSize / 1024 / 1024) + " MB").addConstraintViolation();

        return false;
    }

    String mimeType = file.getContentType();

    if (mimeType == null || !allowedMimeTypes.contains(mimeType.toLowerCase())) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("Unsupported image cover MIME type").addConstraintViolation();

      return false;
    }

    String extension = getExtension(file.getOriginalFilename());

    if (extension == null || !allowedExtensions.contains(extension)) {

      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("Unsupported image cover file extension").addConstraintViolation();

      return false;
    }

    return true;
  }

  private String getExtension(String filename) {

    if (filename == null || !filename.contains(".")) {
      return null;
    }

    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }
}
