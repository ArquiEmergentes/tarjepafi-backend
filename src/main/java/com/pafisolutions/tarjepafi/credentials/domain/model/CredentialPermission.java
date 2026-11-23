package com.pafisolutions.tarjepafi.credentials.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An access exception of one card for one space (US14). It beats the roles the space admits. */
@Entity
@Table(name = "credential_permissions")
public class CredentialPermission {

  @Id private String id;

  @Column(name = "credential_id", nullable = false)
  private String credentialId;

  @Column(name = "space_id", nullable = false)
  private String spaceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PermissionEffect effect;

  protected CredentialPermission() {}

  public CredentialPermission(
      String id, String credentialId, String spaceId, PermissionEffect effect) {
    this.id = id;
    this.credentialId = credentialId;
    this.spaceId = spaceId;
    this.effect = effect;
  }

  public boolean allows() {
    return effect == PermissionEffect.ALLOW;
  }

  public void changeEffect(PermissionEffect effect) {
    this.effect = effect;
  }

  public String getId() {
    return id;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public String getSpaceId() {
    return spaceId;
  }

  public PermissionEffect getEffect() {
    return effect;
  }
}
