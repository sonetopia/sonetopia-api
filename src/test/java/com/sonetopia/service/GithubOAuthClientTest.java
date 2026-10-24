package com.sonetopia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class GithubOAuthClientTest {

  @Test
  void usesPrimaryVerifiedEmail() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubOAuthClient client = new GithubOAuthClient(builder, "client-id", "secret", "https://example.com/callback");

    expectToken(server);
    server.expect(requestTo("https://api.github.com/user"))
      .andExpect(method(HttpMethod.GET))
      .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer github-token"))
      .andRespond(withSuccess(
        "{\"id\":123,\"login\":\"octocat\",\"name\":\"Octo Cat\"}",
        MediaType.APPLICATION_JSON
      ));
    server.expect(requestTo("https://api.github.com/user/emails"))
      .andExpect(method(HttpMethod.GET))
      .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer github-token"))
      .andRespond(withSuccess(
        """
        [
          {"email":"secondary@example.com","primary":false,"verified":true},
          {"email":"octo@example.com","primary":true,"verified":true}
        ]
        """,
        MediaType.APPLICATION_JSON
      ));

    GithubOAuthClient.GithubUser user = client.fetchUser("code");

    assertEquals("123", user.id());
    assertEquals("octo@example.com", user.email());
    assertEquals("Octo Cat", user.name());
    server.verify();
  }

  @Test
  void rejectsAccountWithoutVerifiedEmail() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubOAuthClient client = new GithubOAuthClient(builder, "client-id", "secret", "https://example.com/callback");

    expectToken(server);
    server.expect(requestTo("https://api.github.com/user"))
      .andRespond(withSuccess(
        "{\"id\":123,\"login\":\"octocat\",\"name\":\"Octo Cat\"}",
        MediaType.APPLICATION_JSON
      ));
    server.expect(requestTo("https://api.github.com/user/emails"))
      .andRespond(withSuccess(
        "[{\"email\":\"octo@example.com\",\"primary\":true,\"verified\":false}]",
        MediaType.APPLICATION_JSON
      ));

    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> client.fetchUser("code")
    );

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }

  @Test
  void rejectsInvalidAuthorizationCode() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubOAuthClient client = new GithubOAuthClient(builder, "client-id", "secret", "https://example.com/callback");

    server.expect(requestTo("https://github.com/login/oauth/access_token"))
      .andExpect(method(HttpMethod.POST))
      .andRespond(withSuccess(
        "{\"error\":\"bad_verification_code\"}",
        MediaType.APPLICATION_JSON
      ));

    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> client.fetchUser("bad-code")
    );

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }

  private static void expectToken(MockRestServiceServer server) {
    server.expect(requestTo("https://github.com/login/oauth/access_token"))
      .andExpect(method(HttpMethod.POST))
      .andRespond(withSuccess(
        "{\"access_token\":\"github-token\"}",
        MediaType.APPLICATION_JSON
      ));
  }
}
