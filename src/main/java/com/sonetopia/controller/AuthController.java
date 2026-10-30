package com.sonetopia.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.AppleLoginRequest;
import com.sonetopia.model.GithubLoginRequest;
import com.sonetopia.model.GoogleLoginRequest;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.model.ResendVerificationRequest;
import com.sonetopia.model.WebResponse;
import com.sonetopia.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String REFRESH_COOKIE_NAME = "refresh_token";
  private static final String REFRESH_COOKIE_PATH = "/api/auth";

  @Autowired
  private AuthService authService;

  @Value("${app.auth.refresh-cookie-same-site:Lax}")
  private String refreshCookieSameSite;

  @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> register(
      @RequestBody RegisterRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    return createAuthResponse(authService.register(request), httpRequest, httpResponse);
  }

  @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> login(
      @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    return createAuthResponse(authService.login(request), httpRequest, httpResponse);
  }

  @PostMapping(path = "/google", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> googleLogin(
      @RequestBody GoogleLoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    return createAuthResponse(authService.loginWithGoogle(request), httpRequest, httpResponse);
  }

  @PostMapping(path = "/github", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> githubLogin(
      @RequestBody GithubLoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    return createAuthResponse(authService.loginWithGithub(request), httpRequest, httpResponse);
  }

  @PostMapping(path = "/apple", consumes = "application/json", produces = "application/json")
  public WebResponse<AuthResponse> appleLogin(
      @RequestBody AppleLoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    return createAuthResponse(authService.loginWithApple(request), httpRequest, httpResponse);
  }

  @PostMapping(path = "/refresh", produces = "application/json")
  public WebResponse<AuthResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    return createAuthResponse(authService.refresh(refreshToken), httpRequest, httpResponse);
  }

  @PostMapping(path = "/logout", produces = "application/json")
  public WebResponse<String> logout(
      @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    authService.logout(refreshToken);
    clearRefreshCookie(httpRequest, httpResponse);
    return WebResponse.<String>builder().data("Logged out successfully").build();
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

  private WebResponse<AuthResponse> createAuthResponse(
      AuthResponse authResponse,
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, authResponse.getRefreshToken())
        .httpOnly(true)
        .secure(request.isSecure())
        .sameSite(refreshCookieSameSite)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(Duration.ofSeconds(authResponse.getRefreshTokenExpiresIn()))
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    return WebResponse.<AuthResponse>builder().data(authResponse).build();
  }

  private void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(request.isSecure())
        .sameSite(refreshCookieSameSite)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(Duration.ZERO)
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
