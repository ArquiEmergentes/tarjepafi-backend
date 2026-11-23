package com.pafisolutions.tarjepafi.credentials.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pafisolutions.tarjepafi.credentials.domain.model.AccessResolution.Basis;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Port of {@code credentials-domain.spec.ts} of the old web app (rules 6.B, US14, US15). */
class CredentialsDomainTest {

  private static final LocalDateTime ISSUED = LocalDateTime.parse("2026-08-17T09:00:00");

  private static Credential card(String id, String holderId, CredentialStatus status) {
    return new Credential(id, "04:A2:7F:91:3C", holderId, status, ISSUED, ISSUED, null);
  }

  private static CardHolder holder(String id, HolderRole role, EnrollmentStatus enrollment) {
    return new CardHolder(
        id, "U" + id, "Holder " + id, id + "@upc.edu.pe", role, "Software", enrollment);
  }

  @Test
  void availableActions_activeCard_isBlockLostAndDeactivate() {
    assertThat(card("c", "h", CredentialStatus.ACTIVE).availableActions())
        .containsExactly(
            CredentialAction.BLOCK, CredentialAction.REPORT_LOST, CredentialAction.DEACTIVATE);
  }

  @Test
  void can_blockedOrLostCard_cannotBeBlockedOrReportedLostAgain() {
    assertThat(card("c", "h", CredentialStatus.BLOCKED).can(CredentialAction.BLOCK)).isFalse();
    assertThat(card("c", "h", CredentialStatus.LOST).can(CredentialAction.REPORT_LOST)).isFalse();
  }

  @Test
  void can_deactivate_fromActiveBlockedOrLostButNotFromDeactivated() {
    assertThat(card("c", "h", CredentialStatus.LOST).can(CredentialAction.DEACTIVATE)).isTrue();
    assertThat(card("c", "h", CredentialStatus.DEACTIVATED).can(CredentialAction.DEACTIVATE))
        .isFalse();
  }

  @Test
  void apply_reactivate_returnsToActiveAndRecordsTheChangeTime() {
    Credential credential = card("c", "h", CredentialStatus.DEACTIVATED);
    LocalDateTime at = LocalDateTime.parse("2026-10-07T10:00:00");

    credential.apply(CredentialAction.REACTIVATE, at);

    assertThat(credential.getStatus()).isEqualTo(CredentialStatus.ACTIVE);
    assertThat(credential.getStatusChangedAt()).isEqualTo(at);
    assertThat(card("c", "h", CredentialStatus.ACTIVE).can(CredentialAction.REACTIVATE)).isFalse();
  }

  @Test
  void apply_notAllowedTransition_throws() {
    Credential credential = card("c", "h", CredentialStatus.DEACTIVATED);

    assertThatThrownBy(() -> credential.apply(CredentialAction.BLOCK, ISSUED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void resolve_cardException_beatsTheRolesOfTheSpace() {
    var allow = Optional.of(new CredentialPermission("p", "c", "sp", PermissionEffect.ALLOW));
    var deny = Optional.of(new CredentialPermission("p", "c", "sp", PermissionEffect.DENY));

    assertThat(AccessResolution.resolve(allow, Set.of(HolderRole.STAFF), HolderRole.STUDENT))
        .isEqualTo(new AccessResolution(true, Basis.EXCEPTION));
    assertThat(AccessResolution.resolve(deny, Set.of(HolderRole.STAFF), HolderRole.STAFF))
        .isEqualTo(new AccessResolution(false, Basis.EXCEPTION));
  }

  @Test
  void resolve_withoutException_usesTheRolesOfTheSpace() {
    Set<HolderRole> roles = Set.of(HolderRole.TEACHER, HolderRole.STAFF);

    assertThat(AccessResolution.resolve(Optional.empty(), roles, HolderRole.TEACHER))
        .isEqualTo(new AccessResolution(true, Basis.ROLE));
    assertThat(AccessResolution.resolve(Optional.empty(), roles, HolderRole.STUDENT).allowed())
        .isFalse();
    assertThat(AccessResolution.resolve(Optional.empty(), Set.of(HolderRole.STUDENT), null).allowed())
        .isFalse();
  }

  @Test
  void plan_afterDeadline_deactivatesOnlyActiveCardsOfStudentsWithPendingEnrollment() {
    Map<String, CardHolder> holders =
        Map.of(
            "ch-1", holder("ch-1", HolderRole.STUDENT, EnrollmentStatus.COMPLETED),
            "ch-2", holder("ch-2", HolderRole.STUDENT, EnrollmentStatus.PENDING),
            "ch-3", holder("ch-3", HolderRole.TEACHER, null));
    Credential c1 = card("c1", "ch-1", CredentialStatus.ACTIVE);
    Credential c2 = card("c2", "ch-2", CredentialStatus.ACTIVE);
    List<Credential> cards =
        List.of(
            c1,
            c2,
            card("c3", "ch-3", CredentialStatus.ACTIVE),
            card("c4", "ch-2", CredentialStatus.BLOCKED),
            card("c5", null, CredentialStatus.ACTIVE));

    EnrollmentVerificationPlan plan =
        EnrollmentVerificationPlan.plan(
            cards,
            holders,
            LocalDateTime.parse("2026-10-07T09:50:00"),
            LocalDateTime.parse("2026-10-03T23:59:59"));

    assertThat(plan.deadlinePassed()).isTrue();
    assertThat(plan.toDeactivate()).containsExactly(c2);
    assertThat(plan.unchanged()).containsExactly(c1);
  }

  @Test
  void plan_beforeDeadline_changesNothing() {
    Map<String, CardHolder> holders =
        Map.of("ch-2", holder("ch-2", HolderRole.STUDENT, EnrollmentStatus.PENDING));

    EnrollmentVerificationPlan plan =
        EnrollmentVerificationPlan.plan(
            List.of(card("c2", "ch-2", CredentialStatus.ACTIVE)),
            holders,
            LocalDateTime.parse("2026-10-02T09:00:00"),
            LocalDateTime.parse("2026-10-03T23:59:59"));

    assertThat(plan.deadlinePassed()).isFalse();
    assertThat(plan.toDeactivate()).isEmpty();
  }
}
