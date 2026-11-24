package com.pafisolutions.tarjepafi.spacefacility.infrastructure.seed;

import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import com.pafisolutions.tarjepafi.spacefacility.application.ReservationService;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.SpaceRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The web app's seed has a study cubicle (CUB-L01) with an open policy; reservations make it
 * RESERVATION_REQUIRED for students. Runs after the spaces are imported; a policy an administrator changed later
 * is not touched on the next start (it only applies while the space still has the seed's policy).
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(45)
public class DemoReservationSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoReservationSeeder.class);
  private static final String CUBICLE = "sp-cub-l1";

  private final SpaceRepository spaces;

  public DemoReservationSeeder(SpaceRepository spaces) {
    this.spaces = spaces;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    spaces
        .findById(CUBICLE)
        .filter(space -> "OPEN".equals(space.getAccessPolicy()))
        .ifPresent(
            space -> {
              space.changeAccessPolicy(ReservationService.RESERVATION_REQUIRED, Set.of("STUDENT"));
              spaces.save(space);
              log.info("Demo seed: {} now requires a reservation", space.getCode());
            });
  }
}
