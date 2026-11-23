package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import java.util.List;

/** Same field names as the web app's {@code space.resource.ts}. */
public record SpaceResource(
    String id,
    String code,
    String name,
    String type,
    String building,
    int floor,
    int capacity,
    String status,
    String accessPolicy,
    List<String> allowedRoles) {

  public static SpaceResource from(Space space) {
    return new SpaceResource(
        space.getId(),
        space.getCode(),
        space.getName(),
        space.getType(),
        space.getBuilding(),
        space.getFloor(),
        space.getCapacity(),
        space.getStatus(),
        space.getAccessPolicy(),
        space.getAllowedRoles().stream().sorted().toList());
  }
}
