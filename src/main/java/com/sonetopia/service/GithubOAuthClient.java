package com.sonetopia.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GithubOAuthClient {

  private static final String TOKEN_URI = "https://github.com/login/oauth/access_token";
  private static final String PROFILE_URI = "https://api.github.com/user";
  private static final String EMAILS_URI = "https://api.github.com/user/emails";
  private static final String USER_AGENT = "Sonetopia-API";
  private static final MediaType GITHUB_JSON = MediaType.parseMediaType("application/vnd.github+json");

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;

  public GithubOAuthClient(
      RestClient.Builder restClientBuilder,
      @Value("${app.github.client-id}") String clientId,
      @Value("${app.github.client-secret}") String clientSecret,
      @Value("${app.github.redirect-uri}") String redirectUri
  ) {
    this.restClient = restClientBuilder.build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  public GithubUser fetchUser(String code) {
    String accessToken = exchangeCode(code);
    GithubProfile profile = fetchProfile(accessToken);
    List<GithubEmail> emails = fetchEmails(accessToken);

    String email = emails.stream()
        .filter(candidate -> candidate.primary() && candidate.verified()
            && candidate.email() != null && !candidate.email().isBlank())
        .map(GithubEmail::email)
        .findFirst()
        .orElseGet(() -> emails.stream()
            .filter(candidate -> candidate.verified()
                && candidate.email() != null && !candidate.email().isBlank())
            .map(GithubEmail::email)
            .findFirst()
            .orElse(null));

    if (email == null || email.isBlank()) {
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "GitHub account has no verified email"
      );
    }

    String name = profile.name() != null && !profile.name().isBlank()
        ? profile.name()
        : profile.login();

    return new GithubUser(String.valueOf(profile.id()), email, name);
  }

  private String exchangeCode(String code) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("code", code);
    form.add("redirect_uri", redirectUri);

    TokenResponse response;
    try {
      response = restClient.post()
          .uri(TOKEN_URI)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .accept(MediaType.APPLICATION_JSON)
          .header(HttpHeaders.USER_AGENT, USER_AGENT)
          .body(form)
          .retrieve()
          .body(TokenResponse.class);
    } catch (RestClientResponseException exception) {
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Invalid GitHub authorization code",
        exception
      );
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach GitHub", exception);
    }

    if (response == null || response.access_token() == null || response.access_token().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid GitHub authorization code");
    }
    return response.access_token();
  }

  private GithubProfile fetchProfile(String accessToken) {
    try {
      GithubProfile profile = restClient.get()
          .uri(PROFILE_URI)
          .accept(GITHUB_JSON)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .header(HttpHeaders.USER_AGENT, USER_AGENT)
          .header("X-GitHub-Api-Version", "2022-11-28")
          .retrieve()
          .body(GithubProfile.class);

      if (profile == null) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty GitHub profile response");
      }
      return profile;
    } catch (RestClientResponseException exception) {
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Invalid GitHub access token",
        exception
      );
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach GitHub", exception);
    }
  }

  private List<GithubEmail> fetchEmails(String accessToken) {
    try {
      List<GithubEmail> emails = restClient.get()
          .uri(EMAILS_URI)
          .accept(GITHUB_JSON)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .header(HttpHeaders.USER_AGENT, USER_AGENT)
          .header("X-GitHub-Api-Version", "2022-11-28")
          .retrieve()
          .body(new ParameterizedTypeReference<List<GithubEmail>>() {});

      return emails == null ? List.of() : emails;
    } catch (RestClientResponseException exception) {
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Invalid GitHub access token",
        exception
      );
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach GitHub", exception);
    }
  }

  public record GithubUser(String id, String email, String name) {}

  private record TokenResponse(String access_token, String error, String error_description) {}

  private record GithubProfile(long id, String login, String name, String email) {}

  private record GithubEmail(String email, boolean primary, boolean verified) {}
}
