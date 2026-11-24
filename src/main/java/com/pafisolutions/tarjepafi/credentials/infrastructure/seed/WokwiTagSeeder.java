package com.pafisolutions.tarjepafi.credentials.infrastructure.seed;

import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialRepository;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Points the cards of the demo holders at the preset tags of the Wokwi MFRC522 part (4 or 7 byte UIDs), so a reader
 * simulated on wokwi.com resolves to the same people as the seed. Off unless {@code tarjepafi.demo.wokwi-tags} is
 * true (the deployed demo turns it on); the other tags of the part (Red {@code AA:BB:CC:DD}, Key Fob
 * {@code C0:FF:EE:99}) stay unregistered on purpose.
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(16)
@ConditionalOnProperty(name = "tarjepafi.demo.wokwi-tags", havingValue = "true")
public class WokwiTagSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(WokwiTagSeeder.class);

  /** Credential id of the seed -> Wokwi tag. */
  static final Map<String, String> TAGS =
      Map.of(
          "cred-001", "01:02:03:04", // Blue: student U202312966 (ACTIVE)
          "cred-041", "04:11:22:33:44:55:66", // NFC Tag: teacher D0001 (ACTIVE)
          "cred-045", "11:22:33:44", // Green: staff T0001 (ACTIVE)
          "cred-015", "55:66:77:88"); // Yellow: student U202311918 (BLOCKED)

  private final CredentialRepository credentials;

  public WokwiTagSeeder(CredentialRepository credentials) {
    this.credentials = credentials;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    TAGS.forEach(
        (id, uid) ->
            credentials
                .findById(id)
                .filter(credential -> !uid.equals(credential.getCardUid()))
                .ifPresent(
                    credential -> {
                      credential.reassignCardUid(uid);
                      log.info("Wokwi tag {} now belongs to credential {}", uid, id);
                    }));
  }
}
