package com.pafisolutions.tarjepafi.iam.infrastructure.seed;

import com.pafisolutions.tarjepafi.iam.domain.model.EmailAddress;
import com.pafisolutions.tarjepafi.iam.domain.model.User;
import com.pafisolutions.tarjepafi.iam.domain.model.UserRole;
import com.pafisolutions.tarjepafi.iam.domain.model.UserStatus;
import com.pafisolutions.tarjepafi.iam.infrastructure.persistence.UserRepository;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Imports the demo users, hashing their passwords. Rows that already exist are left alone. */
@Component
@Profile(DemoSeed.PROFILE)
@Order(10)
public class DemoUserSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

  private final DemoSeed seed;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public DemoUserSeeder(DemoSeed seed, UserRepository users, PasswordEncoder passwordEncoder) {
    this.seed = seed;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    int created = 0;
    for (JsonNode row : seed.collection("users")) {
      String id = row.path("id").asString();
      String email = row.path("email").asString();
      if (users.existsById(id) || users.existsByEmail(email)) {
        continue;
      }
      JsonNode cardHolderId = row.path("cardHolderId");
      users.save(
          new User(
              id,
              new EmailAddress(email),
              passwordEncoder.encode(row.path("password").asString()),
              UserRole.valueOf(row.path("role").asString()),
              UserStatus.valueOf(row.path("status").asString()),
              cardHolderId.isString() ? cardHolderId.asString() : null,
              row.path("fullName").asString(),
              LocalDateTime.parse(row.path("createdAt").asString()),
              LocalDateTime.parse(row.path("updatedAt").asString())));
      created++;
    }
    if (created > 0) {
      log.info("Demo seed: {} users imported", created);
    }
  }
}
