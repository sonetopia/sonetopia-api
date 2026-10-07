package com.sonetopia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.User;
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
    "spring.servlet.multipart.max-file-size=50MB",
    "spring.servlet.multipart.max-request-size=55MB"
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

  @BeforeEach 
  void setUp() {
    audioRepository.deleteAll();
    userRepository.deleteAll();

    User user = new User();
    user.setUsername("test");
    user.setEmail("test@gmail.com");
    user.setPassword("test");
    user.setStorageUsed(0L);
    user.setIsAdmin(true);
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());

    userRepository.save(user);
  }

  @Test 
  void testCreateAudioSuccess() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
        "audioFile",
        "test-audio.mp3",
        "audio/mpeg",
        "Test audio content".getBytes()
    );

    MockMultipartFile imageCover = new MockMultipartFile(
        "imageCover",
        "test-cover.jpg",
        "image/jpeg",
        "Test image content".getBytes()
    );

    String responseJson = mockMvc.perform(
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

    List<Audio> audios = audioRepository.findAll();
    assertEquals(1, audios.size());
    Audio savedAudio = audios.get(0);
    assertEquals("Test Audio", savedAudio.getTitle());
    assertEquals("Test Author", savedAudio.getAuthor());
    assertEquals("Test Album", savedAudio.getAlbum());
    assertEquals((short)2023, savedAudio.getYear());
  }

  @Test 
  void testCreateAudioValidationError() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
        "audioFile",
        "test-audio.mp3",
        "audio/mpeg",
        "Test audio content".getBytes()
    );

    mockMvc.perform(
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

    mockMvc.perform(
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

    mockMvc.perform(
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

    mockMvc.perform(
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
}
