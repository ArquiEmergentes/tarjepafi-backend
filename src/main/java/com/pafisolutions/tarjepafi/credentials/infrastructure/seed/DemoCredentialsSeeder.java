package com.pafisolutions.tarjepafi.credentials.infrastructure.seed;

import com.pafisolutions.tarjepafi.credentials.domain.model.CampusSettings;
import com.pafisolutions.tarjepafi.credentials.domain.model.CardHolder;
import com.pafisolutions.tarjepafi.credentials.domain.model.Credential;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialPermission;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatusChange;
import com.pafisolutions.tarjepafi.credentials.domain.model.EnrollmentStatus;
import com.pafisolutions.tarjepafi.credentials.domain.model.HolderRole;
import com.pafisolutions.tarjepafi.credentials.domain.model.PermissionEffect;
import com.pafisolutions.tarjepafi.credentials.domain.model.StatusChangeReason;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CampusSettingsRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CardHolderRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialPermissionRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialStatusChangeRepository;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Imports the demo card holders, cards, history, permission exceptions and campus settings. Rows that
 * already exist are left alone, so a restart never overwrites what an administrator changed.
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(15)
public class DemoCredentialsSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoCredentialsSeeder.class);

  private final DemoSeed seed;
  private final CardHolderRepository holders;
  private final CredentialRepository credentials;
  private final CredentialStatusChangeRepository history;
  private final CredentialPermissionRepository permissions;
  private final CampusSettingsRepository settings;

  public DemoCredentialsSeeder(
      DemoSeed seed,
      CardHolderRepository holders,
      CredentialRepository credentials,
      CredentialStatusChangeRepository history,
      CredentialPermissionRepository permissions,
      CampusSettingsRepository settings) {
    this.seed = seed;
    this.holders = holders;
    this.credentials = credentials;
    this.history = history;
    this.permissions = permissions;
    this.settings = settings;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<CardHolder> newHolders = new ArrayList<>();
    for (JsonNode row : seed.collection("cardHolders")) {
      if (!holders.existsById(row.path("id").asString())) {
        JsonNode enrollment = row.path("enrollmentStatus");
        newHolders.add(
            new CardHolder(
                row.path("id").asString(),
                row.path("code").asString(),
                row.path("fullName").asString(),
                row.path("email").asString(),
                HolderRole.valueOf(row.path("role").asString()),
                row.path("program").asString(),
                enrollment.isString() ? EnrollmentStatus.valueOf(enrollment.asString()) : null));
      }
    }
    holders.saveAll(newHolders);

    List<Credential> newCredentials = new ArrayList<>();
    for (JsonNode row : seed.collection("credentials")) {
      if (!credentials.existsById(row.path("id").asString())) {
        newCredentials.add(
            new Credential(
                row.path("id").asString(),
                row.path("cardUid").asString(),
                text(row, "cardHolderId"),
                CredentialStatus.valueOf(row.path("status").asString()),
                LocalDateTime.parse(row.path("issuedAt").asString()),
                LocalDateTime.parse(row.path("statusChangedAt").asString()),
                row.path("lastUsedAt").isString()
                    ? LocalDateTime.parse(row.path("lastUsedAt").asString())
                    : null));
      }
    }
    credentials.saveAll(newCredentials);

    List<CredentialStatusChange> newHistory = new ArrayList<>();
    for (JsonNode row : seed.collection("credentialStatusHistory")) {
      if (!history.existsById(row.path("id").asString())) {
        newHistory.add(
            new CredentialStatusChange(
                row.path("id").asString(),
                row.path("credentialId").asString(),
                CredentialStatus.valueOf(row.path("fromStatus").asString()),
                CredentialStatus.valueOf(row.path("toStatus").asString()),
                StatusChangeReason.valueOf(row.path("reason").asString()),
                row.path("changedBy").asString(),
                LocalDateTime.parse(row.path("changedAt").asString()),
                text(row, "note")));
      }
    }
    history.saveAll(newHistory);

    List<CredentialPermission> newPermissions = new ArrayList<>();
    for (JsonNode row : seed.collection("credentialPermissions")) {
      if (!permissions.existsById(row.path("id").asString())) {
        newPermissions.add(
            new CredentialPermission(
                row.path("id").asString(),
                row.path("credentialId").asString(),
                row.path("spaceId").asString(),
                PermissionEffect.valueOf(row.path("effect").asString())));
      }
    }
    permissions.saveAll(newPermissions);

    if (!settings.existsById(CampusSettings.ID)) {
      JsonNode row = seedSettings();
      if (row != null) {
        settings.save(
            new CampusSettings(
                row.path("term").asString(),
                LocalDateTime.parse(row.path("enrollmentDeadline").asString()),
                row.path("defaultToleranceMinutes").asInt()));
      }
    }

    if (!newHolders.isEmpty() || !newCredentials.isEmpty()) {
      log.info(
          "Demo seed: {} card holders, {} cards, {} history entries, {} permissions imported",
          newHolders.size(),
          newCredentials.size(),
          newHistory.size(),
          newPermissions.size());
    }
  }

  /** The web app's db.json keeps the settings as one object, not as a list. */
  private JsonNode seedSettings() {
    return seed.object("settings");
  }

  private static String text(JsonNode row, String field) {
    JsonNode node = row.path(field);
    return node.isString() ? node.asString() : null;
  }
}
