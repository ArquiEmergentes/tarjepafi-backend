package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import java.util.List;

/** Same field names as the web app's {@code space.resource.ts}. */
public record SpaceResource(
    @Schema(example = "sp-cub-l1") String id,
    @Schema(example = "CUB-L01") String code,
    @Schema(example = "Cubículo de estudio L-01") String name,
    @Schema(example = "STUDY_CUBICLE") String type,
    @Schema(example = "Biblioteca") String building,
    @Schema(example = "1") int floor,
    @Schema(example = "4") int capacity,
    @Schema(example = "AVAILABLE") String status,
    @Schema(example = "RESERVATION_REQUIRED") String accessPolicy,
    @Schema(example = "[\"STUDENT\"]") List<String> allowedRoles) {

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
