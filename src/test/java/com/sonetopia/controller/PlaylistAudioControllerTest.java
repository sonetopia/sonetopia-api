package com.sonetopia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.Playlist;
import com.sonetopia.entity.PlaylistAudio;
import com.sonetopia.entity.User;
import com.sonetopia.model.PlaylistAudioResponse;
import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.WebResponse;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.repository.PlaylistAudioRepository;
import com.sonetopia.repository.PlaylistRepository;
import com.sonetopia.repository.UserRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc 
public class PlaylistAudioControllerTest {

  @Autowired 
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AudioRepository audioRepository;

  @Autowired
  private PlaylistRepository playlistRepository;

  @Autowired
  private PlaylistAudioRepository playlistAudioRepository;

  @Autowired 
  private ObjectMapper objectMapper;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String accessToken;

  @BeforeEach 
  void setUp() throws Exception {
    audioRepository.deleteAll();
    playlistRepository.deleteAll();
    playlistAudioRepository.deleteAll();
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

    Audio audio1 = new Audio();
    audio1.setId(UUID.randomUUID());
    audio1.setUser(user);
    audio1.setTitle("Test Audio 1");
    audio1.setStorageKey("random-storage-key-1");
    audio1.setMimeType("audio/mpeg");
    audio1.setFileSize(10000L);
    audio1.setCreatedAt(LocalDateTime.now());
    audio1.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio1);

    Audio audio2 = new Audio();
    audio2.setId(UUID.randomUUID());
    audio2.setUser(user);
    audio2.setTitle("Test Audio 2");
    audio2.setStorageKey("random-storage-key-2");
    audio2.setMimeType("audio/mpeg");
    audio2.setFileSize(10000L);
    audio2.setCreatedAt(LocalDateTime.now());
    audio2.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio2);

    Audio audio3 = new Audio();
    audio3.setId(UUID.randomUUID());
    audio3.setUser(user);
    audio3.setTitle("Test Audio 3");
    audio3.setStorageKey("random-storage-key-3");
    audio3.setMimeType("audio/mpeg");
    audio3.setFileSize(10000L);
    audio3.setCreatedAt(LocalDateTime.now());
    audio3.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(audio3);

    Playlist playlist = new Playlist();
    playlist.setUser(user);
    playlist.setTitle("Test Playlist");
    playlist.setCreatedAt(LocalDateTime.now());
    playlist.setUpdatedAt(LocalDateTime.now());
    playlistRepository.save(playlist);
  }
  
  @Test
  void testAddAudioToPlaylist() throws Exception {
    Playlist playlist = playlistRepository.findAll().get(0);
    Audio audio = audioRepository.findAll().get(0);

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio.getId())
      .contentType("application/json")
    ).andExpectAll(
      status().isOk()
    ).andDo(result -> {
      WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
      assertEquals("Audio added to playlist successfully", response.getData());
    });
  }

  @Test
  void testUpdateAudioPositionInPlaylist() throws Exception {
    Playlist playlist = playlistRepository.findAll().get(0);
    Audio audio1 = audioRepository.findAll().get(0);
    Audio audio2 = audioRepository.findAll().get(1);
    Audio audio3 = audioRepository.findAll().get(2);

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio1.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio2.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio3.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    // Update the position of the second audio to 0
    perform(put("/api/v1/playlists/{playlistId}/audio/{audioId}/position/{newPosition}", playlist.getId(), audio2.getId(), 0)
      .contentType("application/json")
    ).andExpectAll(
      status().isOk()
    ).andDo(result -> {
      WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
      assertEquals("Audio position updated successfully", response.getData());

      PlaylistAudio playlistAudio2 = playlistAudioRepository.findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio2.getId()).orElseThrow();
      PlaylistAudio playlistAudio1 = playlistAudioRepository.findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio1.getId()).orElseThrow();
      PlaylistAudio playlistAudio3 = playlistAudioRepository.findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio3.getId()).orElseThrow();

      assertEquals(0, playlistAudio2.getPosition());
      assertEquals(1, playlistAudio1.getPosition());
      assertEquals(2, playlistAudio3.getPosition());
    });
  }

  @Test
  void testUpdateAudioPositionInPlaylistInvalidPosition() throws Exception {
    Playlist playlist = playlistRepository.findAll().get(0);
    Audio audio1 = audioRepository.findAll().get(0);
    Audio audio2 = audioRepository.findAll().get(1);

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio1.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio2.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    // Attempt to update the position of the second audio to an invalid position (e.g., 5)
    perform(put("/api/v1/playlists/{playlistId}/audio/{audioId}/position/{newPosition}", playlist.getId(), audio2.getId(), 5)
      .contentType("application/json")
    ).andExpectAll(
      status().isBadRequest()
    ).andDo(result -> {
      WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
      assertEquals("Invalid playlist position", response.getErrors());
    });
  }

  @Test
  void testRemoveAudioFromPlaylist() throws Exception {
    Playlist playlist = playlistRepository.findAll().get(0);
    Audio audio1 = audioRepository.findAll().get(0);
    Audio audio2 = audioRepository.findAll().get(1);
    Audio audio3 = audioRepository.findAll().get(2);

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio1.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio2.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio3.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(delete("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio1.getId())
      .contentType("application/json")
    ).andExpectAll(
      status().isOk()
    ).andDo(result -> {
      WebResponse<String> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
      assertEquals("Audio removed from playlist successfully", response.getData());
      assertFalse(playlistAudioRepository.findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio1.getId()).isPresent());

      PlaylistAudio playlistAudio2 = playlistAudioRepository.findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio2.getId()).orElseThrow();
      PlaylistAudio playlistAudio3 = playlistAudioRepository.findFirstByPlaylist_IdAndAudio_Id(playlist.getId(), audio3.getId()).orElseThrow();
      
      assertEquals(0, playlistAudio2.getPosition());
      assertEquals(1, playlistAudio3.getPosition());
    });
  }

  @Test
  void testListPlaylistAudios() throws Exception {
    Playlist playlist = playlistRepository.findAll().get(0);
    Audio audio1 = audioRepository.findAll().get(0);
    Audio audio2 = audioRepository.findAll().get(1);

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio1.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(post("/api/v1/playlists/{playlistId}/audio/{audioId}", playlist.getId(), audio2.getId())
      .contentType("application/json")
    ).andExpect(status().isOk());

    perform(get("/api/v1/playlists/{playlistId}/audio", playlist.getId())
      .contentType("application/json")
    ).andExpectAll(
      status().isOk()
    ).andDo(result -> {
      WebResponse<List<PlaylistAudioResponse>> response = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
      assertEquals(2, response.getData().size());
      assertEquals(audio1.getId(), response.getData().get(0).getAudioId());
      assertEquals(audio2.getId(), response.getData().get(1).getAudioId());
      assertEquals(Integer.valueOf(0), response.getData().get(0).getPosition());
      assertEquals(Integer.valueOf(1), response.getData().get(1).getPosition());
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

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }
}