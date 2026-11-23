package com.pafisolutions.tarjepafi.credentials.application;

import com.pafisolutions.tarjepafi.credentials.domain.model.CardHolder;
import com.pafisolutions.tarjepafi.credentials.domain.model.Credential;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialAction;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialPermission;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatusChange;
import com.pafisolutions.tarjepafi.credentials.domain.model.PermissionEffect;
import com.pafisolutions.tarjepafi.credentials.domain.model.StatusChangeReason;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CardHolderRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialPermissionRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialStatusChangeRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import com.pafisolutions.tarjepafi.spacefacility.application.SpaceCatalog;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the cards (rule 6.B): list and read them, change their status (US10, US13), link a
 * holder (US11) and manage the permission exceptions (US14). Every change is written to the card
 * history or announced as an integration event once it has been committed.
 */
@Service
public class CredentialService {

  public static final int NOTE_MAX_LENGTH = 255;

  /** A card with the holder it is linked to, if any. */
  public record CredentialView(Credential credential, CardHolder holder) {}

  private final CredentialRepository credentials;
  private final CardHolderRepository holders;
  private final CredentialStatusChangeRepository history;
  private final CredentialPermissionRepository permissions;
  private final SpaceCatalog spaces;
  private final CampusClock clock;
  private final ApplicationEventPublisher events;

  public CredentialService(
      CredentialRepository credentials,
      CardHolderRepository holders,
      CredentialStatusChangeRepository history,
      CredentialPermissionRepository permissions,
      SpaceCatalog spaces,
      CampusClock clock,
      ApplicationEventPublisher events) {
    this.credentials = credentials;
    this.holders = holders;
    this.history = history;
    this.permissions = permissions;
    this.spaces = spaces;
    this.clock = clock;
    this.events = events;
  }

  // ---------- Queries ----------

  @Transactional(readOnly = true)
  public List<CredentialView> list(CredentialStatus status, String holderId, String query) {
    Map<String, CardHolder> holdersById = holdersById();
    String needle = query == null ? "" : query.trim().toLowerCase();
    return credentials.search(status, holderId).stream()
        .map(credential -> new CredentialView(credential, holdersById.get(credential.getCardHolderId())))
        .filter(view -> needle.isEmpty() || matches(view, needle))
        .toList();
  }

  @Transactional(readOnly = true)
  public CredentialView get(String credentialId) {
    return view(find(credentialId));
  }

  @Transactional(readOnly = true)
  public List<CredentialStatusChange> history(String credentialId) {
    find(credentialId);
    return history.findByCredentialIdOrderByChangedAtDescIdDesc(credentialId);
  }

  @Transactional(readOnly = true)
  public List<CredentialPermission> permissions(String credentialId) {
    find(credentialId);
    return permissions.findByCredentialIdOrderBySpaceId(credentialId);
  }

  // ---------- Commands ----------

  /**
   * Blocks, reports lost, deactivates (US10) or reactivates (US13) a card.
   *
   * @param reason null to derive it from the action
   */
  @Transactional
  public CredentialView changeStatus(
      String credentialId,
      CredentialAction action,
      String note,
      StatusChangeReason reason,
      String actor) {
    Credential credential = find(credentialId);
    if (!credential.can(action)) {
      throw new BusinessRuleException(
          Kind.CONFLICT,
          "ACTION_NOT_ALLOWED",
          action + " is not allowed from " + credential.getStatus());
    }
    String cleanNote = note == null ? "" : note.trim();
    if (action == CredentialAction.BLOCK && cleanNote.isEmpty()) {
      throw new BusinessRuleException(Kind.INVALID, "NOTE_REQUIRED", "Blocking a card needs a reason");
    }
    CardHolder holder = holderOf(credential);
    if (action == CredentialAction.REACTIVATE) {
      if (holder != null && holder.hasPendingEnrollment()) {
        throw new BusinessRuleException(
            Kind.UNPROCESSABLE,
            "ENROLLMENT_NOT_COMPLETED",
            "The holder has not completed the enrollment");
      }
      requireNoOtherActiveCard(holder, credential.getId());
    }
    StatusChangeReason effectiveReason =
        reason != null ? reason : defaultReason(action, credential, holder);
    CredentialStatus from = credential.getStatus();
    LocalDateTime now = clock.now();
    credential.apply(action, now);
    credentials.save(credential);
    record(credential, from, effectiveReason, actor, now, truncate(cleanNote));
    publish(
        "credentials.credential-status-changed",
        credential,
        Map.of(
            "fromStatus", from.name(),
            "toStatus", credential.getStatus().name(),
            "reason", effectiveReason.name(),
            "changedBy", actor));
    return new CredentialView(credential, holder);
  }

  /** US11: links the card to a holder, or moves it to another one. */
  @Transactional
  public CredentialView link(String credentialId, String holderCode, String actor) {
    Credential credential = find(credentialId);
    CardHolder holder =
        holders
            .findByCodeIgnoreCase(holderCode == null ? "" : holderCode.trim())
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        Kind.NOT_FOUND, "HOLDER_NOT_FOUND", "No card holder has code " + holderCode));
    if (holder.getId().equals(credential.getCardHolderId())) {
      throw new BusinessRuleException(
          Kind.CONFLICT, "ALREADY_LINKED", "The card already belongs to this holder");
    }
    if (credential.isActive()) {
      requireNoOtherActiveCard(holder, credential.getId());
    }
    String previousHolderId = credential.getCardHolderId();
    credential.linkTo(holder.getId());
    credentials.save(credential);
    LocalDateTime now = clock.now();
    record(
        credential,
        credential.getStatus(),
        StatusChangeReason.MANUAL,
        actor,
        now,
        truncate(holder.getCode() + " · " + holder.getFullName()));
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("fromCardHolderId", previousHolderId);
    extra.put("toCardHolderId", holder.getId());
    extra.put("changedBy", actor);
    publish("credentials.holder-linked", credential, extra);
    return new CredentialView(credential, holder);
  }

  /** US14: creates or replaces the exception of the card for a space. */
  @Transactional
  public CredentialPermission setPermission(
      String credentialId, String spaceId, PermissionEffect effect, String actor) {
    Credential credential = find(credentialId);
    if (!spaces.exists(spaceId)) {
      throw new BusinessRuleException(Kind.NOT_FOUND, "SPACE_NOT_FOUND", "Unknown space " + spaceId);
    }
    CredentialPermission permission =
        permissions
            .findByCredentialIdAndSpaceId(credentialId, spaceId)
            .orElseGet(
                () ->
                    new CredentialPermission(
                        "perm-" + UUID.randomUUID(), credentialId, spaceId, effect));
    permission.changeEffect(effect);
    permissions.save(permission);
    publish(
        "credentials.permission-set",
        credential,
        Map.of("spaceId", spaceId, "effect", effect.name(), "changedBy", actor));
    return permission;
  }

  @Transactional
  public void removePermission(String credentialId, String spaceId, String actor) {
    Credential credential = find(credentialId);
    CredentialPermission permission =
        permissions
            .findByCredentialIdAndSpaceId(credentialId, spaceId)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        Kind.NOT_FOUND,
                        "PERMISSION_NOT_FOUND",
                        "The card has no exception for space " + spaceId));
    permissions.delete(permission);
    publish(
        "credentials.permission-removed",
        credential,
        Map.of("spaceId", spaceId, "effect", permission.getEffect().name(), "changedBy", actor));
  }

  // ---------- Internals ----------

  private Credential find(String credentialId) {
    return credentials
        .findById(credentialId)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    Kind.NOT_FOUND, "CREDENTIAL_NOT_FOUND", "Unknown credential " + credentialId));
  }

  private CredentialView view(Credential credential) {
    return new CredentialView(credential, holderOf(credential));
  }

  private CardHolder holderOf(Credential credential) {
    return credential.getCardHolderId() == null
        ? null
        : holders.findById(credential.getCardHolderId()).orElse(null);
  }

  private Map<String, CardHolder> holdersById() {
    return holders.findAll().stream().collect(Collectors.toMap(CardHolder::getId, Function.identity()));
  }

  private static boolean matches(CredentialView view, String needle) {
    if (view.credential().getCardUid().toLowerCase().contains(needle)) {
      return true;
    }
    CardHolder holder = view.holder();
    return holder != null
        && (holder.getFullName().toLowerCase().contains(needle)
            || holder.getCode().toLowerCase().contains(needle));
  }

  /** A holder may have one ACTIVE card only. */
  private void requireNoOtherActiveCard(CardHolder holder, String exceptCredentialId) {
    if (holder == null) {
      return;
    }
    Optional<Credential> other =
        credentials.findFirstByCardHolderIdAndStatusAndIdNot(
            holder.getId(), CredentialStatus.ACTIVE, exceptCredentialId);
    if (other.isPresent()) {
      throw new BusinessRuleException(
          Kind.CONFLICT,
          "HOLDER_HAS_ACTIVE_CARD",
          "The holder already has the active card " + other.get().getCardUid());
    }
  }

  private static StatusChangeReason defaultReason(
      CredentialAction action, Credential credential, CardHolder holder) {
    return switch (action) {
      case REPORT_LOST -> StatusChangeReason.LOST;
      case REACTIVATE ->
          holder != null
                  && holder.isStudent()
                  && credential.getStatus() == CredentialStatus.DEACTIVATED
              ? StatusChangeReason.REENROLLMENT
              : StatusChangeReason.MANUAL;
      default -> StatusChangeReason.MANUAL;
    };
  }

  private void record(
      Credential credential,
      CredentialStatus from,
      StatusChangeReason reason,
      String actor,
      LocalDateTime at,
      String note) {
    history.save(
        new CredentialStatusChange(
            "csh-" + UUID.randomUUID(),
            credential.getId(),
            from,
            credential.getStatus(),
            reason,
            actor,
            at,
            note.isEmpty() ? null : note));
  }

  private void publish(String routingKey, Credential credential, Map<String, Object> extra) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("credentialId", credential.getId());
    data.put("cardUid", credential.getCardUid());
    data.put("cardHolderId", credential.getCardHolderId());
    data.putAll(extra);
    events.publishEvent(new IntegrationEvent(routingKey, data));
  }

  private static String truncate(String note) {
    return note.length() <= NOTE_MAX_LENGTH ? note : note.substring(0, NOTE_MAX_LENGTH);
  }
}
