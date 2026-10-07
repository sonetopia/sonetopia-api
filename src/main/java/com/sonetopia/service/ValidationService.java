package com.sonetopia.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@Service 
public class ValidationService {
    private final Validator validator;

    ValidationService(Validator validator) {
      this.validator = validator;
    }

    public void validate(Object request) {
      Set<ConstraintViolation<Object>> violations = validator.validate(request);
      if (violations.size() != 0) {
        throw new ConstraintViolationException(violations);
      }
    }
}
