package com.sonetopia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import jakarta.servlet.DispatcherType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.sonetopia.entity.User;
import com.sonetopia.entity.Audio;
import com.sonetopia.entity.Playlist;
import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.GithubLoginRequest;
import com.sonetopia.model.GoogleLoginRequest;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.model.ResendVerificationRequest;
import com.sonetopia.model.UpdateUserRequest;
import com.sonetopia.model.UserResponse;
import com.sonetopia.model.WebResponse;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.repository.PlaylistRepository;
import com.sonetopia.security.JwtService;
import com.sonetopia.service.GithubOAuthClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AudioRepository audioRepository;

  @Autowired
  private PlaylistRepository playlistRepository;

  @Value("${spring.storage.audio-path}")
  private String audioPath;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private JwtDecoder googleJwtDecoder;

  @MockitoBean
  private GithubOAuthClient githubOAuthClient;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    User user = new User();
    user.setUsername("test");
    user.setName("Test User");
    user.setEmail("test@gmail.com");
    user.setPassword(passwordEncoder.encode("test"));
    user.setStorageUsed(0L);
    user.setIsAdmin(false);
    user.setEmailVerifiedAt(LocalDateTime.now());
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());

    userRepository.save(user);
  }

  @Test
  void testRegisterSuccess() throws Exception {
    RegisterRequest request = RegisterRequest.builder()
        .name("New User")
        .username("newuser")
        .email("newuser@gmail.com")
        .password("secret123")
        .build();

    mockMvc.perform(post("/api/auth/register")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertNotNull(response.getData());
        assertNotNull(response.getData().getToken());
        assertEquals("Bearer", response.getData().getTokenType());
        assertEquals("New User", response.getData().getUser().getName());
        assertEquals("newuser", response.getData().getUser().getUsername());
        assertEquals("newuser@gmail.com", response.getData().getUser().getEmail());
        assertNull(response.getData().getUser().getEmailVerifiedAt());

        User savedUser = userRepository.findByUsername("newuser").orElseThrow();
        assertNull(savedUser.getEmailVerifiedAt());
        assertNotEquals("secret123", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("secret123", savedUser.getPassword()));
      });
  }

  @Test
  void testRegisterValidationError() throws Exception {
    RegisterRequest request = RegisterRequest.builder()
        .name("")
        .username("")
        .email("not-an-email")
        .password("123")
        .build();

    mockMvc.perform(post("/api/auth/register")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertNull(response.getData());
        assertNotNull(response.getErrors());
      });
  }

  @Test
  void testRegisterDuplicateUsername() throws Exception {
    RegisterRequest request = RegisterRequest.builder()
        .name("Duplicate User")
        .username("test")
        .email("another@gmail.com")
        .password("secret123")
        .build();

    mockMvc.perform(post("/api/auth/register")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isConflict()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertNull(response.getData());
        assertTrue(response.getErrors().contains("Username already exists"));
      });
  }

  @Test
  void testLoginSuccess() throws Exception {
    LoginRequest request = new LoginRequest("test", "test");

    mockMvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertNotNull(response.getData());
        assertNotNull(response.getData().getToken());
        assertEquals("test", response.getData().getUser().getUsername());
      });
  }

  @Test
  void testLoginInvalidPassword() throws Exception {
    LoginRequest request = new LoginRequest("test", "wrong-password");

    mockMvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isUnauthorized()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertNull(response.getData());
        assertTrue(response.getErrors().contains("Invalid username or password"));
      });
  }

  @Test
  void testLoginUnknownUsername() throws Exception {
    LoginRequest request = new LoginRequest("unknown", "test");

    mockMvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isUnauthorized()
      );
  }

  @Test
  void testGoogleLoginRegistersNewUser() throws Exception {
    when(googleJwtDecoder.decode("new-user-token"))
      .thenReturn(googleToken("google-sub-new", "google@gmail.com", true));

    GoogleLoginRequest request = GoogleLoginRequest.builder()
        .idToken("new-user-token")
        .build();

    mockMvc.perform(post("/api/auth/google")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertNotNull(response.getData().getToken());
        assertEquals("google@gmail.com", response.getData().getUser().getEmail());
        assertNotNull(response.getData().getUser().getEmailVerifiedAt());

        User savedUser = userRepository.findByEmail("google@gmail.com").orElseThrow();
        assertEquals("google-sub-new", savedUser.getGoogleId());
        assertNull(savedUser.getPassword());
      });
  }

  @Test
  void testGoogleLoginLinksExistingEmail() throws Exception {
    User existingUser = userRepository.findByUsername("test").orElseThrow();

    when(googleJwtDecoder.decode("link-user-token"))
      .thenReturn(googleToken("google-sub-link", "test@gmail.com", true));

    GoogleLoginRequest request = GoogleLoginRequest.builder()
        .idToken("link-user-token")
        .build();

    mockMvc.perform(post("/api/auth/google")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertEquals(existingUser.getId(), response.getData().getUser().getId());

        User linkedUser = userRepository.findByUsername("test").orElseThrow();
        assertEquals("google-sub-link", linkedUser.getGoogleId());
      });
  }

  @Test
  void testGoogleLoginReusesGoogleUser() throws Exception {
    when(googleJwtDecoder.decode("reuse-user-token"))
      .thenReturn(googleToken("google-sub-reuse", "reuse@gmail.com", true));

    GoogleLoginRequest request = GoogleLoginRequest.builder()
        .idToken("reuse-user-token")
        .build();

    for (int i = 0; i < 2; i++) {
      mockMvc.perform(post("/api/auth/google")
          .contentType("application/json")
          .content(objectMapper.writeValueAsString(request))
        ).andExpectAll(
          status().isOk()
        );
    }

    assertEquals(2L, userRepository.count());
  }

  @Test
  void testGoogleLoginRejectsInvalidToken() throws Exception {
    when(googleJwtDecoder.decode("invalid-google-token"))
      .thenThrow(new JwtException("invalid"));

    GoogleLoginRequest request = GoogleLoginRequest.builder()
        .idToken("invalid-google-token")
        .build();

    mockMvc.perform(post("/api/auth/google")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isUnauthorized()
      );
  }

  @Test
  void testGoogleLoginRejectsUnverifiedEmail() throws Exception {
    when(googleJwtDecoder.decode("unverified-google-token"))
      .thenReturn(googleToken("google-sub-unverified", "unverified@gmail.com", false));

    GoogleLoginRequest request = GoogleLoginRequest.builder()
        .idToken("unverified-google-token")
        .build();

    mockMvc.perform(post("/api/auth/google")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isUnauthorized()
      );

    assertTrue(userRepository.findByEmail("unverified@gmail.com").isEmpty());
  }

  @Test
  void testGithubLoginRegistersNewUser() throws Exception {
    when(githubOAuthClient.fetchUser("github-code"))
      .thenReturn(new GithubOAuthClient.GithubUser("github-123", "github@gmail.com", "GitHub User"));

    GithubLoginRequest request = GithubLoginRequest.builder()
        .code("github-code")
        .build();

    mockMvc.perform(post("/api/auth/github")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertNotNull(response.getData().getToken());
        assertEquals("github@gmail.com", response.getData().getUser().getEmail());
        assertNotNull(response.getData().getUser().getEmailVerifiedAt());

        User savedUser = userRepository.findByEmail("github@gmail.com").orElseThrow();
        assertEquals("github-123", savedUser.getGithubId());
        assertNull(savedUser.getPassword());
      });
  }

  @Test
  void testGithubLoginLinksExistingEmail() throws Exception {
    User existingUser = userRepository.findByUsername("test").orElseThrow();

    when(githubOAuthClient.fetchUser("link-github-code"))
      .thenReturn(new GithubOAuthClient.GithubUser("github-456", "test@gmail.com", "Test User"));

    GithubLoginRequest request = GithubLoginRequest.builder()
        .code("link-github-code")
        .build();

    mockMvc.perform(post("/api/auth/github")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AuthResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<AuthResponse>>() {}
        );

        assertEquals(existingUser.getId(), response.getData().getUser().getId());

        User linkedUser = userRepository.findByUsername("test").orElseThrow();
        assertEquals("github-456", linkedUser.getGithubId());
      });
  }

  @Test
  void testGithubLoginRejectsInvalidCode() throws Exception {
    when(githubOAuthClient.fetchUser("bad-github-code"))
      .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid GitHub authorization code"));

    GithubLoginRequest request = GithubLoginRequest.builder()
        .code("bad-github-code")
        .build();

    mockMvc.perform(post("/api/auth/github")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isUnauthorized()
      );
  }

  @Test
  void testGithubLoginValidationError() throws Exception {
    GithubLoginRequest request = GithubLoginRequest.builder()
        .code("")
        .build();

    mockMvc.perform(post("/api/auth/github")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isBadRequest()
      );
  }

  @Test
  void testGetCurrentUserSuccess() throws Exception {
    String accessToken = login("test", "test");

    mockMvc.perform(get("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertNotNull(response.getData());
        assertEquals("test", response.getData().getUsername());
        assertEquals("test@gmail.com", response.getData().getEmail());
      });
  }

  @Test
  void testGetCurrentUserUnauthorized() throws Exception {
    mockMvc.perform(get("/api/me"))
      .andExpectAll(
        status().isUnauthorized()
      );
  }

  @Test
  void testUpdateCurrentUserSuccess() throws Exception {
    String accessToken = login("test", "test");

    UpdateUserRequest request = UpdateUserRequest.builder()
        .name("Updated User")
        .username("updated")
        .password("newpassword")
        .currentPassword("test")
        .build();

    mockMvc.perform(put("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertNotNull(response.getData());
        assertEquals("Updated User", response.getData().getName());
        assertEquals("updated", response.getData().getUsername());
        assertEquals("test@gmail.com", response.getData().getEmail());

        User updatedUser = userRepository.findByUsername("updated").orElseThrow();
        assertTrue(passwordEncoder.matches("newpassword", updatedUser.getPassword()));
      });
  }

  @Test
  void testUpdateCurrentUserWithoutPasswordSuccess() throws Exception {
    String accessToken = login("test", "test");

    UpdateUserRequest request = UpdateUserRequest.builder()
        .name("Updated User")
        .username("updated")
        .build();

    mockMvc.perform(put("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertNotNull(response.getData());
        assertEquals("Updated User", response.getData().getName());
        assertEquals("updated", response.getData().getUsername());
        assertEquals("test@gmail.com", response.getData().getEmail());
      });

    User updatedUser = userRepository.findByUsername("updated").orElseThrow();
    assertTrue(passwordEncoder.matches("test", updatedUser.getPassword()));
  }

  @Test
  void testUpdateCurrentUserPasswordRequiresCurrentPassword() throws Exception {
    String accessToken = login("test", "test");

    UpdateUserRequest request = UpdateUserRequest.builder()
        .name("Test User")
        .username("test")
        .password("newpassword")
        .build();

    mockMvc.perform(put("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertNull(response.getData());
        assertTrue(response.getErrors().contains("Current password is required"));
      });

    User user = userRepository.findByUsername("test").orElseThrow();
    assertTrue(passwordEncoder.matches("test", user.getPassword()));
  }

  @Test
  void testUpdateCurrentUserPasswordRejectsWrongCurrentPassword() throws Exception {
    String accessToken = login("test", "test");

    UpdateUserRequest request = UpdateUserRequest.builder()
        .name("Test User")
        .username("test")
        .password("newpassword")
        .currentPassword("wrong-password")
        .build();

    mockMvc.perform(put("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertNull(response.getData());
        assertTrue(response.getErrors().contains("Current password is incorrect"));
      });

    User user = userRepository.findByUsername("test").orElseThrow();
    assertTrue(passwordEncoder.matches("test", user.getPassword()));
  }
  @Test
  void testUpdateCurrentUserValidationError() throws Exception {
    String accessToken = login("test", "test");

    UpdateUserRequest request = UpdateUserRequest.builder()
        .name("")
        .username("")
        .build();

    mockMvc.perform(put("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertNull(response.getData());
        assertNotNull(response.getErrors());
      });
  }

  @Test
  void testUpdateCurrentUserUnauthorized() throws Exception {
    UpdateUserRequest request = UpdateUserRequest.builder()
        .name("Updated User")
        .username("updated")
        .build();

    mockMvc.perform(put("/api/me")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isUnauthorized()
      );
  }

  @Test
  void testUpdateCurrentUserIgnoresEmail() throws Exception {
    String accessToken = login("test", "test");

    String request = """
        {"name":"Updated User","username":"updated","email":"changed@gmail.com"}
        """;

    mockMvc.perform(put("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType("application/json")
        .content(request)
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertEquals("test@gmail.com", response.getData().getEmail());
      });
  }

  @Test
  void testDeleteCurrentUserRemovesResources() throws Exception {
    User user = userRepository.findByUsername("test").orElseThrow();
    String accessToken = login("test", "test");

    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(user);
    audio.setTitle("Song");
    audio.setType("audio");
    audio.setStorageKey("users/" + user.getId() + "/audio/" + audio.getId() + "/audio.mp3");
    audio.setMimeType("audio/mpeg");
    audio.setFileSize(10L);
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio);

    Playlist playlist = new Playlist();
    playlist.setUser(user);
    playlist.setTitle("Mix");
    playlist.setCreatedAt(LocalDateTime.now());
    playlist.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist);

    Path userDirectory = Paths.get(audioPath, "users", user.getId().toString());
    Path audioDirectory = userDirectory.resolve("audio").resolve(audio.getId().toString());
    Files.createDirectories(audioDirectory);
    Files.writeString(audioDirectory.resolve("audio.mp3"), "audio");

    Path tempUserDirectory = Paths.get(audioPath, "temp", user.getId().toString());
    Path tempDirectory = tempUserDirectory.resolve(UUID.randomUUID().toString());
    Files.createDirectories(tempDirectory);
    Files.writeString(tempDirectory.resolve("temp.mp3"), "audio");

    mockMvc.perform(delete("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
      ).andExpectAll(
        status().isOk()
      );

    assertTrue(userRepository.findById(user.getId()).isEmpty());
    assertTrue(audioRepository.findById(audio.getId()).isEmpty());
    assertTrue(playlistRepository.findById(playlist.getId()).isEmpty());
    assertFalse(Files.exists(userDirectory));
    assertFalse(Files.exists(tempUserDirectory));
  }

  @Test
  void testDeleteCurrentUserUnauthorized() throws Exception {
    mockMvc.perform(delete("/api/me"))
      .andExpectAll(
        status().isUnauthorized()
      );
  }

  @Test
  void testVerifyEmailSuccess() throws Exception {
    User user = createUnverifiedUser("unverified");
    String token = jwtService.generateEmailVerificationToken(user);

    mockMvc.perform(get("/api/auth/verify-email").param("token", token))
      .andExpectAll(
        status().isOk()
      );

    User verifiedUser = userRepository.findByUsername("unverified").orElseThrow();
    assertNotNull(verifiedUser.getEmailVerifiedAt());
  }

  @Test
  void testVerifyEmailRejectsInvalidToken() throws Exception {
    mockMvc.perform(get("/api/auth/verify-email").param("token", "invalid-token"))
      .andExpectAll(
        status().isBadRequest()
      );
  }

  @Test
  void testResendVerificationForUnverifiedUser() throws Exception {
    createUnverifiedUser("unverified");

    ResendVerificationRequest request = ResendVerificationRequest.builder()
        .email("unverified@gmail.com")
        .build();

    mockMvc.perform(post("/api/auth/resend-verification")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<String>>() {}
        );

        assertNotNull(response.getData());
        assertTrue(response.getData().contains("verification email has been sent"));

        User user = userRepository.findByUsername("unverified").orElseThrow();
        assertNotNull(user.getEmailVerificationSentAt());
      });
  }

  @Test
  void testResendVerificationRespectsCooldown() throws Exception {
    User user = createUnverifiedUser("unverified");
    user.setEmailVerificationSentAt(LocalDateTime.now());
    userRepository.save(user);

    ResendVerificationRequest request = ResendVerificationRequest.builder()
        .email("unverified@gmail.com")
        .build();

    mockMvc.perform(post("/api/auth/resend-verification")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isTooManyRequests()
      ).andDo(result -> {
        WebResponse<String> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<String>>() {}
        );

        assertTrue(response.getErrors().contains("Please wait"));
      });
  }

  @Test
  void testResendVerificationDoesNotRevealUnknownEmail() throws Exception {
    ResendVerificationRequest request = ResendVerificationRequest.builder()
        .email("unknown@gmail.com")
        .build();

    mockMvc.perform(post("/api/auth/resend-verification")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isOk()
      );
  }

  @Test
  void testResendVerificationValidationError() throws Exception {
    ResendVerificationRequest request = ResendVerificationRequest.builder()
        .email("not-an-email")
        .build();

    mockMvc.perform(post("/api/auth/resend-verification")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isBadRequest()
      );
  }

  @Test
  void testUnverifiedUserCannotAccessResources() throws Exception {
    createUnverifiedUser("unverified");
    String accessToken = login("unverified", "test");

    mockMvc.perform(get("/api/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
      ).andExpectAll(
        status().isForbidden()
      ).andDo(result -> {
        WebResponse<UserResponse> response = objectMapper.readValue(
          result.getResponse().getContentAsString(),
          new TypeReference<WebResponse<UserResponse>>() {}
        );

        assertNull(response.getData());
        assertTrue(response.getErrors().contains("Email is not verified"));
      });
  }

  @Test
  void testProtectedAudioEndpointUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/audio"))
      .andExpectAll(
        status().isUnauthorized()
      );
  }

  @Test
  void testErrorDispatchIsNotUnauthorized() throws Exception {
    mockMvc.perform(get("/error").with(request -> {
        request.setDispatcherType(DispatcherType.ERROR);
        return request;
      }))
      .andExpectAll(
        status().is5xxServerError()
      );
  }

  private String login(String username, String password) throws Exception {
    LoginRequest loginRequest = new LoginRequest(username, password);

    String responseJson = mockMvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(loginRequest))
      ).andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    WebResponse<AuthResponse> response = objectMapper.readValue(responseJson, new TypeReference<WebResponse<AuthResponse>>() {});

    return response.getData().getToken();
  }

  private Jwt googleToken(String subject, String email, boolean emailVerified) {
    return Jwt.withTokenValue("google-token")
        .header("alg", "RS256")
        .subject(subject)
        .claim("email", email)
        .claim("email_verified", emailVerified)
        .claim("name", "Google User")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  private User createUnverifiedUser(String username) {
    User user = new User();
    user.setUsername(username);
    user.setName("Unverified User");
    user.setEmail(username + "@gmail.com");
    user.setPassword(passwordEncoder.encode("test"));
    user.setStorageUsed(0L);
    user.setIsAdmin(false);
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());

    return userRepository.save(user);
  }
}
