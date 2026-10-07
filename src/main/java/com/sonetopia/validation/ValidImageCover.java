package com.sonetopia.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidImageCoverValidator.class)
@Target({
  ElementType.FIELD,
  ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidImageCover {

  String message() default "Invalid audio file";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  long maxSize() default 50 * 1024 * 1024;

  String[] allowedMimeTypes() default {
    "image/jpeg",
    "image/png",
    "image/gif"
  };

  String[] allowedExtensions() default {
    "jpg",
    "png",
    "gif"
  };
}