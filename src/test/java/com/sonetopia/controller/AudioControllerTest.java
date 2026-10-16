package com.sonetopia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.User;
import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.WebResponse;
import com.sonetopia.model.AudioResponse;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.repository.UserRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/sonetopia",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.show-sql=true",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.storage.audio-path=C:/projects/sonetopia",
    "spring.storage.signed-url-secret=sonetopia-very-secret-key-from-microsoft",
    "spring.security.jwt.secret=sonetopia-jwt-secret-key-for-testing",
    "spring.security.jwt.expiration=86400000",
    "spring.servlet.multipart.max-file-size=50MB",
    "spring.servlet.multipart.max-request-size=55MB",
    "spring.cors.allowed-origins=http://localhost:5173,http://localhost:5174"
  }
)
@AutoConfigureMockMvc 
public class AudioControllerTest {

  @Autowired 
  private MockMvc mockMvc;

  @Autowired 
  private AudioRepository audioRepository;

  @Autowired 
  private UserRepository userRepository;

  @Autowired 
  private ObjectMapper objectMapper;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String accessToken;

  @BeforeEach 
  void setUp() throws Exception {
    audioRepository.deleteAll();
    userRepository.deleteAll();

    User user = new User();
    user.setUsername("test");
    user.setEmail("test@gmail.com");
    user.setPassword(passwordEncoder.encode("test"));
    user.setStorageUsed(0L);
    user.setIsAdmin(true);
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());

    userRepository.save(user);

    accessToken = obtainAccessToken();
  }

  @Test 
  void testCreateAudioSuccess() throws Exception {
    MockMultipartFile audioFile;
    try (InputStream inputStream = new ClassPathResource(
      "audio/sempurna.mp3"
    ).getInputStream()) {
      audioFile = new MockMultipartFile(
        "audioFile",
        "sempurna.mp3",
        "audio/mpeg",
        inputStream
      );
    }

    MockMultipartFile imageCover = new MockMultipartFile(
        "imageCover",
        "test-cover.jpg",
        "image/jpeg",
        "Test image content".getBytes()
    );

    String responseJson = perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .file(imageCover)
        .param("title", "Test Audio")
        .param("author", "Test Author")
        .param("album", "Test Album")
        .param("year", "2023")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    WebResponse<AudioResponse> webResponse = objectMapper.readValue(responseJson, new TypeReference<WebResponse<AudioResponse>>() {});

    assertNotNull(webResponse);
    assertNotNull(webResponse.getData());
    assertEquals("Test Audio", webResponse.getData().getTitle());
    assertEquals("Test Author", webResponse.getData().getAuthor());
    assertEquals("Test Album", webResponse.getData().getAlbum());
    assertEquals((short)2023, webResponse.getData().getYear());
    assertNotNull(webResponse.getData().getDuration());

    List<Audio> audio = audioRepository.findAll();
    assertEquals(1, audio.size());
    Audio savedAudio = audio.get(0);
    assertEquals("Test Audio", savedAudio.getTitle());
    assertEquals("Test Author", savedAudio.getAuthor());
    assertEquals("Test Album", savedAudio.getAlbum());
    assertEquals((short)2023, savedAudio.getYear());
    assertNotNull(savedAudio.getDuration());
  }

  @Test 
  void testCreateAudioValidationError() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
        "audioFile",
        "test-audio.mp3",
        "audio/mpeg",
        "Test audio content".getBytes()
    );

    perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .param("title", "") // Empty title to trigger validation error
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNull(webResponse.getData());
        assertNotNull(webResponse.getErrors());
        assertTrue(webResponse.getErrors().contains("Title is required"));
      });
  }

  @Test
  void testCreateAudioInvalidMimeType() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
        "audioFile",
        "test-audio.txt",
        "text/plain", // invalid MIME type
        "Test audio content".getBytes()
    );

    perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .param("title", "Test Audio")
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNull(webResponse.getData());
        assertNotNull(webResponse.getErrors());
        assertTrue(webResponse.getErrors().contains("Unsupported audio MIME type"));
      });
  }

  @Test
  void testCreateAudioInvalidExtension() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
      "audioFile",
      "test-audio.txt", // invalid extension
      "audio/mpeg",
      "Test audio content".getBytes()
    );

    perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .param("title", "Test Audio")
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNull(webResponse.getData());
        assertNotNull(webResponse.getErrors());
        assertTrue(webResponse.getErrors().contains("Unsupported audio file extension"));
      });
  }

  @Test
  void testCreateAudioInvalidImageCover() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
      "audioFile",
      "test-audio.mp3",
      "audio/mpeg",
      "Test audio content".getBytes()
    );

    MockMultipartFile imageCover = new MockMultipartFile(
      "imageCover",
      "test-image.txt",
      "text/plain",
      "Test image content".getBytes()
    );

    perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .file(imageCover)
        .param("title", "Test Audio")
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNull(webResponse.getData());
        assertNotNull(webResponse.getErrors());
        assertTrue(webResponse.getErrors().contains("Unsupported image cover MIME type"));
      });
  }

  @Test
  void testGetAudioSuccess() throws Exception {
    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(userRepository.findByUsername("test").orElseThrow());
    audio.setTitle("Test Audio");
    audio.setStorageKey("random-string");
    audio.setMimeType("audio/mpeg");
    audio.setFileSize(1024L);
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio);

    perform(get("/api/v1/audio/" + audio.getId()))
      .andExpectAll(
        status().isOk()
      ).andDo(getResult -> {
        WebResponse<AudioResponse> getWebResponse = objectMapper.readValue(getResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(getWebResponse);
        assertNotNull(getWebResponse.getData());
        assertEquals(audio.getId(), getWebResponse.getData().getId());
        assertEquals("Test Audio", getWebResponse.getData().getTitle());
        assertNotNull(getWebResponse.getData().getSignedUrl());
      });
  }

  @Test
  void testGetAudioNotFound() throws Exception {
    UUID nonExistentAudioId = UUID.randomUUID();

    perform(get("/api/v1/audio/" + nonExistentAudioId))
      .andExpectAll(
        status().isNotFound()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNull(webResponse.getData());
        assertNotNull(webResponse.getErrors());
        assertTrue(webResponse.getErrors().contains("Audio not found"));
      });
  }

  @Test
  void testUpdateAudioSuccess() throws Exception {
    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(userRepository.findByUsername("test").orElseThrow());
    audio.setTitle("Test Audio");
    audio.setStorageKey("random-string");
    audio.setMimeType("audio/mpeg");
    audio.setFileSize(1024L);
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio);

    perform(
      multipart("/api/v1/audio/" + audio.getId())
        .param("title", "Updated Test Audio")
        .param("author", "Updated Test Author")
        .param("album", "Updated Test Album")
        .param("year", "2024")
        .with(request -> { request.setMethod("PUT"); return request; }) // Override method to PUT
      ).andExpectAll(
        status().isOk()
      ).andDo(updateResult -> {
        WebResponse<AudioResponse> updateWebResponse = objectMapper.readValue(updateResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(updateWebResponse);
        assertNotNull(updateWebResponse.getData());
        assertEquals(audio.getId(), updateWebResponse.getData().getId());
        assertEquals("Updated Test Audio", updateWebResponse.getData().getTitle());
        assertEquals("Updated Test Author", updateWebResponse.getData().getAuthor());
        assertEquals("Updated Test Album", updateWebResponse.getData().getAlbum());
        assertEquals((short)2024, updateWebResponse.getData().getYear());
      });
  }

  @Test
  void testUpdateAudioWithUpdatingImageCoverSuccess() throws Exception {
    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(userRepository.findByUsername("test").orElseThrow());
    audio.setTitle("Test Audio");
    audio.setStorageKey("random-string");
    audio.setMimeType("audio/mpeg");
    audio.setFileSize(1024L);
    audio.setCoverVersion(1);
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio);

    MockMultipartFile newImageCover = new MockMultipartFile(
      "imageCover",
      "new-test-cover.jpg",
      "image/jpeg",
      "New test image content".getBytes()
    );

    perform(
      multipart("/api/v1/audio/" + audio.getId())
        .file(newImageCover)
        .param("title", "Updated Test Audio with New Cover")
        .with(request -> { request.setMethod("PUT"); return request; }) // Override method to PUT
      ).andExpectAll(
        status().isOk()
      ).andDo(updateResult -> {
        WebResponse<AudioResponse> updateWebResponse = objectMapper.readValue(updateResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(updateWebResponse);
        assertNotNull(updateWebResponse.getData());
        assertEquals(audio.getId(), updateWebResponse.getData().getId());
        assertEquals("Updated Test Audio with New Cover", updateWebResponse.getData().getTitle());
        assertEquals(2, updateWebResponse.getData().getCoverVersion());
      });
  }

  @Test
  void testUpdateAudioEmptyTitleValidationError() throws Exception {
    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(userRepository.findByUsername("test").orElseThrow());
    audio.setTitle("Test Audio");
    audio.setStorageKey("random-string");
    audio.setMimeType("audio/mpeg");
    audio.setFileSize(1024L);
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio);

    perform(
      multipart("/api/v1/audio/" + audio.getId())
        .param("title", "") // Empty title to trigger validation error
        .with(request -> { request.setMethod("PUT"); return request; }) // Override method to PUT
      ).andExpectAll(
        status().isBadRequest()
      ).andDo(updateResult -> {
        WebResponse<AudioResponse> updateWebResponse = objectMapper.readValue(updateResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(updateWebResponse);
        assertNull(updateWebResponse.getData());
        assertNotNull(updateWebResponse.getErrors());
        assertTrue(updateWebResponse.getErrors().contains("Title is required"));
      });
  }

  @Test
  void testDeleteAudioSuccess() throws Exception {
    Audio audio = new Audio();
    audio.setId(UUID.randomUUID());
    audio.setUser(userRepository.findByUsername("test").orElseThrow());
    audio.setTitle("Test Audio");
    audio.setStorageKey("random-string");
    audio.setMimeType("audio/mpeg");
    audio.setFileSize(1024L);
    audio.setCreatedAt(LocalDateTime.now());
    audio.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio);

    perform(delete("/api/v1/audio/" + audio.getId()))
      .andExpectAll(
        status().isOk()
      ).andDo(deleteResult -> {
        WebResponse<String> deleteWebResponse = objectMapper.readValue(deleteResult.getResponse().getContentAsString(), new TypeReference<WebResponse<String>>() {});
    
        assertNotNull(deleteWebResponse);
        assertNotNull(deleteWebResponse.getData());
        assertEquals("Audio deleted successfully", deleteWebResponse.getData());

        // Verify that the audio is deleted from the repository
        assertFalse(audioRepository.findById(audio.getId()).isPresent());
      });
  }

  @Test
  void testListAudioSuccess() throws Exception {
    
    Audio audio1 = new Audio();
    audio1.setId(UUID.randomUUID());
    audio1.setUser(userRepository.findByUsername("test").orElseThrow());
    audio1.setTitle("Test Audio 1");
    audio1.setStorageKey("random-string");
    audio1.setMimeType("audio/mpeg");
    audio1.setFileSize(1024L);
    audio1.setCreatedAt(LocalDateTime.now());
    audio1.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio1);

    Audio audio2 = new Audio();
    audio2.setId(UUID.randomUUID());
    audio2.setUser(userRepository.findByUsername("test").orElseThrow());
    audio2.setTitle("Test Audio 2");
    audio2.setStorageKey("random-string-2");
    audio2.setMimeType("audio/mpeg");
    audio2.setFileSize(1024L);
    audio2.setCreatedAt(LocalDateTime.now());
    audio2.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio2);

    // List all audios
    perform(get("/api/v1/audio"))
      .andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<List<AudioResponse>> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<List<AudioResponse>>>() {});
    
        assertNotNull(webResponse);
        assertNotNull(webResponse.getData());
        assertEquals(2, webResponse.getData().size());

        AudioResponse audioResponse1 = webResponse.getData().get(0);
        AudioResponse audioResponse2 = webResponse.getData().get(1);

        assertEquals("Test Audio 1", audioResponse1.getTitle());
        assertEquals("Test Audio 2", audioResponse2.getTitle());
      });
  }

  private String obtainAccessToken() throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setUsername("test");
    loginRequest.setPassword("test");

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

  private ResultActions perform(MockMultipartHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }
}