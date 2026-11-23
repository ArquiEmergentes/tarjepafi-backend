package com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, String> {

  List<Space> findByTypeOrderByCode(String type);

  List<Space> findAllByOrderByCode();

  boolean existsByCode(String code);
}
