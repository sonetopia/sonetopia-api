package com.sonetopia.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.model.WebResponse;
import com.sonetopia.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  @Autowired
  private AuthService authService;

  @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
    AuthResponse authResponse = authService.register(request);
    return WebResponse.<AuthResponse>builder().data(authResponse).build();
  }

  @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> login(@RequestBody LoginRequest request) {
    AuthResponse authResponse = authService.login(request);
    return WebResponse.<AuthResponse>builder().data(authResponse).build();
  }
}
