package com.pafisolutions.tarjepafi.credentials.application;

import com.pafisolutions.tarjepafi.credentials.domain.model.AccessResolution;
import com.pafisolutions.tarjepafi.credentials.domain.model.CardHolder;
import com.pafisolutions.tarjepafi.credentials.domain.model.Credential;
import com.pafisolutions.tarjepafi.credentials.domain.model.HolderRole;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CardHolderRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialPermissionRepository;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CredentialRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardHolderDirectoryService implements CardHolderDirectoryPort {

  private final CredentialRepository credentials;
  private final CardHolderRepository holders;
  private final CredentialPermissionRepository permissions;

  public CardHolderDirectoryService(
      CredentialRepository credentials,
      CardHolderRepository holders,
      CredentialPermissionRepository permissions) {
    this.credentials = credentials;
    this.holders = holders;
    this.permissions = permissions;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<HolderInfo> findHolder(String cardHolderId) {
    return holders.findById(cardHolderId).map(CardHolderDirectoryService::info);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CardLookup> findByCardUid(String cardUid) {
    return credentials.findByCardUidIgnoreCase(cardUid.trim()).map(this::lookup);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CardLookup> findCardOf(String cardHolderId) {
    List<Credential> cards = credentials.findByCardHolderId(cardHolderId);
    return cards.stream()
        .filter(Credential::isActive)
        .findFirst()
        .or(() -> cards.stream().max(Comparator.comparing(Credential::getIssuedAt)))
        .map(this::lookup);
  }

  @Override
  @Transactional(readOnly = true)
  public AccessDecision resolveAccess(
      String credentialId, String spaceId, Collection<String> allowedRoles) {
    HolderRole holderRole =
        credentials
            .findById(credentialId)
            .map(Credential::getCardHolderId)
            .flatMap(id -> id == null ? Optional.empty() : holders.findById(id))
            .map(CardHolder::getRole)
            .orElse(null);
    Set<HolderRole> roles =
        allowedRoles.stream().map(HolderRole::valueOf).collect(Collectors.toSet());
    AccessResolution resolution =
        AccessResolution.resolve(
            permissions.findByCredentialIdAndSpaceId(credentialId, spaceId), roles, holderRole);
    return new AccessDecision(resolution.allowed(), resolution.basis().name());
  }

  @Override
  @Transactional
  public void markUsed(String credentialId, LocalDateTime at) {
    credentials
        .findById(credentialId)
        .ifPresent(
            credential -> {
              credential.markUsed(at);
              credentials.save(credential);
            });
  }

  private CardLookup lookup(Credential credential) {
    HolderInfo holder =
        credential.getCardHolderId() == null
            ? null
            : holders.findById(credential.getCardHolderId()).map(CardHolderDirectoryService::info).orElse(null);
    return new CardLookup(
        credential.getId(), credential.getCardUid(), credential.getStatus().name(), holder);
  }

  private static HolderInfo info(CardHolder holder) {
    return new HolderInfo(
        holder.getId(),
        holder.getCode(),
        holder.getFullName(),
        holder.getEmail(),
        holder.getRole().name(),
        holder.getProgram(),
        holder.getEnrollmentStatus() == null ? null : holder.getEnrollmentStatus().name());
  }
}
