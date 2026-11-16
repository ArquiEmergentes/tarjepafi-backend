package com.pafisolutions.tarjepafi.iam.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pafisolutions.tarjepafi.iam.application.TokenIssuer;
import com.pafisolutions.tarjepafi.iam.domain.model.User;
import com.pafisolutions.tarjepafi.iam.infrastructure.persistence.UserRepository;
import com.pafisolutions.tarjepafi.shared.application.security.AuthenticatedUser;
import com.pafisolutions.tarjepafi.shared.application.security.TokenAuthenticator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues and checks HS256 access tokens (TS03). A token is accepted only while its signature and
 * expiry are valid <em>and</em> the account is still active, so suspending a user takes effect at
 * once instead of when the token expires.
 */
@Component
public class JwtTokenService implements TokenIssuer, TokenAuthenticator {

  private static final String ISSUER = "tarjepafi-backend";
  private static final int MINIMUM_SECRET_BYTES = 32;

  @ConfigurationProperties("tarjepafi.security.jwt")
  public record JwtProperties(String secret, Duration expiration) {

    public JwtProperties {
      if (secret == null
          || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
        throw new IllegalStateException(
            "tarjepafi.security.jwt.secret (JWT_SECRET) must be at least 32 bytes long");
      }
      expiration = expiration == null ? Duration.ofHours(8) : expiration;
    }
  }

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final Duration expiration;
  private final UserRepository users;

  public JwtTokenService(JwtProperties properties, UserRepository users) {
    SecretKey key =
        new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    this.expiration = properties.expiration();
    this.users = users;
  }

  @Override
  public String issue(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.getId())
            .issuedAt(now)
            .expiresAt(now.plus(expiration))
            .claim("role", user.getRole().name())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  @Override
  public Optional<AuthenticatedUser> authenticate(String token) {
    Jwt jwt;
    try {
      jwt = decoder.decode(token);
    } catch (JwtException exception) {
      return Optional.empty();
    }
    return users
        .findById(jwt.getSubject())
        .filter(User::isEnabled)
        .map(
            user ->
                new AuthenticatedUser(
                    user.getId(), user.getEmail(), user.getRole().name(), user.getCardHolderId()));
  }
}
