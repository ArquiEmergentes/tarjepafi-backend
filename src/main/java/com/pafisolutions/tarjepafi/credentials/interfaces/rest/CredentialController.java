package com.pafisolutions.tarjepafi.credentials.interfaces.rest;

import com.pafisolutions.tarjepafi.credentials.application.CredentialService;
import com.pafisolutions.tarjepafi.credentials.application.EnrollmentVerificationService;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialAction;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.CredentialResource;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.EnrollmentVerificationResource;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.LinkHolderRequest;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.PermissionRequest;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.PermissionResource;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.StatusChangeRequest;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.StatusChangeResource;
import com.pafisolutions.tarjepafi.shared.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Card administration. Every route needs the ADMIN role. */
@RestController
@RequestMapping("/api/v1/credentials")
@Tag(name = "Credentials")
public class CredentialController {

  private final CredentialService credentials;
  private final EnrollmentVerificationService verification;

  public CredentialController(
      CredentialService credentials, EnrollmentVerificationService verification) {
    this.credentials = credentials;
    this.verification = verification;
  }

  @GetMapping
  @Operation(
      summary = "List the cards, newest first, each with its holder",
      description =
          "Optional filters: `status` (ACTIVE, BLOCKED, LOST, DEACTIVATED), `cardHolderId`, and `q`, "
              + "a case-insensitive text matched against the card UID and the holder's name or code.")
  public List<CredentialResource> list(
      @RequestParam(required = false) CredentialStatus status,
      @RequestParam(required = false) String cardHolderId,
      @RequestParam(required = false) String q) {
    return credentials.list(status, cardHolderId, q).stream().map(CredentialResource::from).toList();
  }

  @GetMapping("/{id}")
  @Operation(summary = "One card", description = "404 CREDENTIAL_NOT_FOUND.")
  public CredentialResource get(@PathVariable String id) {
    return CredentialResource.from(credentials.get(id));
  }

  @GetMapping("/{id}/history")
  @Operation(
      summary = "Status history of a card, newest first",
      description = "404 CREDENTIAL_NOT_FOUND.")
  public List<StatusChangeResource> history(@PathVariable String id) {
    return credentials.history(id).stream().map(StatusChangeResource::from).toList();
  }

  @GetMapping("/{id}/permissions")
  @Operation(
      summary = "Access exceptions of a card (US14)",
      description = "404 CREDENTIAL_NOT_FOUND.")
  public List<PermissionResource> permissions(@PathVariable String id) {
    return credentials.permissions(id).stream().map(PermissionResource::from).toList();
  }

  @PostMapping("/{id}/block")
  @Operation(
      summary = "Block an active card (US10)",
      description =
          "Body `{\"note\": \"...\"}` is required. 400 NOTE_REQUIRED, 404 CREDENTIAL_NOT_FOUND, "
              + "409 ACTION_NOT_ALLOWED (the card is not ACTIVE).")
  public CredentialResource block(
      @PathVariable String id,
      @RequestBody(required = false) StatusChangeRequest body,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    return change(id, CredentialAction.BLOCK, body, caller);
  }

  @PostMapping("/{id}/report-lost")
  @Operation(
      summary = "Report an active card as lost (US10)",
      description = "Optional `note`. 404 CREDENTIAL_NOT_FOUND, 409 ACTION_NOT_ALLOWED.")
  public CredentialResource reportLost(
      @PathVariable String id,
      @RequestBody(required = false) StatusChangeRequest body,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    return change(id, CredentialAction.REPORT_LOST, body, caller);
  }

  @PostMapping("/{id}/deactivate")
  @Operation(
      summary = "Deactivate a card that is ACTIVE, BLOCKED or LOST (US10)",
      description = "Optional `note`. 404 CREDENTIAL_NOT_FOUND, 409 ACTION_NOT_ALLOWED.")
  public CredentialResource deactivate(
      @PathVariable String id,
      @RequestBody(required = false) StatusChangeRequest body,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    return change(id, CredentialAction.DEACTIVATE, body, caller);
  }

  @PostMapping("/{id}/reactivate")
  @Operation(
      summary = "Reactivate a BLOCKED, LOST or DEACTIVATED card (US13)",
      description =
          "Optional `note`. 404 CREDENTIAL_NOT_FOUND, 409 ACTION_NOT_ALLOWED, "
              + "409 HOLDER_HAS_ACTIVE_CARD (the holder has another active card), "
              + "422 ENROLLMENT_NOT_COMPLETED (a student whose enrollment is pending).")
  public CredentialResource reactivate(
      @PathVariable String id,
      @RequestBody(required = false) StatusChangeRequest body,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    return change(id, CredentialAction.REACTIVATE, body, caller);
  }

  @PostMapping("/{id}/link-holder")
  @Operation(
      summary = "Link the card to a holder, or move it to another one (US11)",
      description =
          "Body `{\"holderCode\": \"U202312966\"}`. 404 CREDENTIAL_NOT_FOUND, 404 HOLDER_NOT_FOUND, "
              + "409 ALREADY_LINKED, 409 HOLDER_HAS_ACTIVE_CARD (the card is active and the holder "
              + "already has another active one).")
  public CredentialResource linkHolder(
      @PathVariable String id,
      @Valid @RequestBody LinkHolderRequest body,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    return CredentialResource.from(credentials.link(id, body.holderCode(), caller.id()));
  }

  @PutMapping("/{id}/permissions/{spaceId}")
  @Operation(
      summary = "Create or replace the card's exception for a space (US14)",
      description =
          "Body `{\"effect\": \"ALLOW\"}` or DENY. 404 CREDENTIAL_NOT_FOUND, 404 SPACE_NOT_FOUND.")
  public PermissionResource setPermission(
      @PathVariable String id,
      @PathVariable String spaceId,
      @Valid @RequestBody PermissionRequest body,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    return PermissionResource.from(
        credentials.setPermission(id, spaceId, body.effect(), caller.id()));
  }

  @DeleteMapping("/{id}/permissions/{spaceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Remove the card's exception for a space (US14)",
      description = "204. 404 CREDENTIAL_NOT_FOUND, 404 PERMISSION_NOT_FOUND.")
  public void removePermission(
      @PathVariable String id,
      @PathVariable String spaceId,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    credentials.removePermission(id, spaceId, caller.id());
  }

  @GetMapping("/enrollment-verification/preview")
  @Operation(
      summary = "What the enrollment verification would do now (US15); changes nothing",
      description = "`deactivated` lists the cards a run would deactivate.")
  public EnrollmentVerificationResource previewVerification() {
    return EnrollmentVerificationResource.from(verification.preview());
  }

  @PostMapping("/enrollment-verification")
  @Operation(
      summary = "Run the enrollment verification now (US15)",
      description =
          "After `settings.enrollmentDeadline` (campus clock), deactivates the active cards of students "
              + "with a pending enrollment, as user SYSTEM with reason ENROLLMENT_NOT_COMPLETED. "
              + "Before the deadline nothing changes. The same job also runs daily at 00:05 campus time.")
  public EnrollmentVerificationResource runVerification() {
    return EnrollmentVerificationResource.from(verification.run());
  }

  private CredentialResource change(
      String id, CredentialAction action, StatusChangeRequest body, AuthenticatedUser caller) {
    String note = body == null ? null : body.note();
    return CredentialResource.from(credentials.changeStatus(id, action, note, null, caller.id()));
  }
}
