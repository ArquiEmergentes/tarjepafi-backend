package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.SpaceRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registering spaces, changing their state and their access policy (TS18, TS28). */
@Service
@Transactional
public class SpaceManagementService {

  public static final Set<String> TYPES =
      Set.of(
          "CLASSROOM",
          "LABORATORY",
          "RESTRICTED_AREA",
          "CAMPUS_ENTRANCE",
          "CAMPUS_EXIT",
          "MEETING_ROOM",
          "LIBRARY",
          "STUDY_CUBICLE",
          "EVENT_VENUE");
  public static final Set<String> STATUSES = Set.of("AVAILABLE", "MAINTENANCE");
  public static final Set<String> POLICIES = Set.of("OPEN", "RESTRICTED");
  public static final Set<String> ROLES = Set.of("STUDENT", "TEACHER", "STAFF");

  private final SpaceRepository spaces;

  public SpaceManagementService(SpaceRepository spaces) {
    this.spaces = spaces;
  }

  /** TS18: a new space, AVAILABLE. 409 SPACE_CODE_EXISTS, 400 for an invalid type, capacity, policy or role. */
  public Space create(
      String code,
      String name,
      String type,
      String building,
      int floor,
      int capacity,
      String accessPolicy,
      Collection<String> allowedRoles) {
    require(TYPES.contains(type), "INVALID_SPACE_TYPE", "Unknown space type " + type);
    require(capacity > 0, "INVALID_CAPACITY", "The capacity must be greater than 0");
    require(POLICIES.contains(accessPolicy), "INVALID_ACCESS_POLICY", "Unknown policy " + accessPolicy);
    Set<String> roles = validRoles(allowedRoles);
    if (spaces.existsByCode(code)) {
      throw new BusinessRuleException(Kind.CONFLICT, "SPACE_CODE_EXISTS", "The code " + code + " is taken");
    }
    Space space =
        new Space(
            "sp-" + UUID.randomUUID().toString().substring(0, 8),
            code,
            name,
            type,
            building,
            floor,
            capacity,
            "AVAILABLE",
            accessPolicy,
            roles);
    return spaces.save(space);
  }

  /** TS18: AVAILABLE or MAINTENANCE; taps in a space under maintenance are denied afterwards. */
  public Space changeStatus(String spaceId, String status) {
    require(STATUSES.contains(status), "INVALID_SPACE_STATUS", "Unknown status " + status);
    Space space = find(spaceId);
    space.changeStatus(status);
    return spaces.save(space);
  }

  /** TS28: the policy and the roles that taps are evaluated with from now on. */
  public Space changeAccessPolicy(String spaceId, String accessPolicy, Collection<String> allowedRoles) {
    require(POLICIES.contains(accessPolicy), "INVALID_ACCESS_POLICY", "Unknown policy " + accessPolicy);
    Set<String> roles = validRoles(allowedRoles);
    Space space = find(spaceId);
    space.changeAccessPolicy(accessPolicy, roles);
    return spaces.save(space);
  }

  private Space find(String spaceId) {
    return spaces
        .findById(spaceId)
        .orElseThrow(
            () -> new BusinessRuleException(Kind.NOT_FOUND, "SPACE_NOT_FOUND", "Unknown space " + spaceId));
  }

  private static Set<String> validRoles(Collection<String> roles) {
    List<String> given = roles == null ? List.of() : List.copyOf(roles);
    require(
        ROLES.containsAll(given),
        "INVALID_ROLE",
        "Roles must be STUDENT, TEACHER or STAFF");
    return new TreeSet<>(given);
  }

  private static void require(boolean condition, String code, String message) {
    if (!condition) {
      throw new BusinessRuleException(Kind.INVALID, code, message);
    }
  }
}
