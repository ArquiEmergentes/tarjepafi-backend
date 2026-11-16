package com.pafisolutions.tarjepafi.shared.infrastructure.security;

import com.pafisolutions.tarjepafi.shared.application.security.TokenAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the request from its {@code Authorization: Bearer} header. A missing or rejected
 * token leaves the request anonymous, and the authorisation rules then answer 401.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String PREFIX = "Bearer ";

  private final TokenAuthenticator authenticator;

  public BearerTokenAuthenticationFilter(TokenAuthenticator authenticator) {
    this.authenticator = authenticator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(PREFIX)) {
      authenticator
          .authenticate(header.substring(PREFIX.length()).trim())
          .ifPresent(
              user ->
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          UsernamePasswordAuthenticationToken.authenticated(
                              user,
                              null,
                              List.of(new SimpleGrantedAuthority("ROLE_" + user.role())))));
    }
    chain.doFilter(request, response);
  }
}
