package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.seed;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.DeviceType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderConfiguration;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderMode;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.ReaderRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reader on the table of the study cubicle (CUB-L01), where the student checks a reservation in (TS22). The
 * web app's seed only has a door reader there, in maintenance, so this one is added for the Wokwi demo.
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(26)
public class DemoCubicleReaderSeeder implements ApplicationRunner {

  public static final String READER_ID = "RDR-K-003";

  private static final Logger log = LoggerFactory.getLogger(DemoCubicleReaderSeeder.class);

  private final ReaderRepository readers;
  private final CampusClock clock;

  public DemoCubicleReaderSeeder(ReaderRepository readers, CampusClock clock) {
    this.readers = readers;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (readers.existsById(READER_ID)) {
      return;
    }
    LocalDateTime now = clock.now();
    readers.save(
        new Reader(
            READER_ID,
            "Lector Cubículo L01",
            DeviceType.DESK_READER,
            "sp-cub-l1",
            ReaderStatus.ACTIVE,
            new ReaderConfiguration(ReaderMode.ACCESS_CONTROL, 30, 60, "2.4.1"),
            now,
            now));
    log.info("Demo seed: reader {} registered at the study cubicle", READER_ID);
  }
}
