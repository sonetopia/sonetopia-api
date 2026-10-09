package com.sonetopia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.sonetopia.entity.Playlist;
import com.sonetopia.entity.User;
import com.sonetopia.model.WebResponse;
import com.sonetopia.model.CreatePlaylistRequest;
import com.sonetopia.model.PlaylistResponse;
import com.sonetopia.repository.PlaylistRepository;
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
public class PlaylistControllerTest {

  @Autowired 
  private MockMvc mockMvc;

  @Autowired 
  private PlaylistRepository playlistRepository;

  @Autowired 
  private UserRepository userRepository;

  @Autowired 
  private ObjectMapper objectMapper;

  @BeforeEach 
  void setUp() {
    playlistRepository.deleteAll();
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
  void testCreatePlaylistSuccess() throws Exception {
    CreatePlaylistRequest request = new CreatePlaylistRequest();
    request.setTitle("Test Playlist");

    mockMvc.perform(post("/api/v1/playlists")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
    ).andExpectAll(
        status().isOk()
    ).andDo(result -> {
      WebResponse<PlaylistResponse> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<PlaylistResponse>>() {}
      );

      assertNotNull(response.getData().getId());
      assertEquals("Test Playlist", response.getData().getTitle());
      assertNotNull(response.getData().getCreatedAt());
      assertNotNull(response.getData().getUpdatedAt());
    });
  }

  @Test 
  void testCreatePlaylistValidationError() throws Exception {
    CreatePlaylistRequest request = new CreatePlaylistRequest();
    request.setTitle(""); // Invalid title

    mockMvc.perform(post("/api/v1/playlists")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
    ).andExpectAll(
        status().isBadRequest()
    ).andDo(result -> {
        WebResponse<PlaylistResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<PlaylistResponse>>() {});
    
        assertNotNull(webResponse);
        assertNull(webResponse.getData());
        assertNotNull(webResponse.getErrors());
        assertTrue(webResponse.getErrors().contains("Title is required"));
    });
  }

  @Test
  void testGetPlaylistSuccess() throws Exception {
    Playlist playlist = new Playlist();
    playlist.setUser(userRepository.findByUsername("test").orElseThrow());
    playlist.setTitle("Test Playlist");
    playlist.setCreatedAt(LocalDateTime.now());
    playlist.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist);

    mockMvc.perform(get("/api/v1/playlists/" + playlist.getId())
        .contentType("application/json")
    ).andExpectAll(
        status().isOk()
    ).andDo(result -> {
      WebResponse<PlaylistResponse> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<PlaylistResponse>>() {}
      );

      assertNotNull(response.getData());
      assertEquals(playlist.getId(), response.getData().getId());
      assertEquals("Test Playlist", response.getData().getTitle());
      assertNotNull(response.getData().getCreatedAt());
      assertNotNull(response.getData().getUpdatedAt());
    });
  }

  @Test
  void testGetPlaylistNotFound() throws Exception {
    mockMvc.perform(get("/api/v1/playlists/" + UUID.randomUUID())
        .contentType("application/json")
    ).andExpectAll(
        status().isNotFound()
    ).andDo(result -> {
      WebResponse<PlaylistResponse> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<PlaylistResponse>>() {}
      );

      assertNull(response.getData());
      assertNotNull(response.getErrors());
      assertTrue(response.getErrors().contains("Playlist not found"));
    });
  }

  @Test
  void testUpdatePlaylistSuccess() throws Exception {
    Playlist playlist = new Playlist();
    playlist.setUser(userRepository.findByUsername("test").orElseThrow());
    playlist.setTitle("Test Playlist");
    playlist.setCreatedAt(LocalDateTime.now());
    playlist.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist);

    CreatePlaylistRequest request = new CreatePlaylistRequest();
    request.setTitle("Updated Playlist");

    mockMvc.perform(put("/api/v1/playlists/" + playlist.getId())
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
    ).andExpectAll(
        status().isOk()
    ).andDo(result -> {
      WebResponse<PlaylistResponse> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<PlaylistResponse>>() {}
      );

      assertNotNull(response.getData());
      assertEquals(playlist.getId(), response.getData().getId());
      assertEquals("Updated Playlist", response.getData().getTitle());
      assertNotNull(response.getData().getCreatedAt());
      assertNotNull(response.getData().getUpdatedAt());
    });
  }

  @Test
  void testUpdatePlaylistValidationError() throws Exception {
    Playlist playlist = new Playlist();
    playlist.setUser(userRepository.findByUsername("test").orElseThrow());
    playlist.setTitle("Test Playlist");
    playlist.setCreatedAt(LocalDateTime.now());
    playlist.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist);

    CreatePlaylistRequest request = new CreatePlaylistRequest();
    request.setTitle(""); // Invalid title

    mockMvc.perform(put("/api/v1/playlists/" + playlist.getId())
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(request))
    ).andExpectAll(
        status().isBadRequest()
    ).andDo(result -> {
        WebResponse<PlaylistResponse> webResponse = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<WebResponse<PlaylistResponse>>() {});
    
        assertNotNull(webResponse);
        assertNull(webResponse.getData());
        assertNotNull(webResponse.getErrors());
        assertTrue(webResponse.getErrors().contains("Title is required"));
    });
  }

  @Test
  void testDeletePlaylistSuccess() throws Exception {
    Playlist playlist = new Playlist();
    playlist.setUser(userRepository.findByUsername("test").orElseThrow());
    playlist.setTitle("Test Playlist");
    playlist.setCreatedAt(LocalDateTime.now());
    playlist.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist);

    mockMvc.perform(delete("/api/v1/playlists/" + playlist.getId())
        .contentType("application/json")
    ).andExpectAll(
        status().isOk()
    ).andDo(result -> {
      WebResponse<UUID> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<UUID>>() {}
      );

      assertNotNull(response.getData());
      assertEquals(playlist.getId(), response.getData());
    });
  }

  @Test
  void testListPlaylistsSuccess() throws Exception {
    Playlist playlist1 = new Playlist();
    playlist1.setUser(userRepository.findByUsername("test").orElseThrow());
    playlist1.setTitle("Playlist 1");
    playlist1.setCreatedAt(LocalDateTime.now());
    playlist1.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist1);

    Playlist playlist2 = new Playlist();
    playlist2.setUser(userRepository.findByUsername("test").orElseThrow());
    playlist2.setTitle("Playlist 2");
    playlist2.setCreatedAt(LocalDateTime.now());
    playlist2.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist2);

    mockMvc.perform(get("/api/v1/playlists")
        .contentType("application/json")
    ).andExpectAll(
        status().isOk()
    ).andDo(result -> {
      WebResponse<List<PlaylistResponse>> response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        new TypeReference<WebResponse<List<PlaylistResponse>>>() {}
      );

      assertNotNull(response.getData());
      assertEquals(2, response.getData().size());
      assertTrue(response.getData().stream().anyMatch(p -> p.getId().equals(playlist1.getId())));
      assertTrue(response.getData().stream().anyMatch(p -> p.getId().equals(playlist2.getId())));
    });
  }
}
