package com.pafisolutions.tarjepafi.spacefacility.infrastructure.seed;

import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.SpaceRepository;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Imports the demo spaces. Rows that already exist are left alone. */
@Component
@Profile(DemoSeed.PROFILE)
@Order(20)
public class DemoSpaceSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSpaceSeeder.class);

  private final DemoSeed seed;
  private final SpaceRepository spaces;

  public DemoSpaceSeeder(DemoSeed seed, SpaceRepository spaces) {
    this.seed = seed;
    this.spaces = spaces;
  }

  @Override
  public void run(ApplicationArguments args) {
    int created = 0;
    for (JsonNode row : seed.collection("spaces")) {
      String id = row.path("id").asString();
      if (spaces.existsById(id)) {
        continue;
      }
      Set<String> roles = new HashSet<>();
      row.path("allowedRoles").forEach(role -> roles.add(role.asString()));
      spaces.save(
          new Space(
              id,
              row.path("code").asString(),
              row.path("name").asString(),
              row.path("type").asString(),
              row.path("building").asString(),
              row.path("floor").asInt(),
              row.path("capacity").asInt(),
              row.path("status").asString(),
              row.path("accessPolicy").asString(),
              roles));
      created++;
    }
    if (created > 0) {
      log.info("Demo seed: {} spaces imported", created);
    }
  }
}
