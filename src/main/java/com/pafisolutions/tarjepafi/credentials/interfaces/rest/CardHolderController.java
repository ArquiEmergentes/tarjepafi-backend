package com.pafisolutions.tarjepafi.credentials.interfaces.rest;

import com.pafisolutions.tarjepafi.credentials.domain.model.HolderRole;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CardHolderRepository;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.CardHolderResource;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The card holders (people of the simulated academic system). Every route needs the ADMIN role. */
@RestController
@RequestMapping("/api/v1/card-holders")
@Tag(name = "Card holders")
public class CardHolderController {

  private final CardHolderRepository holders;

  public CardHolderController(CardHolderRepository holders) {
    this.holders = holders;
  }

  @GetMapping
  @Operation(
      summary = "List or search the card holders by name",
      description =
          "Optional filters: `role` (STUDENT, TEACHER, STAFF), `q` (text matched against name and "
              + "code), `code` (exact code, ignoring case; answers a list with zero or one holder).")
  public List<CardHolderResource> list(
      @RequestParam(required = false) HolderRole role,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String code) {
    if (code != null) {
      return holders.findByCodeIgnoreCase(code.trim()).map(CardHolderResource::from).stream().toList();
    }
    String query = q == null || q.isBlank() ? null : q.trim();
    return holders.search(role, query).stream().map(CardHolderResource::from).toList();
  }

  @GetMapping("/{id}")
  @Operation(summary = "One card holder", description = "404 HOLDER_NOT_FOUND.")
  public CardHolderResource get(@PathVariable String id) {
    return holders
        .findById(id)
        .map(CardHolderResource::from)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    Kind.NOT_FOUND, "HOLDER_NOT_FOUND", "Unknown card holder " + id));
  }
}
