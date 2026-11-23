package com.pafisolutions.tarjepafi.credentials.application;

import com.pafisolutions.tarjepafi.credentials.application.CredentialService.CredentialView;
import com.pafisolutions.tarjepafi.credentials.domain.model.CampusSettings;
import com.pafisolutions.tarjepafi.credentials.domain.model.CardHolder;
import com.pafisolutions.tarjepafi.credentials.domain.model.Credential;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialAction;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatusChange;
import com.pafisolutions.tarjepafi.credentials.domain.model.EnrollmentVerificationPlan;
import com.pafisolutions.tarjepafi.credentials.domain.model.StatusChangeReason;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CardHolderRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US15: deactivates the cards of students whose enrollment was not completed by the deadline. */
@Service
public class EnrollmentVerificationService {

  private static final Logger log = LoggerFactory.getLogger(EnrollmentVerificationService.class);

  /** What a verification would do (preview) or did (run). */
  public record Summary(
      LocalDateTime executedAt,
      LocalDateTime deadline,
      boolean deadlinePassed,
      List<CredentialView> deactivated,
      int unchangedCount,
      int failedCount) {}

  private final SettingsService settings;
  private final CredentialRepository credentials;
  private final CardHolderRepository holders;
  private final CredentialService credentialService;
  private final CampusClock clock;

  public EnrollmentVerificationService(
      SettingsService settings,
      CredentialRepository credentials,
      CardHolderRepository holders,
      CredentialService credentialService,
      CampusClock clock) {
    this.settings = settings;
    this.credentials = credentials;
    this.holders = holders;
    this.credentialService = credentialService;
    this.clock = clock;
  }

  /** What a run would do right now; changes nothing. */
  @Transactional(readOnly = true)
  public Summary preview() {
    CampusSettings campus = settings.current();
    EnrollmentVerificationPlan plan = plan(campus);
    Map<String, CardHolder> byId = holdersById();
    List<CredentialView> toDeactivate =
        plan.toDeactivate().stream()
            .map(credential -> new CredentialView(credential, byId.get(credential.getCardHolderId())))
            .toList();
    return new Summary(
        clock.now(),
        campus.getEnrollmentDeadline(),
        plan.deadlinePassed(),
        toDeactivate,
        plan.unchanged().size(),
        0);
  }

  /** Deactivates the cards of the plan, one transaction each, as the system user. */
  public Summary run() {
    CampusSettings campus = settings.current();
    EnrollmentVerificationPlan plan = plan(campus);
    List<CredentialView> deactivated = new ArrayList<>();
    int failed = 0;
    for (Credential credential : plan.toDeactivate()) {
      try {
        deactivated.add(
            credentialService.changeStatus(
                credential.getId(),
                CredentialAction.DEACTIVATE,
                null,
                StatusChangeReason.ENROLLMENT_NOT_COMPLETED,
                CredentialStatusChange.SYSTEM_ACTOR));
      } catch (RuntimeException exception) {
        failed++;
        log.warn("Card {} could not be deactivated", credential.getCardUid(), exception);
      }
    }
    log.info(
        "Enrollment verification: {} cards deactivated, {} unchanged, {} failed",
        deactivated.size(),
        plan.unchanged().size(),
        failed);
    return new Summary(
        clock.now(),
        campus.getEnrollmentDeadline(),
        plan.deadlinePassed(),
        deactivated,
        plan.unchanged().size(),
        failed);
  }

  private EnrollmentVerificationPlan plan(CampusSettings campus) {
    return EnrollmentVerificationPlan.plan(
        credentials.findAll(), holdersById(), clock.now(), campus.getEnrollmentDeadline());
  }

  private Map<String, CardHolder> holdersById() {
    return holders.findAll().stream().collect(Collectors.toMap(CardHolder::getId, Function.identity()));
  }
}
