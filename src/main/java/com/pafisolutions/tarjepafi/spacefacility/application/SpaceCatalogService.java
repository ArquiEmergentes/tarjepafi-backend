package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.SpaceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SpaceCatalogService implements SpaceCatalog {

  private final SpaceRepository spaces;

  public SpaceCatalogService(SpaceRepository spaces) {
    this.spaces = spaces;
  }

  @Override
  public boolean exists(String spaceId) {
    return spaces.existsById(spaceId);
  }

  @Override
  public Optional<Space> find(String spaceId) {
    return spaces.findById(spaceId);
  }

  @Override
  public List<Space> list(String type) {
    return type == null ? spaces.findAllByOrderByCode() : spaces.findByTypeOrderByCode(type);
  }
}
