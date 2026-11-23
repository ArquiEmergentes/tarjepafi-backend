package com.pafisolutions.tarjepafi.shared.infrastructure.security;

import com.pafisolutions.tarjepafi.shared.application.security.TokenAuthenticator;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless security: every request is authenticated from its bearer token, and the coarse role
 * rules per route live here. Each bounded context adds its routes to {@link #securityFilterChain}
 * when its endpoints are built; a route that is not listed needs only a valid token.
 */
@Configuration
public class SecurityConfiguration {

  @ConfigurationProperties("tarjepafi.cors")
  public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
      allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, TokenAuthenticator tokenAuthenticator)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .addFilterBefore(
            new BearerTokenAuthenticationFilter(tokenAuthenticator),
            UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            requests ->
                requests
                    // "/error" must stay public: otherwise every 400 is re-dispatched and becomes a 401.
                    .requestMatchers("/error", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/sign-in")
                    .permitAll()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/simulation/clock")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/credentials/**", "/api/v1/card-holders/**", "/api/v1/iot/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated());
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
