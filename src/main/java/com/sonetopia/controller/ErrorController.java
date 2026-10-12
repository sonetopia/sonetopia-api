package com.sonetopia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import com.sonetopia.model.WebResponse;

@RestControllerAdvice 
public class ErrorController {

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<WebResponse<String>> constraintViolationException(ConstraintViolationException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      WebResponse.<String>builder()
        .errors(exception.getMessage())
        .build()
    );
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<WebResponse<String>> responseStatusException(ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode()).body(
      WebResponse.<String>builder()
        .errors(exception.getReason())
        .build()
    );
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<WebResponse<String>> illegalArgumentException(
      IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      WebResponse.<String>builder()
        .errors(exception.getMessage())
        .build()
    );
  }
}
