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
import java.util.UUID;

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

    List<Audio> audio = audioRepository.findAll();
    assertEquals(1, audio.size());
    Audio savedAudio = audio.get(0);
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

  @Test
  void testGetAudioSuccess() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
      "audioFile",
      "test-audio.mp3",
      "audio/mpeg",
      "Test audio content".getBytes()
    );

    mockMvc.perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .param("title", "Test Audio")
        .param("author", "Test Author")
        .param("album", "Test Album")
        .param("year", "2023")
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNotNull(webResponse.getData());
        UUID audioId = webResponse.getData().getId();
    
        mockMvc.perform(get("/api/v1/audio/" + audioId))
          .andExpectAll(
            status().isOk()
          ).andDo(getResult -> {
            WebResponse<AudioResponse> getWebResponse = objectMapper.readValue(getResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
        
            assertNotNull(getWebResponse);
            assertNotNull(getWebResponse.getData());
            assertEquals(audioId, getWebResponse.getData().getId());
            assertEquals("Test Audio", getWebResponse.getData().getTitle());
            assertEquals("Test Author", getWebResponse.getData().getAuthor());
            assertEquals("Test Album", getWebResponse.getData().getAlbum());
            assertEquals((short)2023, getWebResponse.getData().getYear());

            assertNotNull(getWebResponse.getData().getSignedUrl());
          });
      });
  }

  @Test
  void testGetAudioNotFound() throws Exception {
    UUID nonExistentAudioId = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/audio/" + nonExistentAudioId))
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
    MockMultipartFile audioFile = new MockMultipartFile(
      "audioFile",
      "test-audio.mp3",
      "audio/mpeg",
      "Test audio content".getBytes()
    );

    mockMvc.perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .param("title", "Test Audio")
        .param("author", "Test Author")
        .param("album", "Test Album")
        .param("year", "2023")
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNotNull(webResponse.getData());
        UUID audioId = webResponse.getData().getId();

        mockMvc.perform(
          multipart("/api/v1/audio/" + audioId)
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
            assertEquals(audioId, updateWebResponse.getData().getId());
            assertEquals("Updated Test Audio", updateWebResponse.getData().getTitle());
            assertEquals("Updated Test Author", updateWebResponse.getData().getAuthor());
            assertEquals("Updated Test Album", updateWebResponse.getData().getAlbum());
            assertEquals((short)2024, updateWebResponse.getData().getYear());
          });
      });
  }

  @Test
  void testUpdateAudioWithUpdatingImageCoverSuccess() throws Exception {
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

    mockMvc.perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .file(imageCover)
        .param("title", "Test Audio")
        .param("author", "Test Author")
        .param("album", "Test Album")
        .param("year", "2023")
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNotNull(webResponse.getData());
        assertEquals(1, webResponse.getData().getCoverVersion());
        UUID audioId = webResponse.getData().getId();

        MockMultipartFile newImageCover = new MockMultipartFile(
          "imageCover",
          "new-test-cover.jpg",
          "image/jpeg",
          "New test image content".getBytes()
        );

        mockMvc.perform(
          multipart("/api/v1/audio/" + audioId)
            .file(newImageCover)
            .param("title", "Updated Test Audio with New Cover")
            .with(request -> { request.setMethod("PUT"); return request; }) // Override method to PUT
          ).andExpectAll(
            status().isOk()
          ).andDo(updateResult -> {
            WebResponse<AudioResponse> updateWebResponse = objectMapper.readValue(updateResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
        
            assertNotNull(updateWebResponse);
            assertNotNull(updateWebResponse.getData());
            assertEquals(audioId, updateWebResponse.getData().getId());
            assertEquals("Updated Test Audio with New Cover", updateWebResponse.getData().getTitle());
            assertEquals(2, updateWebResponse.getData().getCoverVersion());
          });
      });
  }

  @Test
  void testUpdateAudioEmptyTitleValidationError() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
      "audioFile",
      "test-audio.mp3",
      "audio/mpeg",
      "Test audio content".getBytes()
    );

    mockMvc.perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .param("title", "Test Audio")
        .param("author", "Test Author")
        .param("album", "Test Album")
        .param("year", "2023")
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNotNull(webResponse.getData());
        UUID audioId = webResponse.getData().getId();

        mockMvc.perform(
          multipart("/api/v1/audio/" + audioId)
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
      });
  }

  @Test
  void testDeleteAudioSuccess() throws Exception {
    MockMultipartFile audioFile = new MockMultipartFile(
      "audioFile",
      "test-audio.mp3",
      "audio/mpeg",
      "Test audio content".getBytes()
    );

    mockMvc.perform(
      multipart("/api/v1/audio")
        .file(audioFile)
        .param("title", "Test Audio")
        .param("author", "Test Author")
        .param("album", "Test Album")
        .param("year", "2023")
      ).andExpectAll(
        status().isOk()
      ).andDo(result -> {
        WebResponse<AudioResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioResponse>>() {});
    
        assertNotNull(webResponse);
        assertNotNull(webResponse.getData());
        UUID audioId = webResponse.getData().getId();

        mockMvc.perform(delete("/api/v1/audio/" + audioId))
          .andExpectAll(
            status().isOk()
          ).andDo(deleteResult -> {
            WebResponse<String> deleteWebResponse = objectMapper.readValue(deleteResult.getResponse().getContentAsString(), new TypeReference<WebResponse<String>>() {});
        
            assertNotNull(deleteWebResponse);
            assertNotNull(deleteWebResponse.getData());
            assertEquals("Audio deleted successfully", deleteWebResponse.getData());

            // Verify that the audio is deleted from the repository
            assertFalse(audioRepository.findById(audioId).isPresent());
          });
      });
  }
}
