package com.sonetopia.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sonetopia.entity.Audio;
import com.sonetopia.entity.User;
import com.sonetopia.model.AuthResponse;
import com.sonetopia.model.LoginRequest;
import com.sonetopia.model.SearchResultResponse;
import com.sonetopia.model.SearchSource;
import com.sonetopia.model.WebResponse;
import com.sonetopia.repository.AudioRepository;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.service.GithubOAuthClient;
import com.sonetopia.service.YtDlpService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AudioSearchControllerTest {

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

  @MockitoBean
  private YtDlpService ytDlpService;

  // Not used here; mocked so the context does not build a real HTTP client (same as AuthControllerTest).
  @MockitoBean
  private GithubOAuthClient githubOAuthClient;

  private User user;
  private String accessToken;

  @BeforeEach
  void setUp() throws Exception {
    audioRepository.deleteAll();
    userRepository.deleteAll();

    user = new User();
    user.setUsername("searcher");
    user.setName("Search User");
    user.setEmail("searcher@example.com");
    user.setPassword(passwordEncoder.encode("secret"));
    user.setStorageUsed(0L);
    user.setIsAdmin(false);
    user.setEmailVerifiedAt(LocalDateTime.now());
    user.setCreatedAt(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());
    user = userRepository.save(user);

    accessToken = login();
  }

  @Test
  void searchRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/audio/search").param("q", "coldplay"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void blankQueryIsAClientError() throws Exception {
    authorized(get("/api/v1/audio/search").param("q", "  "))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors").value("Search query is required"));
  }

  @Test
  void missingQueryIsAClientError() throws Exception {
    authorized(get("/api/v1/audio/search"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors").value("Search query is required"));
  }

  @Test
  void unknownSourceIsAClientError() throws Exception {
    authorized(get("/api/v1/audio/search").param("q", "coldplay").param("source", "spotify"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors").value("Unknown search source"));
  }

  @Test
  void returnsPagedResultsAndLoadsMore() throws Exception {
    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), eq("coldplay"), anyInt()))
      .thenReturn(tracks(25));

    authorized(get("/api/v1/audio/search").param("q", "coldplay").param("limit", "10"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.source").value("youtube_music"))
      .andExpect(jsonPath("$.data.offset").value(0))
      .andExpect(jsonPath("$.data.limit").value(10))
      .andExpect(jsonPath("$.data.hasMore").value(true))
      .andExpect(jsonPath("$.data.results.length()").value(10))
      .andExpect(jsonPath("$.data.results[0].title").value("Track 0"))
      .andExpect(jsonPath("$.data.results[0].inLibrary").value(false));

    authorized(get("/api/v1/audio/search")
        .param("q", "coldplay").param("limit", "10").param("offset", "20").param("source", "youtube_music"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.hasMore").value(false))
      .andExpect(jsonPath("$.data.results.length()").value(5))
      .andExpect(jsonPath("$.data.results[0].title").value("Track 20"));
  }

  @Test
  void marksTracksAlreadyImportedByTheUser() throws Exception {
    Audio imported = new Audio();
    imported.setId(UUID.randomUUID());
    imported.setUser(user);
    imported.setTitle("Track 1");
    imported.setType("music");
    imported.setSourceUrl("https://www.youtube.com/watch?v=track00001a");
    imported.setStorageKey("users/x/audio/y/audio.mp3");
    imported.setMimeType("audio/mpeg");
    imported.setFileSize(1L);
    imported.setCreatedAt(LocalDateTime.now());
    imported.setUpdatedAt(LocalDateTime.now());
    audioRepository.save(imported);

    when(ytDlpService.search(eq(SearchSource.YOUTUBE_MUSIC), anyString(), anyInt())).thenReturn(List.of(
      SearchResultResponse.builder().title("Track 0").url("https://music.youtube.com/watch?v=track00000a").source("youtube_music").build(),
      SearchResultResponse.builder().title("Track 1").url("https://music.youtube.com/watch?v=track00001a&list=x").source("youtube_music").build()
    ));

    authorized(get("/api/v1/audio/search").param("q", "marked"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.results[0].inLibrary").value(false))
      .andExpect(jsonPath("$.data.results[1].inLibrary").value(true))
      .andExpect(jsonPath("$.data.results[1].libraryAudioId").value(imported.getId().toString()));
  }

  private org.springframework.test.web.servlet.ResultActions authorized(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
  ) throws Exception {
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }

  private List<SearchResultResponse> tracks(int count) {
    return IntStream.range(0, count)
      .mapToObj(index -> SearchResultResponse.builder()
        .title("Track " + index)
        .url("https://music.youtube.com/watch?v=track" + String.format("%06d", index))
        .source("youtube_music")
        .build())
      .toList();
  }

  private String login() throws Exception {
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setUsername("searcher");
    loginRequest.setPassword("secret");

    String responseJson = mockMvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(loginRequest)))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    WebResponse<AuthResponse> response = objectMapper.readValue(responseJson, new TypeReference<WebResponse<AuthResponse>>() {});
    return response.getData().getToken();
  }
}
