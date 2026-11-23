package com.pafisolutions.tarjepafi.spacefacility.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Set;
import java.util.TreeSet;

/**
 * A campus space. {@code type} is one of CLASSROOM, LABORATORY, RESTRICTED_AREA, CAMPUS_ENTRANCE,
 * CAMPUS_EXIT, MEETING_ROOM, LIBRARY, STUDY_CUBICLE; {@code status} is AVAILABLE or MAINTENANCE;
 * {@code accessPolicy} is OPEN or RESTRICTED. The roles are STUDENT, TEACHER and STAFF; this context
 * keeps them as plain text so it does not depend on the credentials context.
 */
@Entity
@Table(name = "spaces")
public class Space {

  @Id private String id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String building;

  @Column(nullable = false)
  private int floor;

  @Column(nullable = false)
  private int capacity;

  @Column(nullable = false)
  private String status;

  @Column(name = "access_policy", nullable = false)
  private String accessPolicy;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "space_allowed_roles", joinColumns = @JoinColumn(name = "space_id"))
  @Column(name = "role", nullable = false)
  private Set<String> allowedRoles = new TreeSet<>();

  /** Optimistic locking (ADD iteration 6): two admins editing the same space do not overwrite each other. */
  @Version private long version;

  protected Space() {}

  public Space(
      String id,
      String code,
      String name,
      String type,
      String building,
      int floor,
      int capacity,
      String status,
      String accessPolicy,
      Set<String> allowedRoles) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.type = type;
    this.building = building;
    this.floor = floor;
    this.capacity = capacity;
    this.status = status;
    this.accessPolicy = accessPolicy;
    this.allowedRoles = new TreeSet<>(allowedRoles);
  }

  public boolean isInMaintenance() {
    return "MAINTENANCE".equals(status);
  }

  public void changeStatus(String newStatus) {
    this.status = newStatus;
  }

  public void changeAccessPolicy(String policy, Set<String> roles) {
    this.accessPolicy = policy;
    this.allowedRoles.clear();
    this.allowedRoles.addAll(roles);
  }

  public String getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getBuilding() {
    return building;
  }

  public int getFloor() {
    return floor;
  }

  public int getCapacity() {
    return capacity;
  }

  public String getStatus() {
    return status;
  }

  public String getAccessPolicy() {
    return accessPolicy;
  }

  public Set<String> getAllowedRoles() {
    return Set.copyOf(allowedRoles);
  }
}
