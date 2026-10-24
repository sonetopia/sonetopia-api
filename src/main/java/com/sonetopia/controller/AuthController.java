package com.sonetopia.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.GithubLoginRequest;
import com.sonetopia.model.GoogleLoginRequest;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.model.ResendVerificationRequest;
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

  @PostMapping(path = "/google", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> googleLogin(@RequestBody GoogleLoginRequest request) {
    AuthResponse authResponse = authService.loginWithGoogle(request);
    return WebResponse.<AuthResponse>builder().data(authResponse).build();
  }

  @PostMapping(path = "/github", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> githubLogin(@RequestBody GithubLoginRequest request) {
    AuthResponse authResponse = authService.loginWithGithub(request);
    return WebResponse.<AuthResponse>builder().data(authResponse).build();
  }

  @GetMapping(path = "/verify-email", produces = "application/json")
  public WebResponse<String> verifyEmail(@RequestParam("token") String token) {
    authService.verifyEmail(token);
    return WebResponse.<String>builder().data("Email verified successfully").build();
  }

  @PostMapping(
    path = "/resend-verification",
    consumes = "application/json",
    produces = "application/json"
  )
  public WebResponse<String> resendVerification(@RequestBody ResendVerificationRequest request) {
    authService.resendVerificationEmail(request);
    return WebResponse.<String>builder()
      .data("If the email is registered and unverified, a verification email has been sent.")
      .build();
  }
}
