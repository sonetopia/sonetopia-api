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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.sonetopia.model.WebResponse;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.entity.User;
import com.sonetopia.model.AudioCoverResponse;
import com.sonetopia.model.AudioResponse;
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
    "spring.servlet.multipart.max-request-size=55MB",
    "spring.cors.allowed-origins=http://localhost:5173,http://localhost:5174"
  }
)
@AutoConfigureMockMvc 
public class MediaControllerTest {

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
  void testGetAudioMediaWithValidSignedUrl() throws Exception {
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
            assertNotNull(getWebResponse.getData().getSignedUrl());

            String signedUrl = getWebResponse.getData().getSignedUrl();
            mockMvc.perform(get(signedUrl))
              .andExpectAll(
                status().isOk()
              ).andDo(streamResult -> {
                assertNotNull(streamResult.getResponse().getContentAsByteArray());
                assertEquals("audio/mpeg", streamResult.getResponse().getContentType());
              });
          });
      });
  }

  @Test
  void testGetAudioMediaWithInvalidSignedUrl() throws Exception {
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
            assertNotNull(getWebResponse.getData().getSignedUrl());

            String signedUrl = getWebResponse.getData().getSignedUrl();
            // Simulate expired signed URL by manipulating the expires parameter
            String expiredSignedUrl = signedUrl.replaceAll("expires=\\d+", "expires=99999999999");
            mockMvc.perform(get(expiredSignedUrl))
              .andExpectAll(
                status().isUnauthorized()
              ).andDo(streamResult -> {
                WebResponse<String> errorWebResponse = objectMapper.readValue(streamResult.getResponse().getContentAsString(), new TypeReference<WebResponse<String>>() {});
                assertNotNull(errorWebResponse);
                assertNull(errorWebResponse.getData());
                assertNotNull(errorWebResponse.getErrors());
                assertTrue(errorWebResponse.getErrors().contains("Invalid or expired signature"));
              });
          });
      });
  }

  @Test
  void testGetAudioMediaWithRangeHeader() throws Exception {
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
            assertNotNull(getWebResponse.getData().getSignedUrl());

            String signedUrl = getWebResponse.getData().getSignedUrl();
            mockMvc.perform(get(signedUrl).header("Range", "bytes=0-4"))
              .andExpectAll(
                status().isPartialContent()
              ).andDo(streamResult -> {
                assertNotNull(streamResult.getResponse().getContentAsByteArray());
                assertEquals("audio/mpeg", streamResult.getResponse().getContentType());
                assertEquals(5, streamResult.getResponse().getContentAsByteArray().length);
              });
          });
      });
  }

  @Test
  void testGetAudioMediaWithInvalidRangeHeader() throws Exception {
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
            assertNotNull(getWebResponse.getData().getSignedUrl());

            String signedUrl = getWebResponse.getData().getSignedUrl();
            mockMvc.perform(get(signedUrl).header("Range", "bytes=1000-2000")) // Invalid range
              .andExpectAll(
                status().isRequestedRangeNotSatisfiable(),
                header().string("Content-Range", "bytes */" + getWebResponse.getData().getFileSize())
              );
          });
      });
  }

  @Test 
  void testGetImageCoverMediaWithValidSignedUrl() throws Exception {
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
      "Test cover content".getBytes()
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
        UUID audioId = webResponse.getData().getId();
    
        mockMvc.perform(get("/api/v1/audio/" + audioId + "/cover"))
          .andExpectAll(
            status().isOk()
          ).andDo(getResult -> {
            WebResponse<AudioCoverResponse> getWebResponse = objectMapper.readValue(getResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioCoverResponse>>() {});
        
            assertNotNull(getWebResponse);
            assertNotNull(getWebResponse.getData());
            assertEquals(audioId, getWebResponse.getData().getId());
            assertNotNull(getWebResponse.getData().getSignedUrl());

            String coverSignedUrl = getWebResponse.getData().getSignedUrl();
            mockMvc.perform(get(coverSignedUrl))
              .andExpectAll(
                status().isOk()
              ).andDo(streamResult -> {
                assertNotNull(streamResult.getResponse().getContentAsByteArray());
                assertEquals("image/jpeg", streamResult.getResponse().getContentType());
              });
          });
      });
  }

  @Test
  void testGetImageCoverMediaWithInvalidSignedUrl() throws Exception {
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
      "Test cover content".getBytes()
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
        UUID audioId = webResponse.getData().getId();
    
        mockMvc.perform(get("/api/v1/audio/" + audioId + "/cover"))
          .andExpectAll(
            status().isOk()
          ).andDo(getResult -> {
            WebResponse<AudioCoverResponse> getWebResponse = objectMapper.readValue(getResult.getResponse().getContentAsString(), new TypeReference<WebResponse<AudioCoverResponse>>() {});
        
            assertNotNull(getWebResponse);
            assertNotNull(getWebResponse.getData());
            assertEquals(audioId, getWebResponse.getData().getId());
            assertNotNull(getWebResponse.getData().getSignedUrl());

            String coverSignedUrl = getWebResponse.getData().getSignedUrl();
            // Simulate expired signed URL by manipulating the expires parameter
            String expiredCoverSignedUrl = coverSignedUrl.replaceAll("expires=\\d+", "expires=99999999999");
            mockMvc.perform(get(expiredCoverSignedUrl))
              .andExpectAll(
                status().isUnauthorized()
              ).andDo(streamResult -> {
                WebResponse<String> errorWebResponse = objectMapper.readValue(streamResult.getResponse().getContentAsString(), new TypeReference<WebResponse<String>>() {});
                assertNotNull(errorWebResponse);
                assertNull(errorWebResponse.getData());
                assertNotNull(errorWebResponse.getErrors());
                assertTrue(errorWebResponse.getErrors().contains("Invalid or expired signature"));
              });
          });
      });
  }

}