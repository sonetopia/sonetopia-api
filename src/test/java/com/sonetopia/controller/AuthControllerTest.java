package com.sonetopia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import jakarta.servlet.DispatcherType;

import static org.junit.jupiter.api.Assertions.*;

import com.sonetopia.entity.User;
import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.model.UpdateUserRequest;
import com.sonetopia.model.UserResponse;
import com.sonetopia.model.WebResponse;
import com.sonetopia.repository.UserRepository;
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
  private ObjectMapper objectMapper;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    User user = new User();
    user.setUsername("test");
    user.setEmail("test@gmail.com");
    user.setPassword(passwordEncoder.encode("test"));
    user.setStorageUsed(0L);
    user.setIsAdmin(false);
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());

    userRepository.save(user);
  }

  @Test
  void testRegisterSuccess() throws Exception {
    RegisterRequest request = RegisterRequest.builder()
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
        assertEquals("newuser", response.getData().getUser().getUsername());
        assertEquals("newuser@gmail.com", response.getData().getUser().getEmail());

        User savedUser = userRepository.findByUsername("newuser").orElseThrow();
        assertNotEquals("secret123", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("secret123", savedUser.getPassword()));
      });
  }

  @Test
  void testRegisterValidationError() throws Exception {
    RegisterRequest request = RegisterRequest.builder()
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
        .username("updated")
        .email("updated@gmail.com")
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
        assertEquals("updated", response.getData().getUsername());
        assertEquals("updated@gmail.com", response.getData().getEmail());

        User updatedUser = userRepository.findByUsername("updated").orElseThrow();
        assertTrue(passwordEncoder.matches("newpassword", updatedUser.getPassword()));
      });
  }

  @Test
  void testUpdateCurrentUserWithoutPasswordSuccess() throws Exception {
    String accessToken = login("test", "test");

    UpdateUserRequest request = UpdateUserRequest.builder()
        .username("updated")
        .email("updated@gmail.com")
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
        assertEquals("updated", response.getData().getUsername());
        assertEquals("updated@gmail.com", response.getData().getEmail());
      });

    User updatedUser = userRepository.findByUsername("updated").orElseThrow();
    assertTrue(passwordEncoder.matches("test", updatedUser.getPassword()));
  }

  @Test
  void testUpdateCurrentUserPasswordRequiresCurrentPassword() throws Exception {
    String accessToken = login("test", "test");

    UpdateUserRequest request = UpdateUserRequest.builder()
        .username("test")
        .email("test@gmail.com")
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
        .username("test")
        .email("test@gmail.com")
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
        .username("")
        .email("test@gmail.com")
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
        .username("updated")
        .email("updated@gmail.com")
        .build();

    mockMvc.perform(put("/api/me")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
      ).andExpectAll(
        status().isUnauthorized()
      );
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
}
