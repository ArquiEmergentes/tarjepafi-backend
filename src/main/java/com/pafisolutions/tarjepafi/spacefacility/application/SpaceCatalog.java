package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import java.util.List;
import java.util.Optional;

/** What Space &amp; Facility offers to the other contexts and to its own REST layer. */
public interface SpaceCatalog {

  boolean exists(String spaceId);

  Optional<Space> find(String spaceId);

  /** All spaces ordered by code, or only those of {@code type} when it is not null. */
  List<Space> list(String type);
}
