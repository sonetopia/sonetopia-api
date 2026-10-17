package com.sonetopia.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.sonetopia.model.WebResponse;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.security.JwtAuthenticationFilter;
import com.sonetopia.security.JwtService;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

  @Value("${spring.cors.allowed-origins}")
  private String allowedOrigins;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    List<String> origins = Arrays.stream(allowedOrigins.split(","))
      .map(String::trim)
      .filter(origin -> !origin.isEmpty())
      .toList();

    CorsConfiguration config = new CorsConfiguration();

    if (origins.contains("*")) {
      config.setAllowedOriginPatterns(List.of("*"));
    } else {
      config.setAllowedOrigins(origins);
    }
    config.setAllowedMethods(
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    );
    config.setAllowedHeaders(List.of("*"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    source.registerCorsConfiguration("/**", config);

    return source;
  }

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    JwtService jwtService,
    UserRepository userRepository,
    ObjectMapper objectMapper
  ) throws Exception {

    http
      .cors(Customizer.withDefaults())
      .csrf(csrf -> csrf.disable())
      .httpBasic(AbstractHttpConfigurer::disable)
      .formLogin(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/health").permitAll()
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers("/api/v1/media/**").permitAll()
        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v1/api-docs/**").permitAll()
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .anyRequest().authenticated()
      )
      .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
          objectMapper.writeValueAsString(
            WebResponse.builder().errors("Unauthorized").build()
          )
        );
      }))
      .addFilterBefore(
        new JwtAuthenticationFilter(jwtService, userRepository),
        UsernamePasswordAuthenticationFilter.class
      );

    return http.build();
  }
}
