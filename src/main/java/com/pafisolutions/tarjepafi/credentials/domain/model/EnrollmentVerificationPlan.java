package com.pafisolutions.tarjepafi.credentials.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * US15: after the enrollment deadline, the active cards of students whose enrollment is still pending
 * are deactivated. Every other evaluated card stays as it is.
 *
 * @param toDeactivate active cards of students with a pending enrollment (empty before the deadline)
 * @param unchanged the other active cards of students that were evaluated
 */
public record EnrollmentVerificationPlan(
    boolean deadlinePassed, List<Credential> toDeactivate, List<Credential> unchanged) {

  public static EnrollmentVerificationPlan plan(
      List<Credential> credentials,
      Map<String, CardHolder> holdersById,
      LocalDateTime now,
      LocalDateTime deadline) {
    List<Credential> evaluated =
        credentials.stream()
            .filter(Credential::isActive)
            .filter(
                credential -> {
                  if (!credential.isLinked()) {
                    return false;
                  }
                  CardHolder holder = holdersById.get(credential.getCardHolderId());
                  return holder != null && holder.isStudent();
                })
            .toList();
    boolean deadlinePassed = now.isAfter(deadline);
    if (!deadlinePassed) {
      return new EnrollmentVerificationPlan(false, List.of(), evaluated);
    }
    List<Credential> toDeactivate =
        evaluated.stream()
            .filter(
                credential ->
                    holdersById.get(credential.getCardHolderId()).hasPendingEnrollment())
            .toList();
    List<Credential> unchanged =
        evaluated.stream().filter(credential -> !toDeactivate.contains(credential)).toList();
    return new EnrollmentVerificationPlan(true, toDeactivate, unchanged);
  }
}
