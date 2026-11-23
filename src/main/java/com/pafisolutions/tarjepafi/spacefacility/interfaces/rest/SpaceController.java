package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import com.pafisolutions.tarjepafi.spacefacility.application.SpaceCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces")
@Tag(name = "Spaces")
public class SpaceController {

  private final SpaceCatalog catalog;

  public SpaceController(SpaceCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  @Operation(
      summary = "List the campus spaces ordered by code",
      description = "Optional filter `type`, for example CLASSROOM or CAMPUS_ENTRANCE.")
  public List<SpaceResource> list(@RequestParam(required = false) String type) {
    return catalog.list(type).stream().map(SpaceResource::from).toList();
  }

  @GetMapping("/{id}")
  @Operation(summary = "One space", description = "404 SPACE_NOT_FOUND when the id is unknown.")
  public SpaceResource get(@PathVariable String id) {
    return catalog
        .find(id)
        .map(SpaceResource::from)
        .orElseThrow(
            () -> new BusinessRuleException(Kind.NOT_FOUND, "SPACE_NOT_FOUND", "Unknown space " + id));
  }
}
