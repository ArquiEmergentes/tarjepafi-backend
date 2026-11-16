package com.pafisolutions.tarjepafi.iam.infrastructure.seed;

import com.pafisolutions.tarjepafi.iam.domain.model.EmailAddress;
import com.pafisolutions.tarjepafi.iam.domain.model.User;
import com.pafisolutions.tarjepafi.iam.domain.model.UserRole;
import com.pafisolutions.tarjepafi.iam.domain.model.UserStatus;
import com.pafisolutions.tarjepafi.iam.infrastructure.persistence.UserRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * TS01: creates the first administrator from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} when both
 * are set and no user has that e-mail yet. It never updates an existing account.
 */
@Component
@Order(1)
public class AdminDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminDataSeeder.class);

  @ConfigurationProperties("tarjepafi.admin")
  public record AdminProperties(String email, String password, String fullName) {}

  private final AdminProperties properties;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final CampusClock clock;

  public AdminDataSeeder(
      AdminProperties properties,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      CampusClock clock) {
    this.properties = properties;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (isBlank(properties.email()) || isBlank(properties.password())) {
      return;
    }
    EmailAddress email =
        EmailAddress.parse(properties.email())
            .orElseThrow(
                () -> new IllegalStateException("ADMIN_EMAIL must be an institutional address"));
    if (users.existsByEmail(email.value())) {
      return;
    }
    LocalDateTime now = clock.now();
    users.save(
        new User(
            "usr-" + UUID.randomUUID(),
            email,
            passwordEncoder.encode(properties.password()),
            UserRole.ADMIN,
            UserStatus.ACTIVE,
            null,
            isBlank(properties.fullName()) ? "Administrator" : properties.fullName(),
            now,
            now));
    log.info("Administrator account created for {}", email.value());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
