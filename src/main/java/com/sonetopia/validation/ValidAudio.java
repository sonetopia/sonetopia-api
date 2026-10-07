package com.sonetopia.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidAudioValidator.class)
@Target({
  ElementType.FIELD,
  ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAudio {

  String message() default "Invalid audio file";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  long maxSize() default 50 * 1024 * 1024;

  String[] allowedMimeTypes() default {
    "audio/mpeg",
    "audio/wav",
    "audio/x-wav",
    "audio/ogg",
    "audio/mp4"
  };

  String[] allowedExtensions() default {
    "mp3",
    "wav",
    "ogg",
    "m4a"
  };
}